package com.example.movesmart

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.movesmart.sensor.AndroidSensorAssetReader
import com.example.movesmart.sensor.CsvSensorRepository
import com.example.movesmart.sensor.SamplePacer
import com.example.movesmart.sensor.SensorAssetReader
import com.example.movesmart.sensor.SensorDatasets
import com.example.movesmart.shared.DataSource
import com.example.movesmart.shared.MovementBand
import java.io.StringReader
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CsvSensorRepositoryTest {
    @Test
    fun replaysValidatedRowsAtTwentyMillisecondsAndLoopsMonotonically() = runTest {
        val waits = mutableListOf<Long>()
        val repository = repository(
            validCsv(
                "0,0.10,0.20,0.90,0.01,0.02,0.03,STANDING",
                "20,0.40,0.50,0.60,0.04,0.05,0.06,WALKING"
            ),
            SamplePacer { waits += it }
        )

        val samples = repository.playback(SensorDatasets.UCI_HAPT_SUBSET_ID).take(4).toList()

        assertEquals(listOf(0L, 1L, 2L, 3L), samples.map { it.sampleIndex })
        assertEquals(listOf(0L, 20L, 40L, 60L), samples.map { it.elapsedMilliseconds })
        assertEquals(listOf("STANDING", "WALKING", "STANDING", "WALKING"), samples.map { it.activityLabel })
        assertEquals(listOf(0.10, 0.20, 0.90), samples.first().accelerometer)
        assertEquals(listOf(0.01, 0.02, 0.03), samples.first().gyroscope)
        assertEquals("g", samples.first().accelerometerUnit)
        assertEquals("rad/s", samples.first().gyroscopeUnit)
        assertEquals(DataSource.DEMO, samples.first().source)
        assertEquals(SensorDatasets.UCI_HAPT_SUBSET_ID, samples.first().datasetId)
        assertEquals(listOf(20L, 20L, 20L), waits)
    }

    @Test
    fun eachCollectionRestartsTheColdFlow() = runTest {
        val repository = repository(validCsv("0,0,0,1,0,0,0,STANDING"))

        assertEquals(0L, repository.playback(SensorDatasets.UCI_HAPT_SUBSET_ID).first().sampleIndex)
        assertEquals(0L, repository.playback(SensorDatasets.UCI_HAPT_SUBSET_ID).first().sampleIndex)
    }

    @Test
    fun committedAssetProducesLowModerateAndHighWindows() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = CsvSensorRepository(
            assetReader = AndroidSensorAssetReader(context),
            pacer = SamplePacer { }
        )

        val samples = repository.playback(SensorDatasets.UCI_HAPT_SUBSET_ID).take(150).toList()

        assertEquals(MovementBand.LOW, samples[49].movementBand)
        assertEquals(MovementBand.MODERATE, samples[99].movementBand)
        assertEquals(MovementBand.HIGH, samples[149].movementBand)
        assertEquals(setOf("STANDING", "WALKING", "WALKING_DOWNSTAIRS"), samples.mapNotNull { it.activityLabel }.toSet())
    }

    @Test
    fun rejectsUnknownDatasetBeforeOpeningAnAsset() = runTest {
        var opened = false
        val repository = CsvSensorRepository(
            assetReader = SensorAssetReader { opened = true; StringReader("") },
            pacer = SamplePacer { }
        )

        val failure = runCatching { repository.playback("unknown").first() }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message.orEmpty().contains("unknown"))
        assertFalse(opened)
    }

    @Test
    fun rejectsWrongHeaderAndEmptyData() = runTest {
        assertPlaybackFailure("time,x\n0,1", "header")
        assertPlaybackFailure(CsvSensorRepository.CSV_HEADER + "\n", "no sensor rows")
    }

    @Test
    fun rejectsMalformedNonFiniteAndMisorderedRowsWithLineNumber() = runTest {
        assertPlaybackFailure(validCsv("0,0,0,1,0,0,STANDING"), "row 2")
        assertPlaybackFailure(validCsv("0,NaN,0,1,0,0,0,STANDING"), "row 2")
        assertPlaybackFailure(
            validCsv(
                "0,0,0,1,0,0,0,STANDING",
                "40,0,0,1,0,0,0,STANDING"
            ),
            "20 ms"
        )
    }

    private fun repository(csv: String, pacer: SamplePacer = SamplePacer { }) =
        CsvSensorRepository(
            assetReader = SensorAssetReader { StringReader(csv) },
            pacer = pacer
        )

    private suspend fun assertPlaybackFailure(csv: String, expectedMessage: String) {
        val failure = runCatching {
            repository(csv).playback(SensorDatasets.UCI_HAPT_SUBSET_ID).first()
        }.exceptionOrNull()
        assertTrue("Expected playback to fail", failure is IllegalArgumentException)
        assertTrue(
            "Expected '${failure?.message}' to contain '$expectedMessage'",
            failure?.message.orEmpty().contains(expectedMessage, ignoreCase = true)
        )
    }

    private fun validCsv(vararg rows: String): String =
        (listOf(CsvSensorRepository.CSV_HEADER) + rows).joinToString("\n")
}
