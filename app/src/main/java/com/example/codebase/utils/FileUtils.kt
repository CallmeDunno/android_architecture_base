package com.example.codebase.utils

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Locale

/**
 * Pure `java.io` helpers shared by the storage and cache utils. No Android APIs, so everything here
 * is covered by JVM unit tests.
 *
 * All functions block on disk I/O: call them from a background dispatcher, never the main thread.
 * The Context-based utils ([InternalStorageUtils], [CacheUtils], [AssetUtils]) already wrap them in
 * `withContext(Dispatchers.IO)`.
 */
object FileUtils {

    private const val DEFAULT_FILE_NAME = "file"
    private const val TEMP_PREFIX = "tmp_"
    private const val TEMP_SUFFIX = ".tmp"
    private val ILLEGAL_FILE_NAME_CHARS = Regex("""[\\/:*?"<>|\x00-\x1F]""")

    /**
     * Makes [name] safe to use as a single path segment: characters that are illegal on common file
     * systems become `_`, leading/trailing dots and whitespace are removed and the result is cut to
     * [maxLength] characters. Returns `"file"` if nothing usable is left (e.g. for `".."`).
     */
    fun sanitizeFileName(name: String, maxLength: Int = 127): String {
        require(maxLength > 0) { "maxLength must be positive: $maxLength" }
        val cleaned = name.replace(ILLEGAL_FILE_NAME_CHARS, "_").trimDotsAndWhitespace()
        val truncated = cleaned.take(maxLength)
            .let { if (it.isNotEmpty() && it.last().isHighSurrogate()) it.dropLast(1) else it }
            .trimDotsAndWhitespace()
        return truncated.ifEmpty { DEFAULT_FILE_NAME }
    }

    /**
     * Resolves [relativePath] against [baseDir]. Returns `null` if the path is blank or absolute, or
     * if the resolved file would not be strictly inside [baseDir] (for example through `../`).
     */
    fun resolveChild(baseDir: File, relativePath: String): File? {
        if (relativePath.isBlank() || File(relativePath).isAbsolute) return null
        return try {
            val base = baseDir.canonicalFile
            val child = File(base, relativePath).canonicalFile
            if (child.path.startsWith(base.path + File.separator)) child else null
        } catch (e: IOException) {
            null
        }
    }

    /**
     * Writes [bytes] to a temporary sibling first and then renames it over [file], so readers never
     * see a half-written file. Creates missing parent directories. Returns `false` on I/O failure.
     */
    fun writeBytesAtomically(file: File, bytes: ByteArray): Boolean =
        writeAtomically(file) { it.writeBytes(bytes) }

    /** UTF-8 variant of [writeBytesAtomically]. */
    fun writeTextAtomically(file: File, text: String): Boolean =
        writeAtomically(file) { it.writeText(text) }

    /** Copies [input] into [destination] atomically. [input] is not closed. */
    fun copyStreamAtomically(input: InputStream, destination: File): Boolean =
        writeAtomically(destination) { temp -> temp.outputStream().use { input.copyTo(it) } }

    /** UTF-8 content of [file], or `null` if it isn't a readable file. */
    fun readTextOrNull(file: File): String? = readOrNull(file) { it.readText() }

    /** Content of [file], or `null` if it isn't a readable file. */
    fun readBytesOrNull(file: File): ByteArray? = readOrNull(file) { it.readBytes() }

    /** Size in bytes of a file, or the total size of all files under a directory. `0` if missing. */
    fun sizeOf(file: File): Long = when {
        file.isFile -> file.length()
        file.isDirectory -> file.listFiles()?.sumOf { sizeOf(it) } ?: 0L
        else -> 0L
    }

    /**
     * Deletes everything inside [dir] but keeps [dir] itself. Returns `true` if [dir] is empty
     * afterwards, which includes a [dir] that doesn't exist.
     */
    fun deleteContents(dir: File): Boolean {
        if (!dir.exists()) return true
        if (!dir.isDirectory) return false
        val children = dir.listFiles() ?: return false
        var success = true
        children.forEach { child -> if (!child.deleteRecursively()) success = false }
        return success
    }

    /**
     * [fileName] inside [dir], or `name (1).ext`, `name (2).ext`, ... if that name is taken. Only picks
     * a name; nothing is created, so another writer can still take it before you do.
     */
    fun uniqueFile(dir: File, fileName: String): File {
        val dot = fileName.lastIndexOf('.').takeIf { it > 0 } ?: fileName.length
        val baseName = fileName.substring(0, dot)
        val extension = fileName.substring(dot)
        var candidate = File(dir, fileName)
        var index = 1
        while (candidate.exists()) {
            candidate = File(dir, "$baseName ($index)$extension")
            index++
        }
        return candidate
    }

    /** Lower-cased extension without the dot (`"photo.JPG"` → `"jpg"`); empty if there is none. */
    fun getExtension(fileName: String): String {
        val name = fileName.substringAfterLast('/').substringAfterLast('\\')
        val dot = name.lastIndexOf('.')
        return if (dot <= 0 || dot == name.lastIndex) "" else name.substring(dot + 1).lowercase(Locale.ROOT)
    }

    private inline fun writeAtomically(file: File, write: (File) -> Unit): Boolean {
        if (file.isDirectory) return false
        val parent = file.absoluteFile.parentFile ?: return false
        if (!parent.mkdirs() && !parent.isDirectory) return false
        var temp: File? = null
        return try {
            val created = File.createTempFile(TEMP_PREFIX, TEMP_SUFFIX, parent).also { temp = it }
            write(created)
            replace(created, file.absoluteFile)
        } catch (e: IOException) {
            false
        } finally {
            temp?.takeIf { it.exists() }?.delete()
        }
    }

    // renameTo replaces an existing target atomically on Android (POSIX rename). Some platforms, such
    // as the Windows JVM that runs the unit tests, refuse to rename over an existing file.
    private fun replace(source: File, target: File): Boolean {
        if (source.renameTo(target)) return true
        return target.delete() && source.renameTo(target)
    }

    private inline fun <T> readOrNull(file: File, read: (File) -> T): T? {
        if (!file.isFile) return null
        return try {
            read(file)
        } catch (e: IOException) {
            null
        }
    }

    private fun String.trimDotsAndWhitespace(): String = trim { it == '.' || it.isWhitespace() }
}
