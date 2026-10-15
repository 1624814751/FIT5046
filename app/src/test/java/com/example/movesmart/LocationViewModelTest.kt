package com.example.movesmart

import androidx.lifecycle.ViewModelStore
import com.example.movesmart.location.*
import com.example.movesmart.shared.GeoLocation
import com.example.movesmart.ui.location.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocationViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val fix = GeoLocation("Current location", -37.9, 145.1)

    @Test fun coordinateAndFixValidationRejectsInvalidStaleFutureAndInaccurateValues() {
        assertTrue(validFix(fix, 1000, 30f))
        assertTrue(validFix(fix, 120_000, 5000f))
        listOf(fix.copy(latitude = Double.NaN), fix.copy(latitude = 91.0),
            fix.copy(longitude = Double.POSITIVE_INFINITY), fix.copy(longitude = -181.0), fix.copy(label = "")).forEach {
            assertFalse(it.hasValidCoordinates())
        }
        assertFalse(validFix(fix, 120_001, 30f))
        assertFalse(validFix(fix, -1, 30f))
        assertFalse(validFix(fix, 0, Float.NaN))
        assertFalse(validFix(fix, 0, 10001f))
    }

    @Test fun permissionIsRequestedOnlyByClickAndDeniedPermissionNeverInvokesProvider() = runTest {
        var calls = 0
        val model = LocationViewModel({ calls++; fix }, { emptyList() })
        model.setUser("a")
        runCurrent()
        assertEquals(0, calls)
        val token = model.useCurrent(false)
        assertEquals(LocationStatus.Permission, model.state.value.status)
        model.permissionResult(token, false)
        assertEquals(LocationProblem.PERMISSION_DENIED, (model.state.value.status as LocationStatus.Error).problem)
        assertEquals(0, calls)
        val retry = model.useCurrent(false)
        model.permissionResult(retry, true)
        runCurrent()
        assertEquals(fix, model.state.value.selected)
        assertEquals(1, calls)
    }

    @Test fun serviceDisabledUnavailableInvalidAndNetworkFailuresRemainDistinct() = runTest {
        for (problem in listOf(LocationProblem.SERVICES_DISABLED, LocationProblem.UNAVAILABLE, LocationProblem.NETWORK)) {
            val model = LocationViewModel({ throw LocationFailure(problem, problem.name) }, { emptyList() })
            model.useCurrent(true)
            runCurrent()
            assertEquals(problem, (model.state.value.status as LocationStatus.Error).problem)
        }
        val model = LocationViewModel({ fix.copy(latitude = 999.0) }, { emptyList() })
        model.useCurrent(true)
        runCurrent()
        assertEquals(LocationProblem.INVALID_RESULT, (model.state.value.status as LocationStatus.Error).problem)
    }

    @Test fun timeoutCancelsProviderAndAllowsRetry() = runTest {
        var cancelled = false
        var wait = true
        val model = LocationViewModel({
            if (wait) try { awaitCancellation() } finally { cancelled = true }
            fix
        }, { emptyList() })
        model.useCurrent(true)
        advanceTimeBy(25_001)
        runCurrent()
        assertTrue(cancelled)
        assertEquals(LocationProblem.TIMEOUT, (model.state.value.status as LocationStatus.Error).problem)
        wait = false
        model.useCurrent(true)
        runCurrent()
        assertEquals(fix, model.state.value.selected)
    }

    @Test fun switchingToManualRejectsLatePermissionAndUncooperativeGpsResult() = runTest {
        val gate = CompletableDeferred<GeoLocation>()
        var calls = 0
        val model = LocationViewModel({ calls++; withContext(NonCancellable) { gate.await() } }, { emptyList() })
        val token = model.useCurrent(false)
        model.useManual()
        model.permissionResult(token, true)
        runCurrent()
        assertEquals(0, calls)
        model.useCurrent(true)
        runCurrent()
        model.useManual()
        gate.complete(fix)
        runCurrent()
        assertEquals(LocationMode.MANUAL, model.state.value.mode)
        assertNull(model.state.value.selected)
    }

    @Test fun searchRequiresExplicitSelectionAndRejectsResultsAfterEditingOrSwitchingAccount() = runTest {
        val matches = listOf(LocationMatch(fix.copy(label = "Clayton, VIC 3168"), "test"),
            LocationMatch(fix.copy(label = "Notting Hill, VIC 3168"), "test"))
        val gate = CompletableDeferred<List<LocationMatch>>()
        val model = LocationViewModel({ fix }, { if (it == "slow") withContext(NonCancellable) { gate.await() } else matches })
        model.setUser("a")
        model.editQuery("3168")
        model.submitSearch()
        runCurrent()
        assertNull(model.state.value.selected)
        assertEquals(2, (model.state.value.status as LocationStatus.Results).matches.size)
        model.select(matches[1])
        assertEquals(matches[1].location, model.state.value.selected)
        assertNull(model.forUser("b").selected)
        model.editQuery("slow")
        model.submitSearch()
        runCurrent()
        model.setUser("b")
        gate.complete(matches)
        runCurrent()
        assertEquals(LocationState(userId = "b"), model.state.value)
        model.select(matches[0])
        assertNull(model.state.value.selected)
    }

    @Test fun leavingWorkflowAndNewQueryRejectLateManualResults() = runTest {
        val gate = CompletableDeferred<List<LocationMatch>>()
        val model = LocationViewModel({ fix }, { withContext(NonCancellable) { gate.await() } })
        model.editQuery("Clayton")
        model.submitSearch()
        runCurrent()
        model.cancelPending()
        model.editQuery("Chadstone")
        gate.complete(listOf(LocationMatch(fix, "test")))
        runCurrent()
        assertEquals("Chadstone", model.state.value.query)
        assertEquals(LocationStatus.Idle, model.state.value.status)
        ViewModelStore().apply { put("location", model); clear() }
    }

    @Test fun emptyResultsNetworkErrorsAndSearchTimeoutAreVisible() = runTest {
        val model = LocationViewModel({ fix }, {
            when (it) {
                "offline" -> throw LocationFailure(LocationProblem.NETWORK, "Offline")
                "timeout" -> awaitCancellation()
                else -> emptyList()
            }
        })
        model.editQuery("nothing")
        model.submitSearch()
        runCurrent()
        assertEquals(LocationStatus.Results(emptyList()), model.state.value.status)
        model.editQuery("offline")
        model.submitSearch()
        runCurrent()
        assertEquals(LocationProblem.NETWORK, (model.state.value.status as LocationStatus.Error).problem)
        model.editQuery("timeout")
        model.submitSearch()
        advanceTimeBy(22_001)
        runCurrent()
        assertEquals(LocationProblem.TIMEOUT, (model.state.value.status as LocationStatus.Error).problem)
    }
}
