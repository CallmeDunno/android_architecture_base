package com.example.codebase.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class PostDto(
    val id: Int,
    val userId: Int,
    val title: String,
    val body: String
)
