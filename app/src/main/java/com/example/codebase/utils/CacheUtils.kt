package com.example.codebase.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * App cache in `Context.cacheDir`, plus `externalCacheDir` for size and clearing. The system may delete
 * these files when storage runs low, so only keep data that can be recreated.
 * I/O runs on [Dispatchers.IO]; failures return `null` or `false`.
 */
object CacheUtils {

    private const val SHARED_DIR = "shared"
    private const val TEMP_DIR = "tmp"

    /** Total bytes used by `cacheDir` and `externalCacheDir`. */
    suspend fun getCacheSize(context: Context): Long = withContext(Dispatchers.IO) {
        cacheDirs(context).sumOf { FileUtils.sizeOf(it) }
    }

    /** [getCacheSize] formatted by [FormatUtils.formatFileSize], e.g. `12.4 MB`. */
    suspend fun getFormattedCacheSize(context: Context): String =
        FormatUtils.formatFileSize(getCacheSize(context))

    /**
     * Deletes the contents of `cacheDir` and `externalCacheDir` but keeps the directories. This also
     * removes caches written by libraries (for example an OkHttp or image cache).
     */
    suspend fun clearCache(context: Context): Boolean = withContext(Dispatchers.IO) {
        cacheDirs(context).map { FileUtils.deleteContents(it) }.all { it }
    }

    /** Creates an empty, uniquely named file in `cacheDir/tmp`; delete it when done. `null` on failure. */
    suspend fun createTempFile(context: Context, prefix: String = "tmp_", suffix: String? = null): File? =
        withContext(Dispatchers.IO) {
            try {
                val dir = File(context.cacheDir, TEMP_DIR).apply { mkdirs() }
                // createTempFile requires a prefix of at least three characters.
                File.createTempFile(FileUtils.sanitizeFileName(prefix).padEnd(3, '_'), suffix, dir)
            } catch (e: IOException) {
                null
            }
        }

    /** Writes [bytes] to `cacheDir/<name>` ([name] is sanitized) and returns the file, or `null` on failure. */
    suspend fun writeToCache(context: Context, name: String, bytes: ByteArray): File? =
        withContext(Dispatchers.IO) {
            val file = File(context.cacheDir, FileUtils.sanitizeFileName(name))
            if (FileUtils.writeBytesAtomically(file, bytes)) file else null
        }

    /**
     * `cacheDir/shared`: the only part of the cache exposed through FileProvider
     * (`res/xml/file_paths.xml`). Put temporary files here before passing them to [IntentUtils.shareFile].
     */
    fun sharedCacheDir(context: Context): File = File(context.cacheDir, SHARED_DIR).apply { mkdirs() }

    private fun cacheDirs(context: Context): List<File> =
        listOfNotNull(context.cacheDir, context.externalCacheDir)
}
