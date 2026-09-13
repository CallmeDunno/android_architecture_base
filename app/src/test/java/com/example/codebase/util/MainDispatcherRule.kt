package com.example.codebase.util

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Exposes [testDispatcher] so it can also be passed to `runTest(...)`, putting `viewModelScope`
 * (backed by `Dispatchers.Main`) on the same virtual-time scheduler as the test body. That lets
 * tests deterministically step through intermediate StateFlow emissions with `runCurrent()` /
 * `advanceUntilIdle()` instead of racing a real dispatcher.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
