package com.example.codebase.utils

import android.Manifest
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.coroutines.resume

/**
 * External storage, in two flavours:
 *
 * - **App-specific folders** ([getAppFilesDir], [getAppCacheDir]): no permission needed, removed on
 *   uninstall, not visible to other apps on API 29+.
 * - **Shared collections** ([saveImageToGallery] for Pictures, [saveToDownloads]): visible in the
 *   gallery or file manager and kept after uninstall. API 29+ goes through MediaStore and needs no
 *   permission. API 24-28 writes to the public folder and needs `WRITE_EXTERNAL_STORAGE` (declared
 *   with `maxSdkVersion="28"`). Ask for it with
 *   `PermissionUtils.request(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) { ... }`, which
 *   continues right away on API 29+.
 *
 * I/O runs on [Dispatchers.IO]; failures return `null` or `false`.
 */
object ExternalStorageUtils {

    private const val DEFAULT_IMAGE_QUALITY = 95
    private const val SCAN_TIMEOUT_MS = 10_000L

    fun isWritable(): Boolean = Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED

    fun isReadable(): Boolean = Environment.getExternalStorageState().let {
        it == Environment.MEDIA_MOUNTED || it == Environment.MEDIA_MOUNTED_READ_ONLY
    }

    /**
     * App-specific external folder, optionally of a [type] such as `Environment.DIRECTORY_PICTURES`.
     * `null` if external storage isn't available.
     */
    fun getAppFilesDir(context: Context, type: String? = null): File? = context.getExternalFilesDir(type)

    fun getAppCacheDir(context: Context): File? = context.externalCacheDir

    /**
     * Saves [bitmap] to the shared Pictures collection, in `Pictures/<album>` when [album] is given.
     * Returns a MediaStore Uri, or `null` on failure (including a missing permission on API 24-28).
     */
    suspend fun saveImageToGallery(
        context: Context,
        bitmap: Bitmap,
        displayName: String,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
        quality: Int = DEFAULT_IMAGE_QUALITY,
        album: String? = null,
    ): Uri? = withContext(Dispatchers.IO) {
        val target = SaveTarget(
            directory = Environment.DIRECTORY_PICTURES,
            subDirectory = album?.let { FileUtils.sanitizeFileName(it) },
            displayName = FileUtils.sanitizeFileName(displayName),
            mimeType = imageMimeTypeOf(format),
        )
        val write: (OutputStream) -> Boolean = { bitmap.compress(format, quality, it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            saveToMediaStore(context, collection, target, write)
        } else {
            saveToPublicDirectory(context, target, write)
        }
    }

    /**
     * Copies [input] into the shared Downloads folder; [input] is not closed. Returns a MediaStore Uri,
     * or a `file://` Uri on API 24-28 if the media scanner didn't answer. Returns `null` on failure.
     */
    suspend fun saveToDownloads(
        context: Context,
        input: InputStream,
        displayName: String,
        mimeType: String,
    ): Uri? = withContext(Dispatchers.IO) {
        val target = SaveTarget(
            directory = Environment.DIRECTORY_DOWNLOADS,
            subDirectory = null,
            displayName = FileUtils.sanitizeFileName(displayName),
            mimeType = mimeType,
        )
        val write: (OutputStream) -> Boolean = { output ->
            input.copyTo(output)
            true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            saveToMediaStore(context, collection, target, write)
        } else {
            saveToPublicDirectory(context, target, write)
        }
    }

    /**
     * Deletes an item saved by this app. Returns `false` if it doesn't exist or can't be deleted.
     * Deleting items owned by other apps needs a user confirmation, which this function doesn't handle.
     */
    suspend fun delete(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            if (uri.scheme == ContentResolver.SCHEME_FILE) {
                uri.path?.let { File(it).delete() } ?: false
            } else {
                context.contentResolver.delete(uri, null, null) > 0
            }
        } catch (e: SecurityException) {
            false
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    /** Free bytes on the primary external volume, or `0` if it isn't available. */
    fun getAvailableBytes(context: Context): Long {
        val dir = context.getExternalFilesDir(null) ?: return 0L
        return try {
            StatFs(dir.path).availableBytes
        } catch (e: IllegalArgumentException) {
            0L
        }
    }

    private class SaveTarget(
        val directory: String,
        val subDirectory: String?,
        val displayName: String,
        val mimeType: String,
    )

    // Inserts a pending row first so other apps never see a half-written file, then publishes it.
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveToMediaStore(
        context: Context,
        collection: Uri,
        target: SaveTarget,
        write: (OutputStream) -> Boolean,
    ): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, target.displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, target.mimeType)
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                listOfNotNull(target.directory, target.subDirectory).joinToString("/")
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = try {
            resolver.insert(collection, values)
        } catch (e: IllegalArgumentException) {
            null // e.g. a MIME type the collection doesn't accept
        } catch (e: SecurityException) {
            null
        }
        if (uri == null) return null

        val written = try {
            resolver.openOutputStream(uri)?.use(write) == true
        } catch (e: IOException) {
            false
        } catch (e: SecurityException) {
            false
        }
        return try {
            if (written) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } else {
                resolver.delete(uri, null, null)
                null
            }
        } catch (e: SecurityException) {
            null
        }
    }

    // Shared folders are only reachable through the public directory path below API 29.
    @Suppress("DEPRECATION")
    private suspend fun saveToPublicDirectory(
        context: Context,
        target: SaveTarget,
        write: (OutputStream) -> Boolean,
    ): Uri? {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission || !isWritable()) return null

        val publicDir = Environment.getExternalStoragePublicDirectory(target.directory)
        val dir = target.subDirectory?.let { File(publicDir, it) } ?: publicDir
        if (!dir.mkdirs() && !dir.isDirectory) return null

        val file = FileUtils.uniqueFile(dir, withExtension(target.displayName, target.mimeType))
        val written = try {
            file.outputStream().use(write)
        } catch (e: IOException) {
            false
        }
        if (!written) {
            file.delete()
            return null
        }
        return scanFile(context, file, target.mimeType) ?: Uri.fromFile(file)
    }

    /** Adds [file] to MediaStore so galleries see it; returns its content Uri, or `null` on timeout. */
    private suspend fun scanFile(context: Context, file: File, mimeType: String): Uri? =
        withTimeoutOrNull(SCAN_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                MediaScannerConnection.scanFile(
                    context.applicationContext,
                    arrayOf(file.absolutePath),
                    arrayOf(mimeType)
                ) { _, uri ->
                    if (continuation.isActive) continuation.resume(uri)
                }
            }
        }

    // MediaStore adds the extension itself on API 29+; a plain file needs it in its name.
    private fun withExtension(fileName: String, mimeType: String): String {
        if (FileUtils.getExtension(fileName).isNotEmpty()) return fileName
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: return fileName
        return "$fileName.$extension"
    }

    private fun imageMimeTypeOf(format: Bitmap.CompressFormat): String = when {
        format == Bitmap.CompressFormat.PNG -> "image/png"
        format.name.startsWith("WEBP") -> "image/webp"
        else -> "image/jpeg"
    }
}
