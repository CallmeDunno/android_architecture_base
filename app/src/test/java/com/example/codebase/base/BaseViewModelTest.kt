package com.example.codebase.base

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.codebase.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private data class CounterUiState(val count: Int = 0, val label: String = "")

private class CounterTestViewModel : BaseViewModel<CounterUiState>(CounterUiState()) {
    fun increment() = setState { copy(count = count + 1) }
    fun rename(label: String) = setState { copy(label = label) }
    fun countFromCurrentState(): Int = currentState.count
}

@OptIn(ExperimentalCoroutinesApi::class)
class BaseViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `uiState starts with the initial state`() {
        assertEquals(CounterUiState(), CounterTestViewModel().uiState.value)
    }

    @Test
    fun `setState applies reducers on top of the current state`() {
        val viewModel = CounterTestViewModel()

        viewModel.increment()
        viewModel.increment()
        viewModel.rename("two")

        assertEquals(CounterUiState(count = 2, label = "two"), viewModel.uiState.value)
        assertEquals(2, viewModel.countFromCurrentState())
    }

    @Test
    fun `uiStateLiveData mirrors uiState`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = CounterTestViewModel()
        viewModel.uiStateLiveData.observeForever {}

        viewModel.increment()
        advanceUntilIdle()

        assertEquals(viewModel.uiState.value, viewModel.uiStateLiveData.value)
        assertEquals(1, viewModel.uiStateLiveData.value?.count)
    }
}
