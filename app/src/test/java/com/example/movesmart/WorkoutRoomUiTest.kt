package com.example.movesmart

import androidx.compose.ui.test.*
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.movesmart.data.local.MoveSmartDatabase
import com.example.movesmart.data.local.RoomWorkoutRepository
import com.example.movesmart.navigation.MoveSmartApp
import com.example.movesmart.shared.WorkoutRepository
import com.example.movesmart.shared.DataSource
import com.example.movesmart.shared.WorkoutRecord
import com.example.movesmart.ui.screens.WorkoutEditorScreen
import com.example.movesmart.ui.theme.MoveSmartTheme
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

/** Compose navigation plus real Room on a host Android runtime; not a physical-device test. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class WorkoutRoomUiTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var database: MoveSmartDatabase
    private lateinit var repository: RoomWorkoutRepository
    private lateinit var backDispatcher: OnBackPressedDispatcher
    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MoveSmartDatabase::class.java).build()
        repository = RoomWorkoutRepository(database)
    }
    @After fun tearDown() { database.close() }
    private fun start(repository: WorkoutRepository = this.repository) {
        compose.setContent {
            backDispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            MoveSmartTheme { MoveSmartApp(workoutRepository = repository) }
        }
        compose.onNode(hasText("Explore demo") and hasClickAction()).performScrollTo().performClick()
    }
    private fun tap(text: String) = compose.onNode(hasText(text) and hasClickAction()).performClick()
    private fun scrollTap(text: String) = compose.onNode(hasText(text) and hasClickAction()).performScrollTo().performClick()
    private fun waitForText(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
    private fun history() = compose.onNode(hasText("History") and isSelectable()).performClick()
    private fun statistics() = compose.onNode(hasText("Statistics") and isSelectable()).performClick()

    @Test fun emptyStartupAndCancelledEditorDoNotCreateRecords() {
        start()
        history()
        waitForText("No workouts yet")
        tap("Add workout")
        compose.onNodeWithText("Duration (minutes)").performTextReplacement("45")
        compose.onNodeWithContentDescription("Back").performClick()
        waitForText("No workouts yet")
        assertTrue(runBlocking { repository.observe("local-demo").firstRows() }.isEmpty())
    }

    @Test fun savedRecordsReachStatisticsAndEditsAndDeletesPersist() {
        start()
        history()
        waitForText("No workouts yet")
        tap("Add workout")
        compose.onNodeWithText("Duration (minutes)").performTextReplacement("45")
        scrollTap("Save workout")
        waitForText("Every session counts")
        compose.waitUntil(10_000) { runBlocking { repository.observe("local-demo").firstRowCount() } == 1 }
        // On the 320x470 host display, the feedback snackbar covers the Edit button.
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss))
            .performSemanticsAction(SemanticsActions.Dismiss) { it() }
        scrollTap("Edit")
        waitForText("Duration (minutes)")
        compose.onNodeWithText("Duration (minutes)").performTextReplacement("60")
        scrollTap("Save workout")
        waitForText("Every session counts")
        statistics()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("Weekly goal: 60 of 150 minutes").fetchSemanticsNodes().isNotEmpty()
        }
        history()
        compose.onNodeWithContentDescription("Search").performClick()
        tap("My history")
        compose.onNodeWithText("Search activities or history").performTextInput("walk")
        compose.onNodeWithText("Outdoor Walk").assertExists()
        scrollTap("View or edit session")
        compose.onNodeWithText("Duration (minutes)").assertTextContains("60")
        compose.onNodeWithContentDescription("Back").performClick()
        // Editor -> Search -> History. Re-selecting this tab would restore its Search stack.
        compose.onNodeWithContentDescription("Back").performClick()
        waitForText("Every session counts")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Delete") and hasClickAction())
        scrollTap("Delete")
        compose.onNode(hasText("Delete") and hasAnyAncestor(isDialog())).performClick()
        waitForText("No workouts yet")
        assertTrue(runBlocking { repository.observe("local-demo").firstRows() }.isEmpty())
        statistics()
        compose.onNodeWithContentDescription("Weekly goal: 0 of 150 minutes").assertExists()
    }

    @Test fun statisticsPeriodsUseSavedRecordsAndExcludeOtherUsers() {
        val today = LocalDate.now()
        runBlocking {
            repository.upsert(WorkoutRecord(0, "local-demo", "Today", 25, today, null, DataSource.LIVE)).getOrThrow()
            repository.upsert(WorkoutRecord(0, "local-demo", "Last month", 40,
                today.withDayOfMonth(1).minusDays(1), null, DataSource.LIVE)).getOrThrow()
            repository.upsert(WorkoutRecord(0, "another-user", "Private workout", 99, today, null, DataSource.LIVE)).getOrThrow()
        }
        start()
        statistics()
        waitForText("Your week in motion")
        tap("Day")
        compose.onNodeWithText("Your day in motion").assertExists()
        compose.onNodeWithText("1 sessions · 1 of 1 active days").assertExists()
        compose.onNodeWithText("25").assertExists()
        compose.onNodeWithText("99").assertDoesNotExist()
        tap("Month")
        compose.onNodeWithText("Your month in motion").assertExists()
        tap("Previous")
        compose.onNodeWithText("40").assertExists()
        compose.onNodeWithText("99").assertDoesNotExist()
    }

    @Test fun failedSaveKeepsEditorInputAndShowsAnError() {
        val failing = object : WorkoutRepository by repository {
            override suspend fun upsert(record: WorkoutRecord): Result<Int> = Result.failure(IllegalStateException("Test disk failure"))
        }
        start(failing)
        history()
        waitForText("No workouts yet")
        tap("Add workout")
        compose.onNodeWithText("Duration (minutes)").performTextReplacement("47")
        scrollTap("Save workout")
        waitForText("Could not save workout. Please try again.")
        compose.onNodeWithText("Duration (minutes)").assertTextContains("47")
        compose.onNodeWithText("Save workout").assertIsEnabled()
        assertTrue(runBlocking { repository.observe("local-demo").firstRows() }.isEmpty())
    }

    @Test fun readFailureHasRetryAndNeverShowsFakeRecords() {
        var failRead = true
        val flaky = object : WorkoutRepository by repository {
            override fun observe(userId: String): Flow<List<WorkoutRecord>> = flow {
                if (failRead) throw IllegalStateException("Test read failure")
                repository.observe(userId).collect { emit(it) }
            }
        }
        start(flaky)
        history()
        waitForText("Could not load workouts. Please retry.")
        compose.onNodeWithText("Outdoor Walk").assertDoesNotExist()
        failRead = false
        tap("Retry")
        waitForText("No workouts yet")
    }

    @Test fun systemBackCannotLeaveEditorWhileDatabaseWriteIsPending() {
        val gate = CompletableDeferred<Unit>()
        val delayed = object : WorkoutRepository by repository {
            override suspend fun upsert(record: WorkoutRecord): Result<Int> {
                gate.await()
                // This test isolates pending-write navigation; SQL is covered by the CRUD test.
                return Result.success(1)
            }
        }
        start(delayed)
        history()
        waitForText("No workouts yet")
        tap("Add workout")
        scrollTap("Save workout")
        waitForText("Saving…")
        compose.runOnIdle { backDispatcher.onBackPressed() }
        compose.onNodeWithText("Duration (minutes)").assertExists()
        compose.runOnIdle { gate.complete(Unit) }
        waitForText("Every session counts")
    }

    @Test fun editorAccepts1440MinutesButRejectsZeroWithoutCallingSave() {
        var saves = 0
        compose.setContent { MoveSmartTheme {
            WorkoutEditorScreen(null, LocalDate.now()) { _, _, _ -> saves++ }
        } }
        compose.onNodeWithText("Duration (minutes)").performTextReplacement("0")
        scrollTap("Save workout")
        assertEquals(0, saves)
        compose.onNodeWithText("Duration (minutes)").performTextReplacement("1440")
        scrollTap("Save workout")
        assertEquals(1, saves)
    }
}

private suspend fun Flow<List<WorkoutRecord>>.firstRows() = first()
private suspend fun Flow<List<WorkoutRecord>>.firstRowCount() = firstRows().size
