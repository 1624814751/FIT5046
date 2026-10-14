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

    private fun start(signedIn: Boolean) {
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
            listOf("local-demo", "firebase-user", "signed-out").forEach { userId ->
                ViewModelProvider(owner, factory)["demo-plan:$userId", WorkoutViewModel::class.java]
            }
        }
        compose.setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                MoveSmartTheme { MoveSmartApp(workoutRepository = repository) }
            }
        }
        if (!signedIn) compose.onNodeWithText("Explore demo").performScrollTo().performClick()
    }

    private fun history() = compose.onNode(hasText("History") and isSelectable()).performClick()
    private fun assertDemoHistoryVisible() {
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Existing demo walk"))
        compose.onNodeWithText("Existing demo walk").assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
    }
    private fun assertDemoUnchanged() = runBlocking {
        val rows = repository.observe("local-demo").first()
        assertEquals(listOf("Existing demo walk"), rows.map { it.title })
        assertEquals(listOf(30), rows.map { it.minutes })
        assertEquals(emptyList<WorkoutRecord>(), repository.observe("firebase-user").first())
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

    @Test fun signInWhileDemoEditorIsOpenRemovesPrivateInputAndSaveAction() {
        start(signedIn = false)
        history()
        assertDemoHistoryVisible()
        compose.onNodeWithText("Add workout").performClick()
        compose.onNodeWithText("Duration (minutes)").performTextReplacement("47")
        compose.runOnIdle { auth.currentUser.value = UserAccount("firebase-user", "Account", "account@example.com") }
        compose.onNodeWithText("Existing demo walk").assertDoesNotExist()
        compose.onNodeWithText("Save workout").assertDoesNotExist()
        compose.onNodeWithText("Duration (minutes)").assertDoesNotExist()
        assertDemoUnchanged()
    }

    @Test fun signedInSampleWorkoutCannotSaveAndLogoutRestoresExplicitDemoAccess() {
        start(signedIn = true)
        compose.onNodeWithContentDescription("Search").performClick()
        compose.onAllNodesWithText("Start sample workout")[0].performScrollTo().performClick()
        compose.onNodeWithText("Live tracking is not connected yet").assertExists()
        compose.onNodeWithText("Finish workout").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNode(hasText("Profile") and isSelectable()).performClick()
        compose.onNodeWithText("Log out").performScrollTo().performClick()
        compose.onNode(hasText("Log out") and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Explore demo").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Explore demo").performScrollTo().performClick()
        history()
        assertDemoHistoryVisible()
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
