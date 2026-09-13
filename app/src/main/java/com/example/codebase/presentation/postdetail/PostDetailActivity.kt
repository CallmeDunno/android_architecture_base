package com.example.codebase.presentation.postdetail

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import com.example.codebase.base.BaseActivity
import com.example.codebase.databinding.ActivityPostDetailBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PostDetailActivity : BaseActivity<ActivityPostDetailBinding>(ActivityPostDetailBinding::inflate) {

    companion object {
        const val EXTRA_POST_ID = "extra_post_id"
    }

    private val viewModel: PostDetailViewModel by viewModels()

    override fun initView(savedInstanceState: Bundle?) {
        // Guard against reloading on simple rotation, while still covering the
        // process-death-recreation case where the ViewModel itself is freshly created.
        val alreadyLoadedOrLoading = viewModel.uiState.value.let { it.post != null || it.isLoading }
        if (!alreadyLoadedOrLoading) {
            val postId = intent.getIntExtra(EXTRA_POST_ID, -1)
            check(postId != -1) { "PostDetailActivity requires EXTRA_POST_ID" }
            viewModel.loadPost(postId)
        }
    }

    override fun observeData() {
        // uiStateLiveData is observed here (instead of collecting uiState/StateFlow)
        // to demonstrate the LiveData consumption style for this boilerplate.
        viewModel.uiStateLiveData.observe(this, ::render)
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
