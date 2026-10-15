package com.example.movesmart

import android.Manifest
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.movesmart.data.WeatherProvider
import com.example.movesmart.location.*
import com.example.movesmart.recommendation.SimpleRecommendationEngine
import com.example.movesmart.shared.*
import com.example.movesmart.ui.context.*
import com.example.movesmart.ui.location.*
import com.example.movesmart.ui.recommendation.RecommendationViewModel
import com.example.movesmart.ui.theme.MoveSmartTheme
import com.example.movesmart.ui.weather.WeatherViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/** Default tests use fakes. Opt-in live tests use real geocoding/weather and simulated device GPS. */
@RunWith(AndroidJUnit4::class)
class LocationFlowDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val owner = ViewModelStore()
    private val auth = LocationFlowAuth()
    private val suburb = GeoLocation("Clayton, VIC", -37.91667, 145.11667)
    private val postcodeMatches = listOf(LocationMatch(suburb.copy(label = "Clayton, VIC 3168"), "test"),
        LocationMatch(GeoLocation("Notting Hill, VIC 3168", -37.9, 145.1167), "test"))
    private lateinit var location: LocationViewModel
    private lateinit var weather: WeatherViewModel
    private lateinit var recommendations: RecommendationViewModel
    private lateinit var form: WorkoutViewModel
    private val requests = mutableListOf<WeatherQuery>()
    private var offline = false
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun start(search: LocationSearch = LocationSearch {
        if (it == "3168") postcodeMatches else if (it == "Nowhere") emptyList() else listOf(LocationMatch(suburb, "test"))
    }, current: CurrentLocationProvider = CurrentLocationProvider { suburb.copy(label = "Current location") }, liveWeather: Boolean = false) {
        compose.runOnIdle {
            location = LocationViewModel(current, search).also { it.setUser("a") }
            form = WorkoutViewModel(SavedStateHandle()).apply { updateIntensity("Easy"); updatePreference("Indoor"); updateDuration("15 min") }
            weather = WeatherViewModel(if (liveWeather) WeatherProvider.getRepository(context) else object : WeatherRepository {
                override fun observe(query: WeatherQuery) = emptyFlow<WeatherContext?>()
                override suspend fun refresh(query: WeatherQuery): Result<WeatherContext> {
                    requests.add(query)
                    return if (offline) Result.failure(IllegalStateException("Weather offline")) else
                        Result.success(WeatherContext(query, 18.0, 20, 10.0, Instant.now(), DataSource.LIVE, false))
                }
            })
            recommendations = RecommendationViewModel(auth, object : PreferencesRepository {
                override fun observe(userId: String) = flowOf(UserPreferences(userId, "Stay Active", Intensity.LOW, 150, listOf("Yoga"), Environment.INDOOR))
                override suspend fun save(preferences: UserPreferences) = Result.success(Unit)
            }, SimpleRecommendationEngine(), { object : RecommendationStore {
                override fun observe(id: String) = flowOf<Recommendation?>(null)
                override suspend fun save(recommendation: Recommendation) = Result.success(Unit)
            } })
            owner.put("location", location); owner.put("weather", weather); owner.put("recommendation", recommendations)
        }
        compose.setContent {
            val account by auth.currentUser.collectAsStateWithLifecycle()
            val selected by location.state.collectAsStateWithLifecycle()
            val uid = account?.userId.orEmpty()
            val usable = selected.let { location.forUser(uid).selected }
            LaunchedEffect(uid) { location.setUser(uid) }
            LaunchedEffect(usable) { usable?.let { form.updateLocation(it.label) } }
            MoveSmartTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    LocationInput(uid, location)
                    MemberAContextRoute(uid, memberAPlan(form, usable), weather, recommendations)
                }
            }
        }
    }

    @After fun clear() { compose.runOnIdle { owner.clear() } }
    private fun tap(text: String) = compose.onNodeWithText(text).performScrollTo().performClick()
    private fun search(query: String) {
        compose.onNodeWithText("Suburb or postcode").performScrollTo().performTextReplacement(query)
        tap("Search locations")
    }
    private fun awaitResults() = compose.waitUntil(30_000) { location.state.value.status is LocationStatus.Results || location.state.value.status is LocationStatus.Error }
    private fun awaitWeather() = compose.waitUntil(40_000) { weather.state.value is UiState.Success || weather.state.value is UiState.Error }
    private fun generateAndSelect() {
        tap("Generate recommendation")
        compose.waitUntil(10_000) { recommendations.state.value is UiState.Success }
        tap("Select recommendation")
        compose.onNodeWithText("Selected for this session").assertExists()
    }

    @Test fun suburbThenAmbiguousPostcodeRefreshesWeatherAndRecommendationsWithoutResettingPlan() {
        start()
        search("Clayton VIC")
        tap("Clayton, VIC")
        generateAndSelect()
        tap("Enter Suburb or Postcode")
        search("3168")
        compose.onNodeWithText("Select the correct location (2 results).").assertExists()
        compose.runOnIdle { assertNull(location.state.value.selected); assertNull(recommendations.selection.value) }
        tap("Notting Hill, VIC 3168")
        generateAndSelect()
        compose.runOnIdle {
            assertEquals(postcodeMatches[1].location, requests.last().location)
            val result = (recommendations.state.value as UiState.Success).data!!.recommendation
            assertEquals(postcodeMatches[1].location, result.location)
            assertEquals(15, result.minutes)
            assertEquals("Yoga", result.activityName)
            assertEquals(Intensity.LOW, result.intensity)
            assertEquals(result.id, recommendations.selection.value!!.recommendationId)
            assertEquals("a", recommendations.selection.value!!.userId)
            assertEquals("15 min", form.selectedDuration)
            assertEquals("Indoor", form.selectedPreference)
            auth.currentUser.value = UserAccount("b", "B", "b@example.com")
        }
        compose.onNodeWithText("Selected for this session").assertDoesNotExist()
        compose.runOnIdle { assertNull(location.state.value.selected); assertNull(recommendations.selection.value) }
    }

    @Test fun noResultsAndWeatherFailureAreExplicitAndNeverDisplaySampleWeather() {
        start()
        search("Nowhere")
        compose.onNodeWithText("No matching Australian location found. Try a suburb and state or a different postcode.").assertExists()
        offline = true
        search("Clayton")
        tap("Clayton, VIC")
        compose.onNodeWithText("Weather offline").assertExists()
        compose.onNodeWithText("Weather unavailable: recommendations can use your preferences and time without weather. Outdoor conditions are not verified.").assertExists()
        compose.onNodeWithText("Clear · 18°C").assertDoesNotExist()
        generateAndSelect()
        compose.runOnIdle {
            assertTrue((recommendations.state.value as UiState.Success).data!!.recommendation.reasons.any { it.contains("weather", true) })
        }
    }

    @Test fun switchingToManualCancelsPendingGpsInUi() {
        start(current = CurrentLocationProvider { CompletableDeferred<GeoLocation>().await() })
        compose.runOnIdle { location.useCurrent(true) }
        compose.onNodeWithText("Finding current location…").assertExists()
        tap("Enter Suburb or Postcode")
        search("3168")
        tap("Clayton, VIC 3168")
        generateAndSelect()
    }

    @Test fun liveSuburbAndPostcodeToWeatherAndRecommendation() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("locationLiveTest") == "true")
        start(search = AustralianLocationSearch.create(), liveWeather = true)
        search("Clayton VIC")
        awaitResults()
        tap("Clayton, VIC")
        awaitWeather()
        compose.runOnIdle { assertEquals(DataSource.LIVE, (weather.state.value as UiState.Success).data.source) }
        generateAndSelect()
        tap("Enter Suburb or Postcode")
        search("3168")
        awaitResults()
        compose.runOnIdle { assertTrue((location.state.value.status as LocationStatus.Results).matches.size >= 2) }
        tap("Notting Hill, VIC 3168")
        awaitWeather()
        compose.runOnIdle { assertEquals(DataSource.LIVE, (weather.state.value as UiState.Success).data.source) }
        generateAndSelect()
    }

    @Test fun liveSimulatedDeviceLocationToWeatherAndRecommendation() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("locationGpsTest") == "true")
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.grantRuntimePermission(context.packageName, Manifest.permission.ACCESS_COARSE_LOCATION)
        automation.grantRuntimePermission(context.packageName, Manifest.permission.ACCESS_FINE_LOCATION)
        start(current = AndroidCurrentLocationProvider(context), liveWeather = true)
        tap("Use Current Location")
        compose.waitUntil(30_000) { location.state.value.selected != null || location.state.value.status is LocationStatus.Error }
        compose.runOnIdle { assertNotNull(location.state.value.toString(), location.state.value.selected) }
        awaitWeather()
        compose.runOnIdle { assertEquals(DataSource.LIVE, (weather.state.value as UiState.Success).data.source) }
        generateAndSelect()
    }
}

private class LocationFlowAuth : AuthRepository {
    override val currentUser = MutableStateFlow<UserAccount?>(UserAccount("a", "A", "a@example.com"))
    override suspend fun signIn(email: String, password: String) = Result.success(requireNotNull(currentUser.value))
    override suspend fun signUp(displayName: String, email: String, password: String) = signIn(email, password)
    override suspend fun signInWithGoogle(idToken: String) = signIn("", "")
    override suspend fun signOut(): Result<Unit> { currentUser.value = null; return Result.success(Unit) }
}
