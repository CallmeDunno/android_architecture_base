package com.example.codebase.presentation.postlist

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.codebase.databinding.ActivityPostListBinding
import com.example.codebase.domain.model.Post
import com.example.codebase.presentation.postdetail.PostDetailActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PostListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPostListBinding
    private val viewModel: PostListViewModel by viewModels()
    private val adapter = PostAdapter(onPostClick = ::openPostDetail)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityPostListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.recyclerPosts.layoutManager = LinearLayoutManager(this)
        binding.recyclerPosts.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadPosts()
        }

        observeUiState()
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
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
