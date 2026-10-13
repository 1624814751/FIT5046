package com.example.movesmart

import com.example.movesmart.shared.*
import com.example.movesmart.ui.planning.PlanningViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class PlanningViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val location = GeoLocation("Melbourne", -37.8136, 144.9631)
    private val plan = PlanningInput(LocalDate.of(2026, 10, 14), 30, Intensity.MEDIUM, Environment.ANY, location)
    private val now = Instant.parse("2026-10-14T00:00:00Z")
    private val weather = WeatherContext(WeatherQuery(location, plan.date), 20.0, 10, 5.0, now, DataSource.LIVE, false)
    private val preferences = UserPreferences("a", "Stay active", Intensity.MEDIUM, 150, emptyList(), Environment.ANY)
    private class Store : RecommendationStore {
        val rows = mutableListOf<Recommendation>()
        var fail = false
        var gate: CompletableDeferred<Unit>? = null
        override fun observe(id: String) = flowOf(rows.find { it.id == id })
        override suspend fun save(recommendation: Recommendation): Result<Unit> {
            gate?.await()
            if (fail) return Result.failure(IllegalStateException("Storage unavailable"))
            rows.add(recommendation)
            return Result.success(Unit)
        }
    }
    @Test fun waitsForPersistenceBeforeOfferingNavigationAndIgnoresDoubleTap() = runTest(main.dispatcher) {
        val store = Store().apply { gate = CompletableDeferred() }
        val vm = PlanningViewModel("a", store, now = { now })
        vm.create(preferences, plan, weather); runCurrent()
        vm.create(preferences, plan, weather); runCurrent()
        assertEquals(UiState.Loading, vm.state.value)
        assertTrue(store.rows.isEmpty())
        store.gate!!.complete(Unit); advanceUntilIdle()
        assertEquals(1, store.rows.size)
        assertEquals(store.rows.single().id, (vm.state.value as UiState.Success).data)
    }
    @Test fun storageFailureDoesNotProduceNavigation() = runTest(main.dispatcher) {
        val store = Store().apply { fail = true }
        val vm = PlanningViewModel("a", store, now = { now })
        vm.create(preferences, plan, weather); advanceUntilIdle()
        assertTrue(vm.state.value is UiState.Error)
        assertTrue(store.rows.isEmpty())
    }
    @Test fun rejectsDifferentUserAndMismatchedForecast() = runTest(main.dispatcher) {
        val store = Store(); val vm = PlanningViewModel("a", store, now = { now })
        vm.create(preferences.copy(userId = "b"), plan, weather); advanceUntilIdle()
        assertTrue(vm.state.value is UiState.Error)
        vm.create(preferences, plan.copy(date = plan.date.plusDays(1)), weather); advanceUntilIdle()
        assertTrue(vm.state.value is UiState.Error)
        assertTrue(store.rows.isEmpty())
    }
    @Test fun rejectsDemoWeatherAndReportsNoSuitableActivity() = runTest(main.dispatcher) {
        val store = Store(); val vm = PlanningViewModel("a", store, now = { now })
        vm.create(preferences, plan, weather.copy(source = DataSource.DEMO)); advanceUntilIdle()
        assertTrue(vm.state.value is UiState.Error)
        vm.create(preferences.copy(activityNames = listOf("Nonexistent activity")), plan, weather); advanceUntilIdle()
        assertTrue(vm.state.value is UiState.Error)
        assertTrue(store.rows.isEmpty())
    }
    @Test fun identicalPlansHaveSeparateSavedIds() = runTest(main.dispatcher) {
        val store = Store(); val vm = PlanningViewModel("a", store, now = { now })
        repeat(2) { vm.create(preferences, plan, weather); advanceUntilIdle(); vm.consumeNavigation() }
        assertEquals(2, store.rows.size)
        assertNotEquals(store.rows[0].id, store.rows[1].id)
    }
    @Test fun leavingAccountCancelsPendingSaveWithoutNavigation() = runTest(main.dispatcher) {
        val store = Store().apply { gate = CompletableDeferred() }
        val vm = PlanningViewModel("a", store, now = { now })
        vm.create(preferences, plan, weather); runCurrent(); vm.cancelPending()
        store.gate!!.complete(Unit); advanceUntilIdle()
        assertEquals(UiState.Idle, vm.state.value)
        assertTrue(store.rows.isEmpty())
    }
}
