package com.example.codebase.presentation.postdetail

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.codebase.domain.model.Post
import com.example.codebase.domain.usecase.GetPostByIdUseCase
import com.example.codebase.domain.util.Resource
import com.example.codebase.util.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PostDetailViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val samplePost = Post(id = 7, userId = 1, title = "Title", body = "Body")

    @Test
    fun `loadPost with given id invokes use case with that id`() = runTest(mainDispatcherRule.testDispatcher) {
        val useCase = mockk<GetPostByIdUseCase>()
        every { useCase(7) } returns flowOf(Resource.Success(samplePost))

        val viewModel = PostDetailViewModel(useCase)
        viewModel.loadPost(7)
        advanceUntilIdle()

        verify { useCase(7) }
        assertEquals(samplePost, viewModel.uiState.value.post)
    }

    @Test
    fun `uiStateLiveData mirrors uiState after loading`() = runTest(mainDispatcherRule.testDispatcher) {
        val useCase = mockk<GetPostByIdUseCase>()
        every { useCase(7) } returns flowOf(Resource.Success(samplePost))

        val viewModel = PostDetailViewModel(useCase)
        // asLiveData() only starts collecting once it has an active observer.
        viewModel.uiStateLiveData.observeForever {}
        viewModel.loadPost(7)
        advanceUntilIdle()

        assertEquals(viewModel.uiState.value, viewModel.uiStateLiveData.value)
        assertEquals(samplePost, viewModel.uiStateLiveData.value?.post)
    }

    @Test
    fun `error preserves previously loaded post`() = runTest(mainDispatcherRule.testDispatcher) {
        val useCase = mockk<GetPostByIdUseCase>()
        every { useCase(7) } returns flowOf(
            Resource.Success(samplePost),
            Resource.Error("network down")
        )

        val viewModel = PostDetailViewModel(useCase)
        viewModel.loadPost(7)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(samplePost, state.post)
        assertEquals("network down", state.error)
    }
}
