package com.example.codebase.base

import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding

/** ViewHolder shared by [BaseListAdapter] and [BaseAdapter]: it only carries the item's ViewBinding. */
class BaseViewHolder<VB : ViewBinding>(val binding: VB) : RecyclerView.ViewHolder(binding.root)
