package com.example.codebase.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Typed access to the app's shared preferences file (`app_prefs`).
 *
 * - Values are stored as plain, unencrypted XML: never put tokens, passwords or other secrets here.
 * - Writes use `apply()`: the in-memory value changes immediately, the disk write is asynchronous.
 * - Reading a key with a different type than it was written with throws `ClassCastException`.
 * - The first access loads the whole file from disk, so keep large data out of it.
 */
object PrefsUtils {

    private const val PREFS_NAME = "app_prefs"

    @PublishedApi
    internal val json = Json { ignoreUnknownKeys = true }

    fun prefs(context: Context): SharedPreferences =
        (context.applicationContext ?: context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getString(context: Context, key: String, default: String? = null): String? =
        prefs(context).getString(key, default)

    fun putString(context: Context, key: String, value: String?) {
        prefs(context).edit { putString(key, value) }
    }

    fun getInt(context: Context, key: String, default: Int = 0): Int =
        prefs(context).getInt(key, default)

    fun putInt(context: Context, key: String, value: Int) {
        prefs(context).edit { putInt(key, value) }
    }

    fun getLong(context: Context, key: String, default: Long = 0L): Long =
        prefs(context).getLong(key, default)

    fun putLong(context: Context, key: String, value: Long) {
        prefs(context).edit { putLong(key, value) }
    }

    fun getFloat(context: Context, key: String, default: Float = 0f): Float =
        prefs(context).getFloat(key, default)

    fun putFloat(context: Context, key: String, value: Float) {
        prefs(context).edit { putFloat(key, value) }
    }

    fun getBoolean(context: Context, key: String, default: Boolean = false): Boolean =
        prefs(context).getBoolean(key, default)

    fun putBoolean(context: Context, key: String, value: Boolean) {
        prefs(context).edit { putBoolean(key, value) }
    }

    /** Returns a copy: the set handed out by SharedPreferences must not be modified. */
    fun getStringSet(context: Context, key: String, default: Set<String> = emptySet()): Set<String> =
        prefs(context).getStringSet(key, null)?.toSet() ?: default

    fun putStringSet(context: Context, key: String, value: Set<String>) {
        prefs(context).edit { putStringSet(key, value.toSet()) }
    }

    /** Stores [value] as JSON. [T] must be `@Serializable`. */
    inline fun <reified T> putJson(context: Context, key: String, value: T) {
        putString(context, key, json.encodeToString(value))
    }

    /** Reads a value stored with [putJson]; `null` if the key is missing or the JSON doesn't match [T]. */
    inline fun <reified T> getJson(context: Context, key: String): T? {
        val raw = getString(context, key) ?: return null
        return try {
            json.decodeFromString<T>(raw)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    fun contains(context: Context, key: String): Boolean = prefs(context).contains(key)

    fun remove(context: Context, key: String) {
        prefs(context).edit { remove(key) }
    }

    fun clear(context: Context) {
        prefs(context).edit { clear() }
    }

    /**
     * Emits every time [key] changes or is removed, until the collector is cancelled. There is no
     * initial emission: read the current value first if you need it.
     */
    fun observe(context: Context, key: String): Flow<Unit> = callbackFlow {
        val preferences = prefs(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            // changedKey is null when clear() is called (API 30+).
            if (changedKey == null || changedKey == key) trySend(Unit)
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}
