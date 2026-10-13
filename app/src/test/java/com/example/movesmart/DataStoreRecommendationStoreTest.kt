package com.example.movesmart

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.movesmart.data.local.DataStoreRecommendationStore
import com.example.movesmart.recommendation.SimpleRecommendationEngine
import com.example.movesmart.shared.Environment
import com.example.movesmart.shared.Recommendation
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DataStoreRecommendationStoreTest {
    @get:Rule val folder = TemporaryFolder()
    private val jobs = mutableListOf<Job>()
    private val recommendation = SimpleRecommendationEngine().evaluate(recommendationInput()).recommendation

    @After fun stopStores() = runBlocking {
        for (job in jobs) job.cancelAndJoin()
    }

    @Test fun saveAndLoadAllFields() = runTest {
        val store = DataStoreRecommendationStore(makeDataStore(), "user-a")
        assertTrue(store.save(recommendation).isSuccess)
        assertEquals(recommendation, store.observe(recommendation.id).first())
    }

    @Test fun missingAndBlankIdsReturnNull() = runTest {
        val store = DataStoreRecommendationStore(makeDataStore(), "user-a")
        for (id in listOf("", " ", "missing")) assertNull(store.observe(id).first())
    }

    @Test fun sameIdUpdatesSavedRecommendation() = runTest {
        val store = DataStoreRecommendationStore(makeDataStore(), "user-a")
        store.save(recommendation).getOrThrow()
        val changed = recommendation.copy(minutes = 15, reasons = listOf("A shorter activity fits your time."))
        store.save(changed).getOrThrow()
        assertEquals(changed, store.observe(changed.id).first())
    }

    @Test fun separateIdsKeepBothRecommendations() = runTest {
        val store = DataStoreRecommendationStore(makeDataStore(), "user-a")
        val other = recommendation.copy(id = "another-id")
        store.save(recommendation).getOrThrow()
        store.save(other).getOrThrow()
        assertEquals(recommendation, store.observe(recommendation.id).first())
        assertEquals(other, store.observe(other.id).first())
    }

    @Test fun observerReceivesSaveFromAnotherStore() = runTest {
        val dataStore = makeDataStore()
        val reader = DataStoreRecommendationStore(dataStore, "user-a")
        val writer = DataStoreRecommendationStore(dataStore, "user-a")
        val changes = Channel<Recommendation?>(Channel.UNLIMITED)
        val job = launch { reader.observe(recommendation.id).collect { changes.send(it) } }
        assertNull(changes.receive())
        writer.save(recommendation).getOrThrow()
        assertEquals(recommendation, changes.receive())
        job.cancelAndJoin()
        changes.close()
    }

    @Test fun usersCannotReadEachOthersRecommendations() = runTest {
        val dataStore = makeDataStore()
        val first = DataStoreRecommendationStore(dataStore, "user-a")
        val second = DataStoreRecommendationStore(dataStore, "user-b")
        first.save(recommendation).getOrThrow()
        assertNull(second.observe(recommendation.id).first())
        val other = recommendation.copy(minutes = 10)
        second.save(other).getOrThrow()
        assertEquals(recommendation, first.observe(recommendation.id).first())
        assertEquals(other, second.observe(recommendation.id).first())
    }

    @Test fun userNamesAndIdsCannotOverlapKeys() = runTest {
        val dataStore = makeDataStore()
        val first = DataStoreRecommendationStore(dataStore, "a")
        val second = DataStoreRecommendationStore(dataStore, "a.b")
        first.save(recommendation.copy(id = "b.c")).getOrThrow()
        assertNull(second.observe("c").first())
    }

    @Test fun savedDataSurvivesDataStoreRecreation() = runTest {
        val first = DataStoreRecommendationStore(makeDataStore(), "user-a")
        first.save(recommendation).getOrThrow()
        jobs.last().cancelAndJoin()
        val restored = DataStoreRecommendationStore(makeDataStore(), "user-a")
        assertEquals(recommendation, restored.observe(recommendation.id).first())
    }

    @Test fun invalidRecommendationsFailWithoutReplacingSavedData() = runTest {
        val store = DataStoreRecommendationStore(makeDataStore(), "user-a")
        store.save(recommendation).getOrThrow()
        for (invalid in listOf(recommendation.copy(id = " "), recommendation.copy(minutes = 0),
            recommendation.copy(activityName = ""), recommendation.copy(reasons = emptyList()),
            recommendation.copy(reasons = listOf("")), recommendation.copy(environment = Environment.ANY),
            recommendation.copy(location = recommendation.location.copy(longitude = Double.NaN)))) {
            assertTrue(store.save(invalid).isFailure)
        }
        assertEquals(recommendation, store.observe(recommendation.id).first())
    }

    @Test fun brokenJsonReturnsNull() = runTest {
        val dataStore = makeDataStore()
        val store = DataStoreRecommendationStore(dataStore, "user-a")
        dataStore.edit { it[savedKey()] = "broken" }
        assertNull(store.observe(recommendation.id).first())
    }

    @Test fun wrongSavedIdAndInvalidSavedValuesReturnNull() = runTest {
        val dataStore = makeDataStore()
        val store = DataStoreRecommendationStore(dataStore, "user-a")
        store.save(recommendation).getOrThrow()
        val value = dataStore.data.first()[savedKey()]!!
        val wrongId = org.json.JSONObject(value).put("id", "different").toString()
        dataStore.edit { it[savedKey()] = wrongId }
        assertNull(store.observe(recommendation.id).first())
        val wrongMinutes = org.json.JSONObject(value).put("minutes", -1).toString()
        dataStore.edit { it[savedKey()] = wrongMinutes }
        assertNull(store.observe(recommendation.id).first())
    }

    @Test fun textAndReasonOrderSurviveStorage() = runTest {
        val store = DataStoreRecommendationStore(makeDataStore(), "user-a")
        val value = recommendation.copy(activityName = "Yoga \"Home\"", reasons = listOf("First\nreason", "第二条理由"))
        store.save(value).getOrThrow()
        assertEquals(value, store.observe(value.id).first())
    }

    @Test fun emptyUserIdIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            DataStoreRecommendationStore(makeDataStore(), " ")
        }
    }

    @Test fun writeFailureIsReturned() = runTest {
        val error = IOException("Device storage is unavailable.")
        val store = DataStoreRecommendationStore(FailedDataStore(error), "user-a")
        assertSame(error, store.save(recommendation).exceptionOrNull())
    }

    @Test fun cancellationIsNotHidden() = runTest {
        val store = DataStoreRecommendationStore(FailedDataStore(CancellationException()), "user-a")
        try {
            store.save(recommendation)
            fail("Cancellation must be thrown.")
        } catch (_: CancellationException) {
            Unit
        }
    }

    @Test fun readFailureIsNotReportedAsMissingData() = runTest {
        val error = IOException("Device storage is unavailable.")
        val dataStore = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { throw error }
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = emptyPreferences()
        }
        val store = DataStoreRecommendationStore(dataStore, "user-a")
        try {
            store.observe(recommendation.id).first()
            fail("Read failure must be thrown.")
        } catch (caught: IOException) {
            assertSame(error, caught)
        }
    }

    private fun makeDataStore(): DataStore<Preferences> {
        val job = SupervisorJob()
        jobs.add(job)
        return PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + job),
            produceFile = { File(folder.root, "recommendations.preferences_pb") }
        )
    }

    private fun savedKey(): Preferences.Key<String> {
        val user = Base64.getUrlEncoder().withoutPadding().encodeToString("user-a".toByteArray())
        return stringPreferencesKey("$user.${recommendation.id}")
    }

    private class FailedDataStore(private val error: Exception) : DataStore<Preferences> {
        override val data: Flow<Preferences> = flowOf(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = throw error
    }
}
