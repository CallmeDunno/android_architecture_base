package com.example.codebase.utils

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@Serializable
private data class AssetConfigFixture(val name: String, val version: Int)

// AssetManager is stubbed in JVM tests, so only the JSON decoding behind AssetUtils.readJson is tested.
class AssetUtilsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decodeOrNull decodes matching JSON and ignores unknown keys`() {
        assertEquals(
            AssetConfigFixture("demo", 2),
            json.decodeOrNull<AssetConfigFixture>("""{"name":"demo","version":2,"extra":true}""")
        )
    }

    @Test
    fun `decodeOrNull returns null for malformed JSON, wrong types and missing fields`() {
        assertNull(json.decodeOrNull<AssetConfigFixture>("""{"name":"demo""""))
        assertNull(json.decodeOrNull<AssetConfigFixture>("""{"name":"demo","version":"two"}"""))
        assertNull(json.decodeOrNull<AssetConfigFixture>("""{"version":2}"""))
    }
}
