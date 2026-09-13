package com.example.codebase.presentation.postdetail

import com.example.codebase.domain.model.Post

data class PostDetailUiState(
    val post: Post? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)
