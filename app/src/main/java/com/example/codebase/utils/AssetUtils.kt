package com.example.codebase.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/**
 * Read-only access to files bundled in `app/src/main/assets`. Paths are relative to the assets root
 * without a leading slash (`"data/config.json"`). I/O runs on [Dispatchers.IO]; failures return
 * `null`, `false` or an empty list.
 */
object AssetUtils {

    @PublishedApi
    internal val defaultJson = Json { ignoreUnknownKeys = true }

    suspend fun readText(context: Context, path: String): String? = withContext(Dispatchers.IO) {
        try {
            context.assets.open(path).bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            null
        }
    }

    suspend fun readBytes(context: Context, path: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            context.assets.open(path).use { it.readBytes() }
        } catch (e: IOException) {
            null
        }
    }

    /** Names (not full paths) of the entries directly inside [dir]; `""` is the assets root. */
    suspend fun list(context: Context, dir: String = ""): List<String> = withContext(Dispatchers.IO) {
        try {
            context.assets.list(dir)?.toList().orEmpty()
        } catch (e: IOException) {
            emptyList()
        }
    }

    /** Whether [path] is a file in assets. Directories return `false`. */
    suspend fun exists(context: Context, path: String): Boolean = withContext(Dispatchers.IO) {
        try {
            context.assets.open(path).close()
            true
        } catch (e: IOException) {
            false
        }
    }

    /** Copies an asset to [destination] atomically, e.g. to seed a file on first launch. */
    suspend fun copyToFile(context: Context, assetPath: String, destination: File): Boolean =
        withContext(Dispatchers.IO) {
            try {
                context.assets.open(assetPath).use { FileUtils.copyStreamAtomically(it, destination) }
            } catch (e: IOException) {
                false
            }
        }

    /** Decodes a JSON asset into `@Serializable` [T]; `null` if the file is missing or doesn't match. */
    suspend inline fun <reified T> readJson(context: Context, path: String, json: Json = defaultJson): T? =
        readText(context, path)?.let { json.decodeOrNull<T>(it) }
}

/** Returns `null` instead of throwing when [text] isn't valid JSON for [T]. */
@PublishedApi
internal inline fun <reified T> Json.decodeOrNull(text: String): T? = try {
    decodeFromString<T>(text)
} catch (e: IllegalArgumentException) {
    // SerializationException (malformed JSON, missing or mistyped fields) extends IllegalArgumentException.
    null
}
