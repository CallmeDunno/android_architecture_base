package com.example.codebase.presentation.postlist

import androidx.lifecycle.viewModelScope
import com.example.codebase.base.BaseViewModel
import com.example.codebase.domain.usecase.GetPostsUseCase
import com.example.codebase.domain.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class PostListViewModel @Inject constructor(
    private val getPostsUseCase: GetPostsUseCase
) : BaseViewModel<PostListUiState>(PostListUiState()) {

    private var loadJob: Job? = null

    init {
        loadPosts()
    }

    fun loadPosts() {
        // The repository flow ends in a Room Flow that never completes, so an uncancelled
        // previous collection would stay alive for the lifetime of the ViewModel.
        loadJob?.cancel()
        loadJob = getPostsUseCase().onEach { resource ->
            setState {
                when (resource) {
                    is Resource.Loading -> copy(isLoading = true)
                    is Resource.Success -> copy(isLoading = false, posts = resource.data, error = null)
                    is Resource.Error -> copy(
                        isLoading = false,
                        error = resource.message,
                        posts = resource.data ?: posts
                    )
                }
            }
        }.launchIn(viewModelScope)
    }
}
