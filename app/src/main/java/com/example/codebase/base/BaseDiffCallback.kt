package com.example.codebase.base

import android.annotation.SuppressLint
import androidx.recyclerview.widget.DiffUtil

/**
 * Generic [DiffUtil.ItemCallback] for [BaseAdapter]: items are the same when [idSelector] returns
 * equal keys, and their contents are the same when `oldItem == newItem`.
 *
 * `T` must be a `data class` (or otherwise implement `equals`), because content comparison relies
 * on structural equality. Subclass and override [areContentsTheSame] / [getChangePayload] when a
 * list needs custom rules or partial rebinds.
 *
 * Usage: `BaseAdapter<Xxx, ItemXxxBinding>(BaseDiffCallback { it.id })`
 */
open class BaseDiffCallback<T : Any>(
    private val idSelector: (T) -> Any?
) : DiffUtil.ItemCallback<T>() {

    override fun areItemsTheSame(oldItem: T, newItem: T): Boolean =
        idSelector(oldItem) == idSelector(newItem)

    // Lint can't prove a generic T overrides equals; the data-class requirement is documented above.
    @SuppressLint("DiffUtilEquals")
    override fun areContentsTheSame(oldItem: T, newItem: T): Boolean = oldItem == newItem
}
