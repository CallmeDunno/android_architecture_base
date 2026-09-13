package com.example.codebase.data.mapper

import com.example.codebase.data.local.PostEntity
import com.example.codebase.data.remote.dto.PostDto
import com.example.codebase.domain.model.Post

fun PostDto.toEntity(): PostEntity = PostEntity(
    id = id,
    userId = userId,
    title = title,
    body = body
)

fun PostEntity.toDomain(): Post = Post(
    id = id,
    userId = userId,
    title = title,
    body = body
)
