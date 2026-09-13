package com.example.codebase.presentation.postlist

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.codebase.domain.model.Post
import com.example.codebase.domain.usecase.GetPostsUseCase
import com.example.codebase.domain.util.Resource
import com.example.codebase.util.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class PostListViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val samplePosts = listOf(
        Post(id = 1, userId = 1, title = "Title 1", body = "Body 1")
    )

    @Test
    fun `loadPosts updates uiState from Loading to Success`() = runTest(mainDispatcherRule.testDispatcher) {
        val useCase = mockk<GetPostsUseCase>()
        every { useCase() } returns flow {
            emit(Resource.Loading())
            delay(1)
            emit(Resource.Success(samplePosts))
        }

        val viewModel = PostListViewModel(useCase)

        // init's launchIn is only scheduled, not yet run, on StandardTestDispatcher.
        assertEquals(PostListUiState(), viewModel.uiState.value)

        runCurrent()
        assertEquals(PostListUiState(isLoading = true), viewModel.uiState.value)

        advanceUntilIdle()
        assertEquals(PostListUiState(isLoading = false, posts = samplePosts), viewModel.uiState.value)
    }

    @Test
    fun `uiStateLiveData mirrors uiState`() = runTest(mainDispatcherRule.testDispatcher) {
        val useCase = mockk<GetPostsUseCase>()
        every { useCase() } returns flowOf(Resource.Success(samplePosts))

        val viewModel = PostListViewModel(useCase)
        // asLiveData() only starts collecting once it has an active observer.
        viewModel.uiStateLiveData.observeForever {}
        advanceUntilIdle()

        assertEquals(viewModel.uiState.value, viewModel.uiStateLiveData.value)
        assertEquals(PostListUiState(isLoading = false, posts = samplePosts), viewModel.uiStateLiveData.value)
    }

    @Test
    fun `error preserves previously loaded posts`() = runTest(mainDispatcherRule.testDispatcher) {
        val useCase = mockk<GetPostsUseCase>()
        every { useCase() } returns flowOf(
            Resource.Success(samplePosts),
            Resource.Error("network down")
        )

        val viewModel = PostListViewModel(useCase)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(samplePosts, state.posts)
        assertEquals("network down", state.error)
    }

    @Test
    fun `loadPosts cancels the previous collection instead of stacking collectors`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val active = AtomicInteger(0)
            val useCase = mockk<GetPostsUseCase>()
            // Mirrors the real repository, whose flow ends in a Room Flow that never completes.
            val neverEnding = MutableStateFlow<Resource<List<Post>>>(Resource.Success(samplePosts))
            every { useCase() } returns neverEnding
                .onStart { active.incrementAndGet() }
                .onCompletion { active.decrementAndGet() }

            val viewModel = PostListViewModel(useCase)
            advanceUntilIdle()
            assertEquals(1, active.get())

            viewModel.loadPosts()
            advanceUntilIdle()
            viewModel.loadPosts()
            advanceUntilIdle()

            assertEquals(1, active.get())
        }

    @Test
    fun `loadPosts re-invokes the use case on refresh`() = runTest(mainDispatcherRule.testDispatcher) {
        val useCase = mockk<GetPostsUseCase>()
        every { useCase() } returns flowOf(Resource.Success(samplePosts))

        val viewModel = PostListViewModel(useCase)
        advanceUntilIdle()

        viewModel.loadPosts()
        advanceUntilIdle()

        verify(exactly = 2) { useCase() }
    }
}
