package com.example.codebase.presentation.postdetail

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.codebase.domain.usecase.GetPostByIdUseCase
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
class PostDetailViewModel @Inject constructor(
    private val getPostByIdUseCase: GetPostByIdUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(PostDetailUiState())
    val uiState: StateFlow<PostDetailUiState> = _uiState.asStateFlow()

    // Detail screen demonstrates observing LiveData instead of StateFlow directly.
    val uiStateLiveData: LiveData<PostDetailUiState> = uiState.asLiveData()

    private var loadJob: Job? = null

    fun loadPost(id: Int) {
        loadJob?.cancel()
        loadJob = getPostByIdUseCase(id).onEach { resource ->
            _uiState.update { current ->
                when (resource) {
                    is Resource.Loading -> current.copy(isLoading = true)
                    is Resource.Success -> current.copy(isLoading = false, post = resource.data, error = null)
                    is Resource.Error -> current.copy(
                        isLoading = false,
                        error = resource.message,
                        post = resource.data ?: current.post
                    )
                }
            }
        }.launchIn(viewModelScope)
    }
}
