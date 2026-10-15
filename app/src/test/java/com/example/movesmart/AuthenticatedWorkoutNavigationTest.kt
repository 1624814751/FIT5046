package com.example.movesmart

import android.app.Activity
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.movesmart.data.local.MoveSmartDatabase
import com.example.movesmart.data.local.RoomWorkoutRepository
import com.example.movesmart.data.remote.GoogleLoginProvider
import com.example.movesmart.navigation.MoveSmartApp
import com.example.movesmart.shared.*
import com.example.movesmart.ui.auth.AuthViewModel
import com.example.movesmart.ui.preferences.PreferencesViewModel
import com.example.movesmart.ui.theme.MoveSmartTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

/** Integration coverage for Member C's authenticated Live flow and Member D's navigation guards. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class AuthenticatedWorkoutNavigationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var database: MoveSmartDatabase
    private lateinit var repository: RoomWorkoutRepository
    private lateinit var back: OnBackPressedDispatcher
    private val notification = mutableIntStateOf(0)

    @Before fun setUp() {
        androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(
            ApplicationProvider.getApplicationContext(), androidx.work.Configuration.Builder()
                .setExecutor(androidx.work.testing.SynchronousExecutor()).build())
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MoveSmartDatabase::class.java).build()
        repository = RoomWorkoutRepository(database)
    }

    @After fun tearDown() { database.close() }

    private fun start(workouts: WorkoutRepository = repository) {
        val auth = object : AuthRepository {
            override val currentUser = MutableStateFlow<UserAccount?>(UserAccount("firebase-user", "Account", "account@example.com"))
            override suspend fun signIn(email: String, password: String) = Result.failure<UserAccount>(UnsupportedOperationException())
            override suspend fun signUp(displayName: String, email: String, password: String) = signIn(email, password)
            override suspend fun signInWithGoogle(idToken: String) = signIn("", "")
            override suspend fun signOut(): Result<Unit> { currentUser.value = null; return Result.success(Unit) }
        }
        val google = object : GoogleLoginProvider {
            override suspend fun getIdToken(activity: Activity) = Result.failure<String>(UnsupportedOperationException())
            override suspend fun clear() = Result.success(Unit)
        }
        val preferences = object : PreferencesRepository {
            override fun observe(userId: String) = flowOf<UserPreferences?>(null)
            override suspend fun save(preferences: UserPreferences) = Result.success(Unit)
        }
        val authVm = AuthViewModel(auth, google)
        val preferencesVm = PreferencesViewModel(auth, preferences)
        val sensors = object : SensorRepository {
            override fun playback(datasetId: String) = emptyFlow<MotionContext>()
        }
        compose.setContent {
            back = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            MoveSmartTheme {
                MoveSmartApp(openReminderRequest = notification.intValue, workoutRepository = workouts,
                    sensorRepository = sensors, authViewModelOverride = authVm,
                    preferencesViewModelOverride = preferencesVm)
            }
        }
        compose.onNodeWithContentDescription("Search").performClick()
        compose.onAllNodesWithText("Start sample workout")[0].performScrollTo().performClick()
        compose.onNodeWithText("Finish workout").assertExists()
    }

    private fun tap(text: String) = compose.onNodeWithText(text).performClick()
    private fun savedCount() = runBlocking { repository.observe("firebase-user").first().size }

    @Test fun toolbarAndSystemBackAskBeforeDiscardingAuthenticatedWorkout() {
        start()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Leave this workout?").assertExists()
        tap("Stay in workout")
        compose.onNodeWithText("Finish workout").assertExists()
        compose.runOnIdle { back.onBackPressed() }
        compose.onNodeWithText("Leave this workout?").assertExists()
        tap("Leave without saving")
        compose.onNodeWithText("Finish workout").assertDoesNotExist()
        assertEquals(0, savedCount())
    }

    @Test fun notificationWaitsUntilAuthenticatedWorkoutIsDiscarded() {
        start()
        compose.runOnIdle { notification.intValue = 1 }
        compose.onNodeWithText("Finish workout").assertExists()
        compose.onNodeWithText("Saved on this device. Android may delay reminders to save battery.").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back").performClick()
        tap("Stay in workout")
        compose.onNodeWithText("Saved on this device. Android may delay reminders to save battery.").assertDoesNotExist()
        compose.runOnIdle { back.onBackPressed() }
        tap("Leave without saving")
        compose.onNodeWithText("Saved on this device. Android may delay reminders to save battery.").assertExists()
        assertEquals(0, savedCount())
    }

    @Test fun repeatedLeaveInSameFramePopsOnlyTheLiveScreen() {
        start()
        compose.onNodeWithContentDescription("Back").performClick()
        val leave = compose.onNodeWithText("Leave without saving")
            .fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        compose.runOnIdle { leave(); leave() }
        // Live was entered from Search. A duplicate pop would incorrectly return to Home.
        compose.onAllNodesWithText("Start sample workout")[0].assertExists()
        assertEquals(0, savedCount())
    }

    @Test fun delayedNotificationThenRepeatedFinishSavesOneRecordBeforeOpeningReminders() {
        val gate = CompletableDeferred<Unit>()
        var writes = 0
        val delayed = object : WorkoutRepository by repository {
            override suspend fun upsert(record: WorkoutRecord): Result<Int> {
                writes++
                gate.await()
                return repository.upsert(record)
            }
        }
        start(delayed)
        compose.runOnIdle { notification.intValue = 1 }
        compose.onNodeWithText("Finish workout").performScrollTo().performClick()
        // Invoke two rapid submissions in the same frame, before recomposition disables the UI.
        val submit = compose.onNode(hasText("Save session") and hasAnyAncestor(isDialog()))
            .fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        compose.runOnIdle { submit(); submit() }
        compose.waitUntil(10_000) { writes == 1 }
        compose.onNodeWithText("Saving…").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Back").assertIsNotEnabled()
        compose.runOnIdle { back.onBackPressed() }
        compose.onNodeWithText("Live workout").assertExists()
        compose.onNodeWithText("Leave this workout?").assertDoesNotExist()
        compose.runOnIdle { gate.complete(Unit) }
        compose.waitUntil(10_000) { savedCount() == 1 }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Finish workout").fetchSemanticsNodes().isEmpty() }
        assertEquals(1, writes)
        compose.onNodeWithText("Saved on this device. Android may delay reminders to save battery.").assertExists()
        assertEquals(0, runBlocking { repository.observe("local-demo").first().size })
    }
}
