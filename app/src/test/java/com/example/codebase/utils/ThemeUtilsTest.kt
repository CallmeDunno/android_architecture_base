package com.example.codebase.utils

import androidx.appcompat.app.AppCompatDelegate
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeUtilsTest {

    @Test
    fun `fromStorageKey maps every saved key back to its mode`() {
        ThemeMode.entries.forEach { mode ->
            assertEquals(mode, ThemeMode.fromStorageKey(mode.storageKey))
        }
    }

    @Test
    fun `unknown or missing key falls back to SYSTEM`() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorageKey(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorageKey("sepia"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorageKey("LIGHT"))
    }

    @Test
    fun `modes map to AppCompat night modes`() {
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, ThemeMode.LIGHT.nightMode)
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, ThemeMode.DARK.nightMode)
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, ThemeMode.SYSTEM.nightMode)
    }

    @Test
    fun `storage keys stay stable because they are persisted`() {
        assertEquals(listOf("light", "dark", "system"), ThemeMode.entries.map { it.storageKey })
    }
}
