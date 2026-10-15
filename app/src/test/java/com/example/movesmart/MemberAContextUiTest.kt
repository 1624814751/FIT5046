package com.example.movesmart

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.movesmart.recommendation.SimpleRecommendationEngine
import com.example.movesmart.shared.*
import com.example.movesmart.ui.context.*
import com.example.movesmart.ui.recommendation.RecommendationViewModel
import com.example.movesmart.ui.theme.MoveSmartTheme
import com.example.movesmart.ui.weather.WeatherViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Real ViewModels/engine with controlled repositories; no Firebase or network claims. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MemberAContextUiTest {
    @get:Rule val compose = createComposeRule()
    private val owner = ViewModelStore()
    private val auth = ContextUiAuth()
    private val preferences = MutableStateFlow<UserPreferences?>(prefs("a"))
    private val plan = PlanningInput(LocalDate.of(2026, 10, 15), 30, Intensity.LOW,
        Environment.INDOOR, GeoLocation("Test location", -37.8136, 144.9631))
    private val forecast = WeatherContext(WeatherQuery(plan.location, plan.date), 17.5,
        null, null, Instant.parse("2026-10-15T00:00:00Z"), DataSource.CACHE, true)
    private val cache = MutableStateFlow<WeatherContext?>(null)
    private var weatherError = false
    private var weatherGate: CompletableDeferred<Unit>? = null
    private var refreshCalls = 0
    private lateinit var recommendations: RecommendationViewModel
    private lateinit var weather: WeatherViewModel

    private fun start(input: PlanningInput? = plan) {
        compose.runOnIdle {
            weather = WeatherViewModel(object : WeatherRepository {
                override fun observe(query: WeatherQuery) = cache
                override suspend fun refresh(query: WeatherQuery): Result<WeatherContext> {
                    refreshCalls++
                    weatherGate?.await()
                    return if (weatherError) Result.failure(IllegalStateException("Weather offline"))
                    else Result.success(forecast)
                }
            })
            val preferenceRepository = object : PreferencesRepository {
                override fun observe(userId: String) = preferences
                override suspend fun save(preferences: UserPreferences) = Result.success(Unit)
            }
            recommendations = RecommendationViewModel(auth, preferenceRepository,
                SimpleRecommendationEngine(), { object : RecommendationStore {
                    override fun observe(id: String) = flowOf<Recommendation?>(null)
                    override suspend fun save(recommendation: Recommendation) = Result.success(Unit)
                } })
            owner.put("weather", weather)
            owner.put("recommendations", recommendations)
        }
        compose.setContent {
            val account by auth.currentUser.collectAsStateWithLifecycle()
            MoveSmartTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    MemberAContextRoute(account?.userId.orEmpty(), input, weather, recommendations)
                }
            }
        }
        compose.waitForIdle()
    }

    @After fun clear() { compose.runOnIdle { owner.clear() } }
    private fun tap(text: String) = compose.onNodeWithText(text).performScrollTo().performClick()

    @Test fun missingCoordinatesMakeNoRequestAndDoNotShowSampleWeather() {
        start(null)
        compose.onNodeWithText("Weather unavailable: this location has no verified coordinates.").assertExists()
        compose.onNodeWithText("Generate recommendation").assertIsNotEnabled()
        compose.onNodeWithText("Clear · 18°C").assertDoesNotExist()
        assertEquals(0, refreshCalls)
    }

    @Test fun cachedWeatherShowsProvenanceAgeAndMissingFieldsAndObservesWorkerUpdates() {
        start()
        compose.onNodeWithText("Saved weather").assertExists()
        compose.onNodeWithText("Old weather — refresh before going outside.").assertExists()
        compose.onNodeWithText("Rain: Unavailable · Wind: Unavailable").assertExists()
        compose.onNodeWithText("Noon forecast · 2026-10-15").assertExists()
        compose.runOnIdle { cache.value = forecast.copy(temperatureC = 19.0, fetchedAt = forecast.fetchedAt.plusSeconds(60)) }
        compose.onNodeWithText("19.0°C · Test location").assertExists()
    }

    @Test fun weatherLoadingAndRetryAreDrivenByViewModel() {
        weatherGate = CompletableDeferred()
        weatherError = true
        start()
        compose.onNodeWithText("Loading weather…").assertExists()
        compose.runOnIdle { weatherGate!!.complete(Unit) }
        compose.waitForIdle()
        compose.onNodeWithText("Weather offline").assertExists()
        compose.runOnIdle { weatherError = false }
        tap("Retry weather")
        compose.onNodeWithText("Saved weather").assertExists()
        assertEquals(2, refreshCalls)
    }

    @Test fun engineResultSelectionAndAccountChangeReachTheUi() {
        start()
        tap("Generate recommendation")
        compose.waitForIdle()
        val activity = compose.runOnIdle { (recommendations.state.value as UiState.Success).data!!.recommendation }
        compose.onNodeWithText(activity.activityName).assertExists()
        compose.onNodeWithText("${activity.minutes} min · low intensity").assertExists()
        tap("Select recommendation")
        compose.onNodeWithText("Selected for this session").assertExists()
        compose.runOnIdle {
            assertEquals(activity.id, recommendations.selectionForUser("a")!!.recommendationId)
            auth.currentUser.value = UserAccount("b", "B", "b@example.com")
            preferences.value = prefs("b")
        }
        compose.onNodeWithText("Selected for this session").assertDoesNotExist()
        compose.onNodeWithText(activity.activityName).assertDoesNotExist()
        compose.runOnIdle { assertNull(recommendations.selection.value) }
    }

    @Test fun missingPreferencesAndMissingSavedRecommendationAreShown() {
        preferences.value = null
        start()
        compose.onNodeWithText("Save your exercise preferences before requesting a recommendation.").assertExists()
        compose.runOnIdle { preferences.value = prefs("a") }
        compose.waitForIdle()
        compose.runOnIdle { recommendations.loadRecommendation("missing") }
        compose.onNodeWithText("No saved recommendation was found.").assertExists()
        compose.onNodeWithText("Select recommendation").assertDoesNotExist()
    }

    @Test fun planningAdapterRejectsLabelsAndInvalidCoordinatesAndPreservesUtcDate() {
        compose.runOnIdle {
            val form = WorkoutViewModel(SavedStateHandle())
            listOf("Chadstone", "Home", "Campus").forEach {
                form.updateLocation(it)
                assertNull(memberAPlan(form, null))
            }
            form.updateLocation(plan.location.label)
            form.updateDate(plan.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            form.updateDuration("30 min")
            form.updateIntensity("Easy")
            form.updatePreference("Indoor")
            assertEquals(plan, memberAPlan(form, plan.location))
            assertNull(memberAPlan(form, plan.location.copy(latitude = Double.NaN)))
            assertNull(memberAPlan(form, plan.location.copy(longitude = 181.0)))
            assertNull(memberAPlan(form, plan.location.copy(label = "Other place")))
        }
    }

    private fun prefs(id: String) = UserPreferences(id, "Stay Active", Intensity.LOW,
        150, listOf("Yoga"), Environment.INDOOR)
}

private class ContextUiAuth : AuthRepository {
    override val currentUser = MutableStateFlow<UserAccount?>(UserAccount("a", "A", "a@example.com"))
    override suspend fun signIn(email: String, password: String) = Result.success(requireNotNull(currentUser.value))
    override suspend fun signUp(displayName: String, email: String, password: String) = signIn(email, password)
    override suspend fun signInWithGoogle(idToken: String) = signIn("", "")
    override suspend fun signOut(): Result<Unit> { currentUser.value = null; return Result.success(Unit) }
}
