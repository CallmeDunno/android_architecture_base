package com.example.codebase.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IntentUtilsTest {

    @Test
    fun `url without a scheme gets https`() {
        assertEquals("https://example.com", normalizeUrl("example.com"))
        assertEquals("https://example.com:8080/path", normalizeUrl("example.com:8080/path"))
        assertEquals("https://localhost:3000", normalizeUrl("localhost:3000"))
    }

    @Test
    fun `url with a scheme is kept and trimmed`() {
        assertEquals("https://example.com/a", normalizeUrl("  https://example.com/a  "))
        assertEquals("http://example.com", normalizeUrl("http://example.com"))
        assertEquals("myapp://open/1", normalizeUrl("myapp://open/1"))
    }

    @Test
    fun `blank url returns null`() {
        assertNull(normalizeUrl(""))
        assertNull(normalizeUrl("   "))
    }
}
