package com.example.movesmart

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.movesmart.data.local.MoveSmartDatabase
import com.example.movesmart.data.local.RoomWorkoutRepository
import com.example.movesmart.shared.DataSource
import com.example.movesmart.shared.WorkoutRecord
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Uses uniquely named test files; never clears the user's application database. */
@RunWith(AndroidJUnit4::class)
class WorkoutDatabaseIntegrationTest {
    @Test fun fileDatabaseSurvivesReopenAndKeepsUsersSeparate() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "member-c-device-test-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, MoveSmartDatabase::class.java, name).build()
        var database = open()
        try {
            var repository = RoomWorkoutRepository(database)
            assertTrue(repository.observe("test-a").first().isEmpty())
            val record = WorkoutRecord(0, "test-a", "Yoga", 40, LocalDate.now(), null, DataSource.LIVE)
            val id = repository.upsert(record).getOrThrow()
            database.close()
            database = open()
            repository = RoomWorkoutRepository(database)
            assertEquals(record.copy(id = id), repository.get("test-a", id).getOrThrow())
            assertTrue(repository.observe("test-b").first().isEmpty())
            assertTrue(repository.delete("test-b", id).isFailure)
            assertTrue(repository.upsert(record.copy(id = id, userId = "test-b")).isFailure)
            repository.upsert(record.copy(id = id, minutes = 50)).getOrThrow()
            assertEquals(50, repository.observe("test-a").first().single().minutes)
            repository.delete("test-a", id).getOrThrow()
            assertTrue(repository.observe("test-a").first().isEmpty())
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
