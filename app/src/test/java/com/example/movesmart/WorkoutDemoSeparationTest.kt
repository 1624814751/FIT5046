package com.example.movesmart

import android.app.Activity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.movesmart.data.local.MoveSmartDatabase
import com.example.movesmart.data.local.RoomWorkoutRepository
import com.example.movesmart.data.remote.GoogleLoginProvider
import com.example.movesmart.navigation.MoveSmartApp
import com.example.movesmart.shared.*
import com.example.movesmart.ui.auth.AuthViewModel
import com.example.movesmart.ui.context.WorkoutViewModel
import com.example.movesmart.ui.preferences.PreferencesViewModel
import com.example.movesmart.ui.theme.MoveSmartTheme
import kotlinx.coroutines.flow.MutableStateFlow
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
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class WorkoutDemoSeparationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var database: MoveSmartDatabase
    private lateinit var repository: RoomWorkoutRepository
    private val auth = DemoBoundaryAuth()
    private val owner = object : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }

    @Before fun setUp() {
        androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(
            ApplicationProvider.getApplicationContext(), androidx.work.Configuration.Builder()
                .setExecutor(androidx.work.testing.SynchronousExecutor()).build())
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MoveSmartDatabase::class.java).build()
        repository = RoomWorkoutRepository(database)
        runBlocking {
            repository.upsert(WorkoutRecord(0, "local-demo", "Existing demo walk", 30,
                LocalDate.now(), null, DataSource.DEMO)).getOrThrow()
        }
    }

    @After fun tearDown() {
        compose.runOnIdle { owner.viewModelStore.clear() }
        database.close()
    }

    private fun start(signedIn: Boolean, reminderRequest: Int = 0) {
        if (signedIn) auth.currentUser.value = UserAccount("firebase-user", "Account", "account@example.com")
        val preferences = object : PreferencesRepository {
            override fun observe(userId: String) = flowOf<UserPreferences?>(null)
            override suspend fun save(preferences: UserPreferences) = Result.success(Unit)
        }
        val google = object : GoogleLoginProvider {
            override suspend fun getIdToken(activity: Activity) = Result.failure<String>(UnsupportedOperationException())
            override suspend fun clear() = Result.success(Unit)
        }
        compose.runOnIdle {
            val factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
                    AuthViewModel::class.java -> AuthViewModel(auth, google)
                    PreferencesViewModel::class.java -> PreferencesViewModel(auth, preferences)
                    WorkoutViewModel::class.java -> WorkoutViewModel(SavedStateHandle())
                    else -> error("Unexpected model")
                } as T
            }
            ViewModelProvider(owner, factory)[AuthViewModel::class.java]
            ViewModelProvider(owner, factory)[PreferencesViewModel::class.java]
            listOf("local-demo", "firebase-user", "firebase-b", "second-user", "signed-out").forEach { userId ->
                ViewModelProvider(owner, factory)["plan-form:$userId", WorkoutViewModel::class.java]
            }
        }
        compose.setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                MoveSmartTheme { MoveSmartApp(openReminderRequest = reminderRequest, workoutRepository = repository, sensorRepository = object : SensorRepository {
                    override fun playback(datasetId: String) = flowOf(MotionContext(
                        datasetId, 0, 0, listOf(0.1, 0.2, 0.9), listOf(0.01, 0.02, 0.03),
                        "g", "rad/s", "WALKING", MovementBand.MODERATE, DataSource.DEMO))
                }) }
            }
        }
    }

    private fun history() = compose.onNode(hasText("History") and isSelectable()).performClick()
    private fun assertDemoUnchanged() = runBlocking {
        val rows = repository.observe("local-demo").first()
        assertEquals(listOf("Existing demo walk"), rows.map { it.title })
        assertEquals(listOf(30), rows.map { it.minutes })
        assertEquals(emptyList<WorkoutRecord>(), repository.observe("firebase-user").first())
    }

    @Test fun handledNotificationDoesNotReplayAfterAccountChange() {
        start(signedIn = true, reminderRequest = 1)
        compose.onNodeWithText("Saved on this device. Android may delay reminders to save battery.").assertExists()
        compose.onNode(hasText("Home") and isSelectable()).performClick()
        compose.runOnIdle {
            auth.currentUser.value = UserAccount("second-user", "Second", "second@example.com")
        }
        compose.waitForIdle()
        compose.onNodeWithText("Saved on this device. Android may delay reminders to save battery.").assertDoesNotExist()
        compose.onNode(hasText("Home") and isSelectable()).assertIsSelected()
    }


    @Test fun signedInSampleWorkoutSavesExactlyOnceUnderTheFirebaseUid() {
        start(signedIn = true)
        compose.onNodeWithContentDescription("Search").performClick()
        compose.onAllNodesWithText("Start sample workout")[0].performScrollTo().performClick()
        compose.onNodeWithText("Source: UCI HAPT public dataset · Demo playback")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Finish workout").performScrollTo().performClick()
        compose.onNode(hasText("Save session") and hasAnyAncestor(isDialog())).performClick()

        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Outdoor Walk").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Outdoor Walk").assertIsDisplayed()

        runBlocking {
            val accountRows = repository.observe("firebase-user").first()
            assertEquals(1, accountRows.size)
            assertEquals("firebase-user", accountRows.single().userId)
            assertEquals(DataSource.DEMO, accountRows.single().source)
            assertEquals(1, accountRows.single().minutes)
            assertEquals(emptyList<WorkoutRecord>(), repository.observe("signed-out").first())

            val demoRows = repository.observe("local-demo").first()
            assertEquals(listOf("Existing demo walk"), demoRows.map { it.title })
        }
    }

    @Test fun restoredFirebaseSessionCannotViewOrWriteDemoHistory() {
        start(signedIn = true)
        history()
        compose.onNodeWithText("Existing demo walk").assertDoesNotExist()
        compose.onNodeWithText("Existing demo walk").assertDoesNotExist()
        compose.onNodeWithText("Add workout").assertExists()
        compose.onNode(hasText("Statistics") and isSelectable()).performClick()
        compose.onNodeWithText("Existing demo walk").assertDoesNotExist()
        compose.onNodeWithText("Your week in motion").assertExists()
        compose.onNode(hasText("Home") and isSelectable()).performClick()
        compose.onNodeWithContentDescription("Search").performClick()
        compose.onNodeWithText("Existing demo walk").assertDoesNotExist()
        compose.onNodeWithText("My history").assertExists()
        assertDemoUnchanged()
    }

    @Test fun signedOutCannotEnterDemoOrReadLegacyRecords() {
        start(signedIn = false)
        compose.onNodeWithText("Continue with Google").assertExists()
        compose.onNodeWithText("Explore demo").assertDoesNotExist()
        compose.onNode(hasText("History") and isSelectable()).assertDoesNotExist()
        compose.onNodeWithText("Existing demo walk").assertDoesNotExist()
        assertDemoUnchanged()
    }

    @Test fun accountSwitchRemovesOldEditorAndSaveAction() {
        start(signedIn = true)
        history()
        compose.onNodeWithText("Add workout").performClick()
        compose.onNodeWithText("Duration (minutes)").performTextReplacement("47")
        compose.runOnIdle { auth.currentUser.value = UserAccount("firebase-b", "B", "b@example.com") }
        compose.onNodeWithText("Save workout").assertDoesNotExist()
        compose.onNodeWithText("Duration (minutes)").assertDoesNotExist()
        history()
        compose.onNodeWithText("Existing demo walk").assertDoesNotExist()
        assertDemoUnchanged()
    }

    @Test fun logoutDuringReplayRemovesFinishActionWithoutSavingOrDemoFallback() {
        start(signedIn = true)
        compose.onNodeWithContentDescription("Search").performClick()
        compose.onAllNodesWithText("Start sample workout")[0].performScrollTo().performClick()
        compose.onNodeWithText("Finish workout").assertExists()
        compose.runOnIdle { auth.currentUser.value = null }
        compose.onNodeWithText("Continue with Google").assertExists()
        compose.onNodeWithText("Finish workout").assertDoesNotExist()
        compose.onNodeWithText("Explore demo").assertDoesNotExist()
        assertDemoUnchanged()
    }

}

private class DemoBoundaryAuth : AuthRepository {
    override val currentUser = MutableStateFlow<UserAccount?>(null)
    override suspend fun signIn(email: String, password: String) = Result.failure<UserAccount>(UnsupportedOperationException())
    override suspend fun signUp(displayName: String, email: String, password: String) = signIn(email, password)
    override suspend fun signInWithGoogle(idToken: String) = signIn("", "")
    override suspend fun signOut(): Result<Unit> { currentUser.value = null; return Result.success(Unit) }
}
