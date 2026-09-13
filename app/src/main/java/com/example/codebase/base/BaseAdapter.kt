package com.example.codebase.base

import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import java.util.concurrent.Executor

/**
 * Base for RecyclerView adapters that support drag & drop with `ItemTouchHelper`. Lists without
 * drag & drop should extend [BaseListAdapter]; both share the same hooks and [BaseViewHolder].
 *
 * - **Updates:** [submitList] diffs on a background thread (like `ListAdapter`) using [diffCallback].
 * - **View types:** override `getItemViewType` and create the binding per `viewType` in [createBinding].
 *   With several layouts use `VB = ViewBinding` and `when (binding) { is ItemABinding -> ... }`.
 * - **Clicks:** the base wires none. Set listeners once in [onViewHolderCreated] and resolve the item
 *   with [getItemOrNull] at click time — never capture `position` / `item` from [bind] for a click.
 * - **Drag & drop:** call [moveItem] from `ItemTouchHelper.Callback.onMove`. It updates [currentList]
 *   and notifies synchronously, which `ListAdapter` cannot do. Push [currentList] to the ViewModel when
 *   the drag ends, otherwise the next state emission restores the old order.
 */
abstract class BaseAdapter<T : Any, VB : ViewBinding>(
    private val diffCallback: DiffUtil.ItemCallback<T>
) : RecyclerView.Adapter<BaseViewHolder<VB>>() {

    var currentList: List<T> = emptyList()
        private set

    // Bumped by every submitList/moveItem; a background diff computed for an older generation is dropped.
    private var generation = 0

    private val diffExecutor: Executor by lazy { AsyncDifferConfig.Builder(diffCallback).build().backgroundThreadExecutor }
    private val mainHandler: Handler by lazy { Handler(Looper.getMainLooper()) }

    protected abstract fun createBinding(inflater: LayoutInflater, parent: ViewGroup, viewType: Int): VB

    protected abstract fun bind(binding: VB, item: T, position: Int)

    /** Partial rebind for payloads from `DiffUtil.ItemCallback.getChangePayload`. Defaults to a full [bind]. */
    protected open fun bindPayloads(binding: VB, item: T, position: Int, payloads: List<Any>) {
        bind(binding, item, position)
    }

    /** Called once per ViewHolder: the place for item / child-view click and drag-handle listeners. */
    protected open fun onViewHolderCreated(holder: BaseViewHolder<VB>, viewType: Int) {}

    fun getItem(position: Int): T = currentList[position]

    /** The item currently bound to [holder], or null while it is being removed or re-laid out. */
    protected fun getItemOrNull(holder: RecyclerView.ViewHolder): T? {
        val position = holder.bindingAdapterPosition
        return if (position == RecyclerView.NO_POSITION) null else currentList.getOrNull(position)
    }

    /**
     * Replaces the list. The diff runs in the background; [onCommitted] runs on the main thread once
     * the list is displayed. A newer [submitList] or a [moveItem] discards a diff still in flight.
     */
    fun submitList(list: List<T>?, onCommitted: (() -> Unit)? = null) {
        val runGeneration = ++generation
        val oldList = currentList
        val newList = list.orEmpty().toList() // copy: later mutations of the caller's list must not leak in

        when {
            oldList == newList -> {
                onCommitted?.invoke()
                return
            }
            oldList.isEmpty() -> {
                currentList = newList
                notifyItemRangeInserted(0, newList.size)
                onCommitted?.invoke()
                return
            }
            newList.isEmpty() -> {
                currentList = newList
                notifyItemRangeRemoved(0, oldList.size)
                onCommitted?.invoke()
                return
            }
        }

        diffExecutor.execute {
            val result = DiffUtil.calculateDiff(ItemListDiff(oldList, newList, diffCallback))
            mainHandler.post {
                if (runGeneration != generation) return@post
                currentList = newList
                result.dispatchUpdatesTo(this)
                onCommitted?.invoke()
            }
        }
    }

    /** Moves an item synchronously for `ItemTouchHelper.Callback.onMove`. Returns false for invalid positions. */
    fun moveItem(fromPosition: Int, toPosition: Int): Boolean {
        val movedList = currentList.withItemMoved(fromPosition, toPosition) ?: return false

        generation++ // a pending diff was computed against the pre-move list and would dispatch wrong updates
        currentList = movedList
        notifyItemMoved(fromPosition, toPosition)
        return true
    }

    final override fun getItemCount(): Int = currentList.size

    final override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder<VB> {
        val holder = BaseViewHolder(createBinding(LayoutInflater.from(parent.context), parent, viewType))
        onViewHolderCreated(holder, viewType)
        return holder
    }

    final override fun onBindViewHolder(holder: BaseViewHolder<VB>, position: Int) {
        bind(holder.binding, getItem(position), position)
    }

    final override fun onBindViewHolder(holder: BaseViewHolder<VB>, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            bind(holder.binding, getItem(position), position)
        } else {
            bindPayloads(holder.binding, getItem(position), position, payloads)
        }
    }

    private class ItemListDiff<T : Any>(
        private val oldList: List<T>,
        private val newList: List<T>,
        private val itemCallback: DiffUtil.ItemCallback<T>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
            itemCallback.areItemsTheSame(oldList[oldItemPosition], newList[newItemPosition])
        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
            itemCallback.areContentsTheSame(oldList[oldItemPosition], newList[newItemPosition])
        override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any? =
            itemCallback.getChangePayload(oldList[oldItemPosition], newList[newItemPosition])
    }
}

/**
 * Copy of this list with the element at [fromPosition] moved to [toPosition] (the `notifyItemMoved`
 * semantics), or null when either position is out of range or they are equal.
 * Kept outside [BaseAdapter] so it is unit-testable: `RecyclerView.Adapter` can't be built on the JVM.
 */
internal fun <T> List<T>.withItemMoved(fromPosition: Int, toPosition: Int): List<T>? {
    if (fromPosition !in indices || toPosition !in indices || fromPosition == toPosition) return null
    return toMutableList().apply { add(toPosition, removeAt(fromPosition)) }
}
