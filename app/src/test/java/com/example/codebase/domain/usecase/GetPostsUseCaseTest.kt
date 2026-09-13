package com.example.codebase.domain.usecase

import app.cash.turbine.test
import com.example.codebase.domain.model.Post
import com.example.codebase.domain.repository.PostRepository
import com.example.codebase.domain.util.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private class FakePostsRepository(
    private val postsFlow: Flow<Resource<List<Post>>> = flowOf()
) : PostRepository {

    override fun getPosts(): Flow<Resource<List<Post>>> = postsFlow

    override fun getPostById(id: Int): Flow<Resource<Post>> = flowOf()
}

class GetPostsUseCaseTest {

    private val samplePosts = listOf(
        Post(id = 1, userId = 1, title = "Title 1", body = "Body 1"),
        Post(id = 2, userId = 1, title = "Title 2", body = "Body 2")
    )

    @Test
    fun `invoke emits exactly what repository emits, in order`() = runTest {
        val flow = flowOf(
            Resource.Loading(),
            Resource.Success(samplePosts)
        )
        val repository = FakePostsRepository(postsFlow = flow)
        val useCase = GetPostsUseCase(repository)

        useCase().test {
            assertEquals(Resource.Loading<List<Post>>(), awaitItem())
            assertEquals(Resource.Success(samplePosts), awaitItem())
            awaitComplete()
        }
    }
}
