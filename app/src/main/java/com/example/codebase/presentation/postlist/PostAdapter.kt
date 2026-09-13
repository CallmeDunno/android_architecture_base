package com.example.codebase.presentation.postlist

import android.view.LayoutInflater
import android.view.ViewGroup
import com.example.codebase.base.BaseDiffCallback
import com.example.codebase.base.BaseListAdapter
import com.example.codebase.base.BaseViewHolder
import com.example.codebase.databinding.ItemPostBinding
import com.example.codebase.domain.model.Post

class PostAdapter(
    private val onPostClick: (Post) -> Unit
) : BaseListAdapter<Post, ItemPostBinding>(BaseDiffCallback { it.id }) {

    override fun createBinding(inflater: LayoutInflater, parent: ViewGroup, viewType: Int): ItemPostBinding =
        ItemPostBinding.inflate(inflater, parent, false)

    override fun onViewHolderCreated(holder: BaseViewHolder<ItemPostBinding>, viewType: Int) {
        holder.binding.root.setOnClickListener { getItemOrNull(holder)?.let(onPostClick) }
    }

    override fun bind(binding: ItemPostBinding, item: Post, position: Int) {
        binding.textTitle.text = item.title
        binding.textBody.text = item.body
    }
}
