package com.example.codebase.utils

import android.content.Context
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * App-private files in `Context.filesDir`: no permission needed, invisible to other apps, removed on
 * uninstall. Use [CacheUtils] for data that can be recreated, since the system never clears `filesDir`.
 *
 * `relativePath` may contain sub-directories (`"exports/report.csv"`). Blank or absolute paths and paths
 * that escape `filesDir` (`"../x"`) are rejected. I/O runs on [Dispatchers.IO]; failures return
 * `null` or `false`.
 */
object InternalStorageUtils {

    private const val SHARED_DIR = "shared"

    /** Resolves [relativePath] inside `filesDir`, or `null` if it is rejected (see class docs). */
    fun getFile(context: Context, relativePath: String): File? =
        FileUtils.resolveChild(context.filesDir, relativePath)

    suspend fun writeText(context: Context, relativePath: String, text: String): Boolean = io {
        getFile(context, relativePath)?.let { FileUtils.writeTextAtomically(it, text) } ?: false
    }

    suspend fun readText(context: Context, relativePath: String): String? = io {
        getFile(context, relativePath)?.let(FileUtils::readTextOrNull)
    }

    suspend fun writeBytes(context: Context, relativePath: String, bytes: ByteArray): Boolean = io {
        getFile(context, relativePath)?.let { FileUtils.writeBytesAtomically(it, bytes) } ?: false
    }

    suspend fun readBytes(context: Context, relativePath: String): ByteArray? = io {
        getFile(context, relativePath)?.let(FileUtils::readBytesOrNull)
    }

    /** Deletes a file or a whole directory. Returns `true` if nothing is left at [relativePath]. */
    suspend fun delete(context: Context, relativePath: String): Boolean = io {
        val file = getFile(context, relativePath)
        file != null && (!file.exists() || file.deleteRecursively())
    }

    suspend fun exists(context: Context, relativePath: String): Boolean = io {
        getFile(context, relativePath)?.exists() == true
    }

    /** Files and directories directly inside [subDir]; `""` lists `filesDir` itself. */
    suspend fun listFiles(context: Context, subDir: String = ""): List<File> = io {
        val dir = if (subDir.isBlank()) context.filesDir else getFile(context, subDir)
        dir?.listFiles()?.toList().orEmpty()
    }

    /**
     * `filesDir/shared`: the only part of `filesDir` exposed through FileProvider
     * (`res/xml/file_paths.xml`). Put files here before passing them to [IntentUtils.shareFile].
     */
    fun sharedDir(context: Context): File = File(context.filesDir, SHARED_DIR).apply { mkdirs() }

    /** Free bytes on the volume that holds `filesDir`. */
    fun getAvailableBytes(context: Context): Long = StatFs(context.filesDir.path).availableBytes

    private suspend inline fun <T> io(crossinline block: () -> T): T =
        withContext(Dispatchers.IO) { block() }
}
