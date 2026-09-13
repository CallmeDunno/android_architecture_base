package com.example.codebase.presentation.postlist

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.codebase.base.BaseActivity
import com.example.codebase.databinding.ActivityPostListBinding
import com.example.codebase.domain.model.Post
import com.example.codebase.presentation.postdetail.PostDetailActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PostListActivity : BaseActivity<ActivityPostListBinding>(ActivityPostListBinding::inflate) {

    private val viewModel: PostListViewModel by viewModels()
    private val adapter = PostAdapter(onPostClick = ::openPostDetail)

    override fun initView(savedInstanceState: Bundle?) {
        binding.recyclerPosts.layoutManager = LinearLayoutManager(this)
        binding.recyclerPosts.adapter = adapter
    }

    override fun initListeners() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadPosts()
        }
    }

    override fun observeData() {
        viewModel.uiState.collectWhenStarted(::render)
    }

    private fun render(state: PostListUiState) {
        binding.swipeRefresh.isRefreshing = state.isLoading && state.posts.isNotEmpty()
        binding.progressBar.visibility =
            if (state.isLoading && state.posts.isEmpty()) View.VISIBLE else View.GONE
        adapter.submitList(state.posts)

        if (state.error != null) {
            binding.textError.visibility = View.VISIBLE
            binding.textError.text = state.error
        } else {
            binding.textError.visibility = View.GONE
        }
    }

    private fun openPostDetail(post: Post) {
        startActivity(
            Intent(this, PostDetailActivity::class.java)
                .putExtra(PostDetailActivity.EXTRA_POST_ID, post.id)
        )
    }
}
