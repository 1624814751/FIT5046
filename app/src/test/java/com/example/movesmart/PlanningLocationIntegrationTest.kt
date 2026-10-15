package com.example.movesmart

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.example.movesmart.recommendation.SimpleRecommendationEngine
import com.example.movesmart.shared.*
import com.example.movesmart.ui.context.MemberAContextRoute
import com.example.movesmart.ui.context.WorkoutViewModel
import com.example.movesmart.ui.context.memberAPlan
import com.example.movesmart.ui.planning.*
import com.example.movesmart.ui.recommendation.RecommendationViewModel
import com.example.movesmart.ui.theme.MoveSmartTheme
import com.example.movesmart.ui.weather.WeatherViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/** Exercises the merge boundary: A's selected location/recommendation -> D's saved plan/details. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlanningLocationIntegrationTest {
    @get:Rule val compose = createComposeRule()
    private val owner = ViewModelStore()
    private val clayton = GeoLocation("Clayton VIC", -37.9167, 145.1167)
    private var location by mutableStateOf<GeoLocation?>(null)
    private var details by mutableStateOf<String?>(null)
    private val rows = MutableStateFlow<Map<String, Recommendation>>(emptyMap())
    private val requests = mutableListOf<WeatherQuery>()
    private lateinit var form: WorkoutViewModel
    private var offline = false
    private val preferences = UserPreferences("a", "Stay Active", Intensity.LOW, 150,
        listOf("Yoga"), Environment.INDOOR)

    private fun start(selected: GeoLocation? = clayton, failWeather: Boolean = false) {
        offline = failWeather
        lateinit var weather: WeatherViewModel
        lateinit var planning: PlanningViewModel
        lateinit var recommendations: RecommendationViewModel
        compose.runOnIdle {
            location = selected
            form = WorkoutViewModel(SavedStateHandle()).apply {
                updateIntensity("Easy"); updatePreference("Indoor"); updateDuration("15 min")
                selected?.let { updateLocation(it.label) }
            }
            val store = object : RecommendationStore {
                override fun observe(id: String) = rows.map { it[id] }
                override suspend fun save(recommendation: Recommendation): Result<Unit> {
                    rows.value = rows.value + (recommendation.id to recommendation)
                    return Result.success(Unit)
                }
            }
            weather = WeatherViewModel(object : WeatherRepository {
                override fun observe(query: WeatherQuery) = emptyFlow<WeatherContext?>()
                override suspend fun refresh(query: WeatherQuery): Result<WeatherContext> {
                    requests.add(query)
                    return if (offline) Result.failure(IllegalStateException("Weather offline")) else
                        Result.success(WeatherContext(query, 18.0, 10, 5.0, Instant.now(), DataSource.LIVE, false))
                }
            })
            val auth = object : AuthRepository {
                override val currentUser = MutableStateFlow<UserAccount?>(UserAccount("a", "A", "a@example.com"))
                override suspend fun signIn(email: String, password: String) = Result.success(currentUser.value!!)
                override suspend fun signUp(displayName: String, email: String, password: String) = signIn(email, password)
                override suspend fun signInWithGoogle(idToken: String) = signIn("", "")
                override suspend fun signOut(): Result<Unit> { currentUser.value = null; return Result.success(Unit) }
            }
            recommendations = RecommendationViewModel(auth, object : PreferencesRepository {
                override fun observe(userId: String) = flowOf(preferences)
                override suspend fun save(preferences: UserPreferences) = Result.success(Unit)
            }, SimpleRecommendationEngine(), { store })
            planning = PlanningViewModel("a", store)
            owner.put("weather", weather); owner.put("planning", planning); owner.put("recommendations", recommendations)
        }
        compose.setContent {
            MoveSmartTheme {
                val id = details
                if (id == null) PlanningScreen(planning, weather, preferences, form, location,
                    contextContent = {
                        MemberAContextRoute("a", memberAPlan(form, location), weather, recommendations,
                            onSelected = { details = it })
                    }, onDetails = { details = it })
                else RecommendationDetails(planning, id, onBack = { details = null }, onReminder = {}, onStart = {})
            }
        }
        compose.waitForIdle()
    }

    @After fun clear() { compose.runOnIdle { owner.clear() } }
    private fun tap(text: String) = compose.onNodeWithText(text).performScrollTo().performClick()

    @Test fun planningUsesSelectedCoordinatesAndSharedFormAndOpensPersistedDetails() {
        start()
        tap("Show activity details")
        compose.onNodeWithText("Start activity").assertExists()
        val saved = rows.value.values.single()
        assertEquals(clayton, saved.location)
        assertEquals(15, saved.minutes)
        assertEquals(Intensity.LOW, saved.intensity)
        assertEquals(clayton, requests.last().location)
    }

    @Test fun localOfflineRecommendationCanBeSelectedAndReadByMemberDDetails() {
        start(failWeather = true)
        compose.onNodeWithText("Show activity details").assertIsNotEnabled()
        tap("Generate recommendation")
        tap("Select recommendation")
        compose.onNodeWithText("Start activity").assertExists()
        assertEquals(clayton, rows.value.values.single().location)
    }

    @Test fun removingAndChangingLocationNeverFallsBackToMelbourneOrOldForecast() {
        start(selected = null)
        compose.onNodeWithText("Load weather").assertIsNotEnabled()
        compose.onNodeWithText("Show activity details").assertIsNotEnabled()
        assertTrue(requests.isEmpty())
        compose.runOnIdle { location = clayton; form.updateLocation(clayton.label) }
        compose.waitForIdle()
        assertEquals(clayton, requests.last().location)
        compose.runOnIdle { location = null }
        compose.onNodeWithText("Show activity details").assertIsNotEnabled()
        val other = GeoLocation("Sydney NSW", -33.8688, 151.2093)
        compose.runOnIdle { location = other; form.updateLocation(other.label) }
        compose.waitForIdle()
        tap("Show activity details")
        assertEquals(other, requests.last().location)
        assertEquals(other, rows.value.values.single().location)
    }
}
