package com.example.codebase.base

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private data class DiffTestItem(val id: Int, val name: String)

class BaseDiffCallbackTest {

    private val callback = BaseDiffCallback<DiffTestItem> { it.id }

    @Test
    fun `items with the same id are the same item`() {
        assertTrue(callback.areItemsTheSame(DiffTestItem(1, "a"), DiffTestItem(1, "b")))
    }

    @Test
    fun `items with different ids are different items`() {
        assertFalse(callback.areItemsTheSame(DiffTestItem(1, "a"), DiffTestItem(2, "a")))
    }

    @Test
    fun `contents are compared with equals`() {
        assertTrue(callback.areContentsTheSame(DiffTestItem(1, "a"), DiffTestItem(1, "a")))
        assertFalse(callback.areContentsTheSame(DiffTestItem(1, "a"), DiffTestItem(1, "b")))
    }
}
