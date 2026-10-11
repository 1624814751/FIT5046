package com.example.movesmart

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.movesmart.data.local.MoveSmartDatabase
import com.example.movesmart.data.local.RoomWorkoutRepository
import com.example.movesmart.shared.DataSource
import com.example.movesmart.shared.WorkoutRecord
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

/** Real Room-generated SQL, not a fake repository. Each test owns a separate database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class RoomWorkoutRepositoryTest {
    private lateinit var database: MoveSmartDatabase
    private lateinit var repository: RoomWorkoutRepository
    private val today = LocalDate.of(2026, 10, 11)
    private val clock = Clock.fixed(Instant.parse("2026-10-11T12:00:00Z"), ZoneOffset.UTC)
    private fun record(user: String = "user-a", title: String = "Walk", date: LocalDate = today) =
        WorkoutRecord(0, user, title, 30, date, Instant.parse("2026-10-11T01:23:45Z"), DataSource.DEMO)

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MoveSmartDatabase::class.java).build()
        repository = RoomWorkoutRepository(database, clock)
    }
    @After fun tearDown() { database.close() }

    @Test fun newDatabaseStartsEmpty() = runBlocking {
        assertTrue(repository.observe("user-a").first().isEmpty())
    }

    @Test fun insertsTrimmedRecordWithAllocatedIdAndMetadata() = runBlocking {
        val input = record(title = "  Walk  ")
        val result = repository.upsert(input)
        assertTrue("Insert must succeed", result.isSuccess)
        val id = result.getOrThrow()
        assertTrue(id > 0)
        assertEquals(input.copy(id = id, title = "Walk"), repository.get("user-a", id).getOrThrow())
    }

    @Test fun updatesExistingRecordWithoutCreatingDuplicate() = runBlocking {
        val id = repository.upsert(record()).getOrThrow()
        assertEquals(id, repository.upsert(record(title = "Yoga").copy(id = id, minutes = 45)).getOrThrow())
        assertEquals(1, repository.observe("user-a").first().size)
        assertEquals(45, repository.get("user-a", id).getOrThrow()!!.minutes)
    }

    @Test fun deletesOnlyRequestedRecord() = runBlocking {
        val first = repository.upsert(record()).getOrThrow()
        val second = repository.upsert(record(title = "Yoga")).getOrThrow()
        repository.delete("user-a", first).getOrThrow()
        assertNull(repository.get("user-a", first).getOrThrow())
        assertEquals(listOf(second), repository.observe("user-a").first().map { it.id })
    }

    @Test fun userQueriesAndMutationsCannotAccessAnotherUsersId() = runBlocking {
        val id = repository.upsert(record()).getOrThrow()
        repository.upsert(record(user = "user-b")).getOrThrow()
        assertNull(repository.get("user-b", id).getOrThrow())
        assertTrue(repository.upsert(record(user = "user-b", title = "Foreign edit").copy(id = id)).isFailure)
        assertTrue(repository.delete("user-b", id).isFailure)
        assertEquals("Walk", repository.get("user-a", id).getOrThrow()!!.title)
        assertEquals(1, repository.observe("user-b").first().size)
    }

    @Test fun observesNewestDateThenNewestId() = runBlocking {
        repository.upsert(record(date = today.minusDays(1))).getOrThrow()
        val olderId = repository.upsert(record()).getOrThrow()
        val newerId = repository.upsert(record()).getOrThrow()
        val rows = repository.observe("user-a").first()
        assertEquals(listOf(newerId, olderId), rows.take(2).map { it.id })
        assertEquals(today.minusDays(1), rows.last().date)
    }

    @Test fun missingUpdateAndDeleteFailInsteadOfCreatingRecords() = runBlocking {
        assertTrue(repository.upsert(record().copy(id = 99)).isFailure)
        assertTrue(repository.delete("user-a", 99).isFailure)
        assertTrue(repository.observe("user-a").first().isEmpty())
    }

    @Test fun rejectsInvalidInputWithoutWritingRows() = runBlocking {
        listOf(record().copy(minutes = 0), record().copy(date = today.plusDays(1)),
            record().copy(title = " "), record().copy(userId = " "), record().copy(id = -1))
            .forEach { assertTrue(repository.upsert(it).isFailure) }
        assertTrue(repository.get(" ", 1).isFailure)
        assertTrue(repository.delete("user-a", 0).isFailure)
        assertTrue(repository.observe("user-a").first().isEmpty())
    }

    @Test fun fileDatabaseRetainsRecordsAfterCloseAndReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "workout-test-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, MoveSmartDatabase::class.java, name).build()
        var fileDatabase = open()
        try {
            val id = RoomWorkoutRepository(fileDatabase, clock).upsert(record()).getOrThrow()
            fileDatabase.close()
            fileDatabase = open()
            assertEquals(record().copy(id = id), RoomWorkoutRepository(fileDatabase, clock).get("user-a", id).getOrThrow())
        } finally {
            fileDatabase.close()
            context.deleteDatabase(name) // This test's uniquely named file only.
        }
    }
}
