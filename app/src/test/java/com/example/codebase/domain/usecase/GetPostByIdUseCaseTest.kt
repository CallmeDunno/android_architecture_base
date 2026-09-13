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

private class FakePostByIdRepository(
    private val postFlow: Flow<Resource<Post>> = flowOf(),
    var lastRequestedId: Int? = null
) : PostRepository {

    override fun getPosts(): Flow<Resource<List<Post>>> = flowOf()

    override fun getPostById(id: Int): Flow<Resource<Post>> {
        lastRequestedId = id
        return postFlow
    }
}

class GetPostByIdUseCaseTest {

    private val samplePost = Post(id = 1, userId = 1, title = "Title 1", body = "Body 1")

    @Test
    fun `invoke emits exactly what repository emits, in order`() = runTest {
        val flow = flowOf(
            Resource.Loading(),
            Resource.Success(samplePost)
        )
        val repository = FakePostByIdRepository(postFlow = flow)
        val useCase = GetPostByIdUseCase(repository)

        useCase(1).test {
            assertEquals(Resource.Loading<Post>(), awaitItem())
            assertEquals(Resource.Success(samplePost), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `invoke passes id through to repository`() = runTest {
        val repository = FakePostByIdRepository()
        val useCase = GetPostByIdUseCase(repository)

        useCase(42).test { awaitComplete() }

        assertEquals(42, repository.lastRequestedId)
    }
}
