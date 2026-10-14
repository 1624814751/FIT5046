package com.example.movesmart

import androidx.lifecycle.ViewModelStore
import com.example.movesmart.recommendation.SimpleRecommendationEngine
import com.example.movesmart.shared.*
import com.example.movesmart.ui.recommendation.RecommendationViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class RecommendationViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()
    private val now = Instant.parse("2026-10-13T01:00:00Z")
    private val plan = PlanningInput(LocalDate.of(2026, 10, 13), 30, Intensity.LOW,
        Environment.ANY, GeoLocation("Melbourne", -37.8136, 144.9631))
    private val auth = RecommendationTestAuth()
    private val preferences = RecommendationTestPreferences()
    private val stores = mutableMapOf<String, RecommendationTestStore>()
    private val owner = ViewModelStore()

    private fun model() = RecommendationViewModel(auth, preferences, SimpleRecommendationEngine(),
        { stores.getOrPut(it) { RecommendationTestStore() } }, Clock.fixed(now, ZoneOffset.UTC)).also {
        owner.put("recommendations", it)
    }

    @Test fun savedPreferencesWeatherTimeAndMotionReachTheEngineAndStore() = runTest {
        val model = model()
        runCurrent()
        preferences.values.getValue("a").value = savedPreference("a").copy(activityNames = listOf("Yoga"))
        runCurrent()
        val weather = WeatherContext(WeatherQuery(plan.location, plan.date), 18.0, 80, 10.0, now, DataSource.LIVE, false)
        val motion = MotionContext("sample", 1, 100, emptyList(), emptyList(), "g", "rad/s",
            "Walking", MovementBand.HIGH, DataSource.DEMO)
        model.generateRecommendation(plan.copy(availableMinutes = 15), weather, motion)
        assertEquals(UiState.Loading, model.state.value)
        runCurrent()
        val decision = (model.state.value as UiState.Success).data!!
        assertEquals("Yoga", decision.recommendation.activityName)
        assertEquals(15, decision.recommendation.minutes)
        assertTrue(decision.recommendation.reasons.any { it.contains("60%") })
        assertNotNull(decision.safetyPrompt)
        assertEquals(decision.recommendation, stores.getValue("a").saved.value[decision.recommendation.id])
        owner.clear()
    }

    @Test fun missingPreferencesAndNoMatchingActivityAreClearErrors() = runTest {
        preferences.values.getValue("a").value = null
        val model = model()
        runCurrent()
        model.generateRecommendation(plan, null)
        assertTrue((model.state.value as UiState.Error).message.contains("Save your exercise preferences"))
        preferences.values.getValue("a").value = savedPreference("a").copy(activityNames = listOf("Swimming"))
        runCurrent()
        model.generateRecommendation(plan, null)
        runCurrent()
        assertTrue((model.state.value as UiState.Error).message.contains("No preferred activity fits"))
        assertFalse((model.state.value as UiState.Error).retryable)
        assertTrue(stores.isEmpty())
        owner.clear()
    }

    @Test fun updatedPreferencesClearPreviousDecisionAndChangeNextResult() = runTest {
        val model = model()
        runCurrent()
        model.generateRecommendation(plan, null)
        runCurrent()
        assertTrue(model.state.value is UiState.Success)
        preferences.values.getValue("a").value = savedPreference("a").copy(goal = "Exercise More Regularly")
        runCurrent()
        assertEquals(UiState.Idle, model.state.value)
        model.retry()
        runCurrent()
        assertEquals(15, (model.state.value as UiState.Success).data!!.recommendation.minutes)
        owner.clear()
    }

    @Test fun accountSwitchUsesDifferentStoreAndCannotRestoreOtherUsersId() = runTest {
        val model = model()
        runCurrent()
        model.generateRecommendation(plan, null)
        runCurrent()
        val id = (model.state.value as UiState.Success).data!!.recommendation.id
        auth.currentUser.value = UserAccount("b", "B", "b@example.com")
        runCurrent()
        assertEquals(UiState.Idle, model.state.value)
        model.loadRecommendation(id)
        runCurrent()
        assertNull((model.state.value as UiState.Success).data)
        auth.currentUser.value = UserAccount("a", "A", "a@example.com")
        runCurrent()
        model.loadRecommendation(id)
        runCurrent()
        assertEquals(id, (model.state.value as UiState.Success).data!!.recommendation.id)
        assertNull((model.state.value as UiState.Success).data!!.safetyPrompt)
        owner.clear()
    }

    @Test fun lateSaveCannotDisplayAnOldAccountsResult() = runTest {
        val gate = CompletableDeferred<Unit>()
        stores["a"] = RecommendationTestStore().apply { saveGate = gate }
        val model = model()
        runCurrent()
        model.generateRecommendation(plan, null)
        runCurrent()
        auth.currentUser.value = UserAccount("b", "B", "b@example.com")
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertEquals(UiState.Idle, model.state.value)
        assertFalse(stores.containsKey("b"))
        model.generateRecommendation(plan, null)
        runCurrent()
        assertTrue(model.state.value is UiState.Success)
        assertEquals(1, stores.getValue("b").saved.value.size)
        owner.clear()
    }

    @Test fun changedPreferencesInvalidateAnUnfinishedSave() = runTest {
        val gate = CompletableDeferred<Unit>()
        stores["a"] = RecommendationTestStore().apply { saveGate = gate }
        val model = model()
        runCurrent()
        model.generateRecommendation(plan, null)
        runCurrent()
        preferences.values.getValue("a").value = savedPreference("a").copy(weeklyGoalMinutes = 210)
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertEquals(UiState.Idle, model.state.value)
        owner.clear()
    }

    @Test fun saveAndReadFailuresAreRetryableWithoutCrashing() = runTest {
        val store = RecommendationTestStore().apply { saveError = IllegalStateException("Disk full") }
        stores["a"] = store
        val model = model()
        runCurrent()
        model.generateRecommendation(plan, null)
        runCurrent()
        assertEquals(UiState.Error("Disk full", true), model.state.value)
        store.saveError = null
        model.retry()
        runCurrent()
        val id = (model.state.value as UiState.Success).data!!.recommendation.id
        store.readError = IllegalStateException("Cannot read")
        model.loadRecommendation(id)
        runCurrent()
        assertEquals(UiState.Error("Cannot read", true), model.state.value)
        store.readError = null
        model.retry()
        runCurrent()
        assertNotNull((model.state.value as UiState.Success).data)
        owner.clear()
    }

    @Test fun preferenceReadErrorCanBeRetried() = runTest {
        preferences.error = IllegalStateException("Preferences unavailable")
        val model = model()
        runCurrent()
        assertEquals(UiState.Error("Preferences unavailable", true), model.state.value)
        preferences.error = null
        model.retry()
        runCurrent()
        assertEquals(UiState.Idle, model.state.value)
        model.generateRecommendation(plan, null)
        runCurrent()
        assertTrue(model.state.value is UiState.Success)
        owner.clear()
    }

    @Test fun logoutClearsResultsAndRejectsGeneration() = runTest {
        val model = model()
        runCurrent()
        model.generateRecommendation(plan, null)
        runCurrent()
        auth.currentUser.value = null
        runCurrent()
        assertEquals(UiState.Idle, model.state.value)
        model.generateRecommendation(plan, null)
        assertTrue((model.state.value as UiState.Error).message.contains("Sign in"))
        owner.clear()
    }

    @Test fun cancellationDoesNotPublishAnErrorOrResult() = runTest {
        stores["a"] = RecommendationTestStore().apply { saveError = CancellationException() }
        val model = model()
        runCurrent()
        model.generateRecommendation(plan, null)
        runCurrent()
        assertFalse(model.state.value is UiState.Error)
        assertFalse(model.state.value is UiState.Success)
        owner.clear()
    }
}

private fun savedPreference(id: String) =
    UserPreferences(id, "Stay Active", Intensity.LOW, 150, emptyList(), Environment.ANY)

private class RecommendationTestPreferences : PreferencesRepository {
    val values = mutableMapOf("a" to MutableStateFlow<UserPreferences?>(savedPreference("a")),
        "b" to MutableStateFlow<UserPreferences?>(savedPreference("b")))
    var error: Exception? = null
    override fun observe(userId: String): Flow<UserPreferences?> {
        val failure = error
        return if (failure != null) flow { throw failure } else values.getValue(userId)
    }
    override suspend fun save(preferences: UserPreferences): Result<Unit> {
        values.getValue(preferences.userId).value = preferences
        return Result.success(Unit)
    }
}

private class RecommendationTestStore : RecommendationStore {
    val saved = MutableStateFlow<Map<String, Recommendation>>(emptyMap())
    var saveGate: CompletableDeferred<Unit>? = null
    var saveError: Exception? = null
    var readError: Exception? = null
    override fun observe(id: String): Flow<Recommendation?> {
        val failure = readError
        return if (failure != null) flow { throw failure } else saved.map { it[id] }
    }
    override suspend fun save(recommendation: Recommendation): Result<Unit> {
        withContext(NonCancellable) { saveGate?.await() }
        saveError?.let { throw it }
        saved.value = saved.value + (recommendation.id to recommendation)
        return Result.success(Unit)
    }
}

private class RecommendationTestAuth : AuthRepository {
    override val currentUser = MutableStateFlow<UserAccount?>(UserAccount("a", "A", "a@example.com"))
    override suspend fun signIn(email: String, password: String) = Result.success(requireNotNull(currentUser.value))
    override suspend fun signUp(displayName: String, email: String, password: String) = signIn(email, password)
    override suspend fun signInWithGoogle(idToken: String) = signIn("", "")
    override suspend fun signOut(): Result<Unit> { currentUser.value = null; return Result.success(Unit) }
}
