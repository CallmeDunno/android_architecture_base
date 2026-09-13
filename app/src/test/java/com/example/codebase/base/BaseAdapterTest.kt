package com.example.codebase.base

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// BaseAdapter itself can't be instantiated in JVM tests (RecyclerView.Adapter's observer list comes
// from the stubbed android.jar), so its drag & drop reorder logic is tested through withItemMoved.
class BaseAdapterTest {

    private val items = listOf(1, 2, 3, 4)

    @Test
    fun `moving down places the item at the target position`() {
        assertEquals(listOf(2, 3, 1, 4), items.withItemMoved(0, 2))
    }

    @Test
    fun `moving up places the item at the target position`() {
        assertEquals(listOf(1, 4, 2, 3), items.withItemMoved(3, 1))
    }

    @Test
    fun `adjacent move swaps the two items`() {
        assertEquals(listOf(1, 3, 2, 4), items.withItemMoved(1, 2))
    }

    @Test
    fun `invalid or identical positions return null`() {
        assertNull(items.withItemMoved(-1, 0))
        assertNull(items.withItemMoved(0, 4))
        assertNull(items.withItemMoved(2, 2))
    }

    @Test
    fun `original list is not mutated`() {
        val source = mutableListOf(1, 2, 3)

        source.withItemMoved(0, 2)

        assertEquals(listOf(1, 2, 3), source)
    }
}
