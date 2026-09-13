package com.example.codebase.domain.usecase

import com.example.codebase.domain.model.Post
import com.example.codebase.domain.repository.PostRepository
import com.example.codebase.domain.util.Resource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetPostByIdUseCase @Inject constructor(
    private val repository: PostRepository
) {
    operator fun invoke(id: Int): Flow<Resource<Post>> = repository.getPostById(id)
}
