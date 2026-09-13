package com.example.codebase.presentation.postlist

import com.example.codebase.domain.model.Post

data class PostListUiState(
    val posts: List<Post> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
