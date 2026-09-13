package com.example.codebase.base

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Base for ViewModels that hold a single immutable UiState.
 *
 * Exposes the state both as [uiState] (StateFlow) and [uiStateLiveData] so a screen can use
 * either consumption style. Subclasses mutate it only through [setState].
 *
 * Repository flows never complete on success, so a subclass that re-invokes a use case must keep
 * its own `loadJob` and cancel it before each `launchIn(viewModelScope)`.
 */
abstract class BaseViewModel<S>(initialState: S) : ViewModel() {

    private val _uiState = MutableStateFlow(initialState)
    val uiState: StateFlow<S> = _uiState.asStateFlow()

    val uiStateLiveData: LiveData<S> = uiState.asLiveData()

    protected val currentState: S
        get() = _uiState.value

    protected fun setState(reducer: S.() -> S) {
        _uiState.update(reducer)
    }
}
