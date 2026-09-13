package com.example.codebase.presentation.postdetail

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.codebase.databinding.ActivityPostDetailBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PostDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_POST_ID = "extra_post_id"
    }

    private lateinit var binding: ActivityPostDetailBinding
    private val viewModel: PostDetailViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityPostDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // uiStateLiveData is observed here (instead of collecting uiState/StateFlow)
        // to demonstrate the LiveData consumption style for this boilerplate.
        viewModel.uiStateLiveData.observe(this) { state -> render(state) }

        // Guard against reloading on simple rotation, while still covering the
        // process-death-recreation case where the ViewModel itself is freshly created.
        val alreadyLoadedOrLoading = viewModel.uiState.value.let { it.post != null || it.isLoading }
        if (!alreadyLoadedOrLoading) {
            val postId = intent.getIntExtra(EXTRA_POST_ID, -1)
            check(postId != -1) { "PostDetailActivity requires EXTRA_POST_ID" }
            viewModel.loadPost(postId)
        }
    }

    private fun render(state: PostDetailUiState) {
        binding.progressBar.visibility = if (state.isLoading && state.post == null) View.VISIBLE else View.GONE
        binding.scrollContent.visibility = if (state.post != null) View.VISIBLE else View.GONE

        state.post?.let { post ->
            binding.textUserId.text = "User #${post.userId}"
            binding.textTitle.text = post.title
            binding.textBody.text = post.body
        }

        if (state.error != null) {
            binding.textError.visibility = View.VISIBLE
            binding.textError.text = state.error
        } else {
            binding.textError.visibility = View.GONE
        }
    }
}
