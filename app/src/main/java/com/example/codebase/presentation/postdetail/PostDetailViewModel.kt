package com.example.codebase.presentation.postdetail

import androidx.lifecycle.viewModelScope
import com.example.codebase.base.BaseViewModel
import com.example.codebase.domain.usecase.GetPostByIdUseCase
import com.example.codebase.domain.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class PostDetailViewModel @Inject constructor(
    private val getPostByIdUseCase: GetPostByIdUseCase
) : BaseViewModel<PostDetailUiState>(PostDetailUiState()) {

    private var loadJob: Job? = null

    fun loadPost(id: Int) {
        loadJob?.cancel()
        loadJob = getPostByIdUseCase(id).onEach { resource ->
            setState {
                when (resource) {
                    is Resource.Loading -> copy(isLoading = true)
                    is Resource.Success -> copy(isLoading = false, post = resource.data, error = null)
                    is Resource.Error -> copy(
                        isLoading = false,
                        error = resource.message,
                        post = resource.data ?: post
                    )
                }
            }
        }.launchIn(viewModelScope)
    }
}
