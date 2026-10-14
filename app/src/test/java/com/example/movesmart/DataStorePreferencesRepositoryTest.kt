package com.example.movesmart

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.example.movesmart.shared.Environment
import com.example.movesmart.shared.Intensity
import com.example.movesmart.shared.UserPreferences
import com.example.movesmart.ui.preferences.DataStorePreferencesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class DataStorePreferencesRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test fun cancellationIsPropagatedWithoutChangingTheStorageFormat() = runTest {
        val dataStore = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = emptyFlow()
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                throw CancellationException("Cancelled save")
            }
        }
        try {
            DataStorePreferencesRepository(dataStore).save(samplePreferences("user-a", 150))
            fail("Cancellation should be propagated")
        } catch (_: CancellationException) {
        }
    }

    @Test
    fun saveAndLoadPreferences() = runTest {
        val repository = makeRepository(this, "save.preferences_pb")
        val preferences = samplePreferences("user-a", 150)

        assertEquals(true, repository.save(preferences).isSuccess)
        assertEquals(preferences, repository.observe("user-a").first())
    }

    @Test
    fun updateReplacesSavedPreferences() = runTest {
        val repository = makeRepository(this, "update.preferences_pb")
        repository.save(samplePreferences("user-a", 90))
        repository.save(samplePreferences("user-a", 210))

        assertEquals(210, repository.observe("user-a").first()?.weeklyGoalMinutes)
    }

    @Test
    fun usersHaveSeparatePreferences() = runTest {
        val repository = makeRepository(this, "users.preferences_pb")
        repository.save(samplePreferences("user-a", 90))
        repository.save(samplePreferences("user-c", 210))

        assertEquals(90, repository.observe("user-a").first()?.weeklyGoalMinutes)
        assertEquals(210, repository.observe("user-c").first()?.weeklyGoalMinutes)
        assertNull(repository.observe("new-user").first())
    }

    private fun makeRepository(scope: TestScope, fileName: String): DataStorePreferencesRepository {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = TestScope(scope.testScheduler + UnconfinedTestDispatcher(scope.testScheduler)),
            produceFile = { File(folder.root, fileName) }
        )
        return DataStorePreferencesRepository(dataStore)
    }

    private fun samplePreferences(userId: String, weeklyMinutes: Int): UserPreferences {
        return UserPreferences(
            userId = userId,
            goal = "Stay Active",
            preferredIntensity = Intensity.MEDIUM,
            weeklyGoalMinutes = weeklyMinutes,
            activityNames = listOf("Walking", "Swimming"),
            environment = Environment.ANY
        )
    }
}
