package com.example.codebase.data.repository

import app.cash.turbine.test
import com.example.codebase.data.local.PostDao
import com.example.codebase.data.local.PostEntity
import com.example.codebase.data.mapper.toDomain
import com.example.codebase.data.mapper.toEntity
import com.example.codebase.data.remote.ApiService
import com.example.codebase.data.remote.dto.PostDto
import com.example.codebase.domain.util.Resource
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** In-memory fake standing in for Room's reactive [PostDao], so tests don't need a real database. */
private class FakePostDao : PostDao {

    private val state = MutableStateFlow<List<PostEntity>>(emptyList())

    fun setInitial(posts: List<PostEntity>) {
        state.value = posts
    }

    override fun getAllPosts(): Flow<List<PostEntity>> = state

    override fun getPostById(id: Int): Flow<PostEntity?> = state.map { list -> list.find { it.id == id } }

    override suspend fun insertAll(posts: List<PostEntity>) {
        val merged = state.value.associateBy { it.id }.toMutableMap()
        posts.forEach { merged[it.id] = it }
        state.value = merged.values.toList()
    }

    override suspend fun insert(post: PostEntity) = insertAll(listOf(post))

    override suspend fun clearAll() {
        state.value = emptyList()
    }
}

class PostRepositoryImplTest {

    private val cachedEntity = PostEntity(id = 1, userId = 1, title = "Cached title", body = "Cached body")
    private val remoteDto = PostDto(id = 1, userId = 1, title = "Remote title", body = "Remote body")

    @Test
    fun `getPosts prefers network data over existing cache`() = runTest {
        val dao = FakePostDao().apply { setInitial(listOf(cachedEntity)) }
        val api = mockk<ApiService>()
        coEvery { api.getPosts() } returns listOf(remoteDto)
        val repository = PostRepositoryImpl(api, dao)

        repository.getPosts().test {
            assertEquals(Resource.Loading<Any>(), awaitItem())
            // No cached Success is emitted before the network result.
            val success = awaitItem() as Resource.Success
            assertEquals(listOf(remoteDto.toEntity().toDomain()), success.data)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getPosts falls back to cached data when network fails`() = runTest {
        val dao = FakePostDao().apply { setInitial(listOf(cachedEntity)) }
        val api = mockk<ApiService>()
        coEvery { api.getPosts() } throws IOException("no connection")
        val repository = PostRepositoryImpl(api, dao)

        repository.getPosts().test {
            assertEquals(Resource.Loading<Any>(), awaitItem())
            val error = awaitItem() as Resource.Error
            assertTrue(error.message.startsWith("Network error"))
            assertEquals(listOf(cachedEntity.toDomain()), error.data)
            awaitComplete()
        }
    }

    @Test
    fun `getPostById falls back to cached post when network fails`() = runTest {
        val dao = FakePostDao().apply { setInitial(listOf(cachedEntity)) }
        val api = mockk<ApiService>()
        coEvery { api.getPost(1) } throws IOException("no connection")
        val repository = PostRepositoryImpl(api, dao)

        repository.getPostById(1).test {
            assertEquals(Resource.Loading<Any>(), awaitItem())
            val error = awaitItem() as Resource.Error
            assertEquals(cachedEntity.toDomain(), error.data)
            awaitComplete()
        }
    }

    @Test
    fun `getPosts maps an unexpected exception to Error instead of letting it escape`() = runTest {
        val dao = FakePostDao()
        val api = mockk<ApiService>()
        coEvery { api.getPosts() } throws SerializationException("malformed json")
        val repository = PostRepositoryImpl(api, dao)

        repository.getPosts().test {
            assertEquals(Resource.Loading<Any>(), awaitItem())
            val error = awaitItem() as Resource.Error
            assertTrue(error.message.startsWith("Unexpected error"))
            awaitComplete()
        }
    }

    @Test
    fun `getPosts lets CancellationException propagate rather than swallowing it`() = runTest {
        val dao = FakePostDao()
        val api = mockk<ApiService>()
        coEvery { api.getPosts() } throws CancellationException("cancelled")
        val repository = PostRepositoryImpl(api, dao)

        assertThrows(CancellationException::class.java) {
            runBlocking { repository.getPosts().toList() }
        }
    }

    @Test
    fun `getPosts network error with empty cache emits Error with null data`() = runTest {
        val dao = FakePostDao()
        val api = mockk<ApiService>()
        coEvery { api.getPosts() } throws IOException("no connection")
        val repository = PostRepositoryImpl(api, dao)

        repository.getPosts().test {
            assertEquals(Resource.Loading<Any>(), awaitItem())
            val error = awaitItem() as Resource.Error
            assertTrue(error.data == null)
            awaitComplete()
        }
    }
}
