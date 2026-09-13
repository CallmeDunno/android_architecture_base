package com.example.codebase.domain.repository

import com.example.codebase.domain.model.Post
import com.example.codebase.domain.util.Resource
import kotlinx.coroutines.flow.Flow

interface PostRepository {
    fun getPosts(): Flow<Resource<List<Post>>>
    fun getPostById(id: Int): Flow<Resource<Post>>
}
