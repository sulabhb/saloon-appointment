package com.salonbooking.app.customer.salon

import androidx.lifecycle.SavedStateHandle
import com.salonbooking.app.core.network.BusinessDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SalonViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val salon = BusinessDto(slug = "glow-hair", displayName = "Glow Hair")

    private class FakeRepository(var next: SalonResult) : SalonRepository {
        val requested = mutableListOf<String>()
        override suspend fun loadSalon(code: String): SalonResult {
            requested += code
            return next
        }
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(repository: SalonRepository) =
        SalonViewModel(SavedStateHandle(mapOf("code" to "glow-hair")), repository)

    @Test
    fun `loads the salon from the route code`() = runTest(dispatcher) {
        val repository = FakeRepository(SalonResult.Found(salon))
        val vm = viewModel(repository)
        assertEquals(SalonUiState.Loading, vm.state.value)

        advanceUntilIdle()

        assertEquals(SalonUiState.Loaded(salon), vm.state.value)
        assertEquals(listOf("glow-hair"), repository.requested)
    }

    @Test
    fun `maps each failure to its own state`() = runTest(dispatcher) {
        val expected = mapOf(
            SalonResult.NotFound to SalonUiState.NotFound("glow-hair"),
            SalonResult.Unavailable to SalonUiState.Unavailable,
            SalonResult.NetworkError to SalonUiState.NetworkError,
        )
        expected.forEach { (result, state) ->
            val vm = viewModel(FakeRepository(result))
            advanceUntilIdle()
            assertEquals(state, vm.state.value)
        }
    }

    @Test
    fun `retry loads again after a network error`() = runTest(dispatcher) {
        val repository = FakeRepository(SalonResult.NetworkError)
        val vm = viewModel(repository)
        advanceUntilIdle()

        repository.next = SalonResult.Found(salon)
        vm.retry()
        advanceUntilIdle()

        assertEquals(SalonUiState.Loaded(salon), vm.state.value)
        assertEquals(2, repository.requested.size)
    }
}
