package com.salonbooking.app.customer.salon

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salonbooking.app.core.network.BusinessDto
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SalonUiState {
    data object Loading : SalonUiState
    data class Loaded(val salon: BusinessDto) : SalonUiState
    data class NotFound(val code: String) : SalonUiState
    data object Unavailable : SalonUiState
    data object NetworkError : SalonUiState
}

@HiltViewModel
class SalonViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: SalonRepository,
) : ViewModel() {

    /** Key matches [SalonRoute.code]. */
    private val code: String = checkNotNull(savedStateHandle["code"]) { "SalonRoute.code is missing" }

    private val _state = MutableStateFlow<SalonUiState>(SalonUiState.Loading)
    val state: StateFlow<SalonUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        _state.value = SalonUiState.Loading
        viewModelScope.launch {
            _state.value = when (val result = repository.loadSalon(code)) {
                is SalonResult.Found -> SalonUiState.Loaded(result.salon)
                SalonResult.NotFound -> SalonUiState.NotFound(code)
                SalonResult.Unavailable -> SalonUiState.Unavailable
                SalonResult.NetworkError -> SalonUiState.NetworkError
            }
        }
    }
}
