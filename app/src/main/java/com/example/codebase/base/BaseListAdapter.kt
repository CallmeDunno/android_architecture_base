package com.example.codebase.base

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding

/**
 * Default base for RecyclerView adapters: a [ListAdapter] (background DiffUtil, update via `submitList`)
 * whose ViewHolder just carries a ViewBinding. Same hooks as [BaseAdapter]; use [BaseAdapter] instead
 * only when the list supports drag & drop with `ItemTouchHelper`.
 *
 * - **View types:** override `getItemViewType` and create the binding per `viewType` in [createBinding].
 *   With several layouts use `VB = ViewBinding` and `when (binding) { is ItemABinding -> ... }`.
 * - **Clicks:** the base wires none. Set listeners once in [onViewHolderCreated] and resolve the item
 *   with [getItemOrNull] at click time — never capture `position` / `item` from [bind] for a click.
 */
abstract class BaseListAdapter<T : Any, VB : ViewBinding>(
    diffCallback: DiffUtil.ItemCallback<T>
) : ListAdapter<T, BaseViewHolder<VB>>(diffCallback) {

    protected abstract fun createBinding(inflater: LayoutInflater, parent: ViewGroup, viewType: Int): VB

    protected abstract fun bind(binding: VB, item: T, position: Int)

    /** Partial rebind for payloads from `DiffUtil.ItemCallback.getChangePayload`. Defaults to a full [bind]. */
    protected open fun bindPayloads(binding: VB, item: T, position: Int, payloads: List<Any>) {
        bind(binding, item, position)
    }

    /** Called once per ViewHolder: the place for item / child-view click listeners. */
    protected open fun onViewHolderCreated(holder: BaseViewHolder<VB>, viewType: Int) {}

    /** The item currently bound to [holder], or null while it is being removed or re-laid out. */
    protected fun getItemOrNull(holder: RecyclerView.ViewHolder): T? {
        val position = holder.bindingAdapterPosition
        return if (position == RecyclerView.NO_POSITION) null else currentList.getOrNull(position)
    }

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
}
