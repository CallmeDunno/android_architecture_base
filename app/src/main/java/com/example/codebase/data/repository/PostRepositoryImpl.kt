package com.example.codebase.data.repository

import com.example.codebase.data.local.PostDao
import com.example.codebase.data.mapper.toDomain
import com.example.codebase.data.mapper.toEntity
import com.example.codebase.data.remote.ApiService
import com.example.codebase.domain.model.Post
import com.example.codebase.domain.repository.PostRepository
import com.example.codebase.domain.util.Resource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

class PostRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    private val postDao: PostDao
) : PostRepository {

    // Online-first: the network is always tried first; Room is only read as a fallback when it fails.
    override fun getPosts(): Flow<Resource<List<Post>>> = flow {
        emit(Resource.Loading())

        try {
            val remote = apiService.getPosts()
            postDao.clearAndInsertAll(remote.map { it.toEntity() })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val cached = postDao.getAllPosts().first().map { it.toDomain() }
            emit(Resource.Error(e.toErrorMessage(), cached.ifEmpty { null }))
            return@flow
        }

        emitAll(postDao.getAllPosts().map { entities ->
            Resource.Success(entities.map { it.toDomain() }) as Resource<List<Post>>
        })
    }

    override fun getPostById(id: Int): Flow<Resource<Post>> = flow {
        emit(Resource.Loading())

        try {
            val remote = apiService.getPost(id)
            postDao.insert(remote.toEntity())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val cached = postDao.getPostById(id).first()
            emit(Resource.Error(e.toErrorMessage(), cached?.toDomain()))
            return@flow
        }

        emitAll(postDao.getPostById(id).filterNotNull().map {
            Resource.Success(it.toDomain()) as Resource<Post>
        })
    }
}

private fun Throwable.toErrorMessage(): String = when (this) {
    is IOException -> "Network error: $message"
    is HttpException -> "Server error: ${code()}"
    else -> "Unexpected error: $message"
}
