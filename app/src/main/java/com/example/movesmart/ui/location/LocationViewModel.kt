package com.example.movesmart.ui.location

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.movesmart.location.*
import com.example.movesmart.shared.GeoLocation
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LocationMode { CURRENT, MANUAL }
sealed interface LocationStatus {
    data object Idle : LocationStatus
    data object Permission : LocationStatus
    data object Loading : LocationStatus
    data class Results(val matches: List<LocationMatch>) : LocationStatus
    data class Error(val problem: LocationProblem, val message: String) : LocationStatus
}
data class LocationState(val userId: String? = null, val mode: LocationMode = LocationMode.MANUAL,
    val query: String = "", val selected: GeoLocation? = null, val source: String? = null,
    val status: LocationStatus = LocationStatus.Idle)

/** Session-only location state, separate from Member B's sample activity form. */
class LocationViewModel(private val current: CurrentLocationProvider, private val search: LocationSearch) : ViewModel() {
    private val mutableState = MutableStateFlow(LocationState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    private var version = 0L

    fun forUser(id: String?): LocationState = state.value.takeIf { id != null && it.userId == id } ?: LocationState()

    fun setUser(id: String?) {
        if (state.value.userId == id) return
        cancelPending()
        mutableState.value = LocationState(userId = id)
    }

    fun cancelPending() {
        version++
        job?.cancel()
        if (state.value.status == LocationStatus.Loading || state.value.status == LocationStatus.Permission) {
            mutableState.value = state.value.copy(status = LocationStatus.Idle)
        }
    }

    fun useManual() {
        cancelPending()
        mutableState.value = state.value.copy(mode = LocationMode.MANUAL, selected = null, source = null, status = LocationStatus.Idle)
    }

    fun editQuery(value: String) {
        cancelPending()
        mutableState.value = state.value.copy(query = value.take(100), selected = null, source = null, status = LocationStatus.Idle)
    }

    /** The token binds the permission callback to the click that requested it. */
    fun useCurrent(permissionGranted: Boolean): Long {
        cancelPending()
        mutableState.value = state.value.copy(mode = LocationMode.CURRENT, selected = null, source = null,
            status = LocationStatus.Permission)
        val token = version
        if (permissionGranted) permissionResult(token, true)
        return token
    }

    fun permissionResult(token: Long, granted: Boolean) {
        if (token != version || state.value.mode != LocationMode.CURRENT || state.value.status != LocationStatus.Permission) return
        if (!granted) {
            mutableState.value = state.value.copy(status = LocationStatus.Error(LocationProblem.PERMISSION_DENIED,
                "Location permission denied. Retry to grant access, enable it in app settings, or enter a suburb/postcode."))
            return
        }
        launchRequest(25_000) {
            val location = current.locate()
            if (!location.hasValidCoordinates()) throw LocationFailure(LocationProblem.INVALID_RESULT, "The device returned invalid coordinates. Please retry.")
            state.value.copy(selected = location, source = "Device location (fix no older than 2 minutes)", status = LocationStatus.Idle)
        }
    }

    fun submitSearch() {
        if (state.value.mode != LocationMode.MANUAL || state.value.status == LocationStatus.Loading) return
        val input = state.value.query
        cancelPending()
        mutableState.value = state.value.copy(selected = null, source = null)
        launchRequest(22_000) { state.value.copy(status = LocationStatus.Results(search.search(input))) }
    }

    fun select(match: LocationMatch) {
        val results = (state.value.status as? LocationStatus.Results)?.matches ?: return
        if (match !in results || !match.location.hasValidCoordinates()) return
        cancelPending()
        mutableState.value = state.value.copy(selected = match.location, source = match.source, status = LocationStatus.Idle)
    }

    private fun launchRequest(timeoutMillis: Long, action: suspend () -> LocationState) {
        val token = version
        mutableState.value = state.value.copy(status = LocationStatus.Loading)
        job = viewModelScope.launch {
            try {
                val result = withTimeout(timeoutMillis) { action() }
                ensureActive()
                if (version == token) mutableState.value = result
            } catch (_: TimeoutCancellationException) {
                if (version == token) mutableState.value = state.value.copy(status = LocationStatus.Error(LocationProblem.TIMEOUT,
                    "Location request timed out. Please retry or use another location input method."))
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                if (version == token) mutableState.value = state.value.copy(status = LocationStatus.Error(
                    (error as? LocationFailure)?.problem ?: LocationProblem.SERVICE, error.message ?: "Location retrieval failed. Please retry."))
            }
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(LocationViewModel::class.java))
                @Suppress("UNCHECKED_CAST")
                return LocationViewModel(AndroidCurrentLocationProvider(context), AustralianLocationSearch.create()) as T
            }
        }
    }
}
