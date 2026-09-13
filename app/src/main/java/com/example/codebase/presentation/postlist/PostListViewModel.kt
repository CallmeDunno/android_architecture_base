package com.example.codebase.presentation.postlist

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.codebase.domain.usecase.GetPostsUseCase
import com.example.codebase.domain.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class PostListViewModel @Inject constructor(
    private val getPostsUseCase: GetPostsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(PostListUiState())
    val uiState: StateFlow<PostListUiState> = _uiState.asStateFlow()

    // LiveData variant is also exposed here to show that either style is available,
    // even though PostListActivity consumes uiState (StateFlow) directly.
    val uiStateLiveData: LiveData<PostListUiState> = uiState.asLiveData()

    private var loadJob: Job? = null

    init {
        loadPosts()
    }

    fun loadPosts() {
        // The repository flow ends in a Room Flow that never completes, so an uncancelled
        // previous collection would stay alive for the lifetime of the ViewModel.
        loadJob?.cancel()
        loadJob = getPostsUseCase().onEach { resource ->
            _uiState.update { current ->
                when (resource) {
                    is Resource.Loading -> current.copy(isLoading = true)
                    is Resource.Success -> current.copy(isLoading = false, posts = resource.data, error = null)
                    is Resource.Error -> current.copy(
                        isLoading = false,
                        error = resource.message,
                        posts = resource.data ?: current.posts
                    )
                }
            }
        }.launchIn(viewModelScope)
    }
}
