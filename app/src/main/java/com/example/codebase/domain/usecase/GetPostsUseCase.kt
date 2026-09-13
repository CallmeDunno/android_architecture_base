package com.example.codebase.domain.usecase

import com.example.codebase.domain.model.Post
import com.example.codebase.domain.repository.PostRepository
import com.example.codebase.domain.util.Resource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetPostsUseCase @Inject constructor(
    private val repository: PostRepository
) {
    operator fun invoke(): Flow<Resource<List<Post>>> = repository.getPosts()
}
