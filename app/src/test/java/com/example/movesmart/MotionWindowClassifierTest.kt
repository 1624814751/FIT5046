package com.example.movesmart

import com.example.movesmart.sensor.MotionWindowClassifier
import com.example.movesmart.shared.DataSource
import com.example.movesmart.shared.MotionContext
import com.example.movesmart.shared.MovementBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MotionWindowClassifierTest {
    @Test
    fun reportsUnknownUntilAFullSameLabelWindowThenLow() {
        val classifier = MotionWindowClassifier(windowSize = 50)

        repeat(49) {
            assertEquals(MovementBand.UNKNOWN, classifier.add(sample(az = 1.0)))
        }

        assertEquals(MovementBand.LOW, classifier.add(sample(az = 1.0)))
        assertEquals(MovementBand.LOW, classifier.currentBand)
    }

    @Test
    fun classifiesModerateWhenEitherModerateThresholdIsReached() {
        val accelerationClassifier = MotionWindowClassifier(windowSize = 50)
        repeat(49) { accelerationClassifier.add(sample(ax = 1.08)) }
        assertEquals(MovementBand.MODERATE, accelerationClassifier.add(sample(ax = 1.08)))

        val gyroscopeClassifier = MotionWindowClassifier(windowSize = 50)
        repeat(49) { gyroscopeClassifier.add(sample(az = 1.0, gx = 0.30)) }
        assertEquals(MovementBand.MODERATE, gyroscopeClassifier.add(sample(az = 1.0, gx = 0.30)))
    }

    @Test
    fun classifiesHighWhenEitherHighThresholdIsReached() {
        val accelerationClassifier = MotionWindowClassifier(windowSize = 50)
        repeat(49) { accelerationClassifier.add(sample(ax = 1.30)) }
        assertEquals(MovementBand.HIGH, accelerationClassifier.add(sample(ax = 1.30)))

        val gyroscopeClassifier = MotionWindowClassifier(windowSize = 50)
        repeat(49) { gyroscopeClassifier.add(sample(az = 1.0, gx = 1.50)) }
        assertEquals(MovementBand.HIGH, gyroscopeClassifier.add(sample(az = 1.0, gx = 1.50)))
    }

    @Test
    fun resetsTheWindowWhenDatasetActivityLabelChanges() {
        val classifier = MotionWindowClassifier(windowSize = 50)
        repeat(49) { classifier.add(sample(az = 1.0, label = "STANDING")) }

        assertEquals(MovementBand.UNKNOWN, classifier.add(sample(ax = 1.20, label = "WALKING")))
        repeat(48) { classifier.add(sample(ax = 1.20, label = "WALKING")) }
        assertEquals(MovementBand.MODERATE, classifier.add(sample(ax = 1.20, label = "WALKING")))
    }

    @Test
    fun rejectsInvalidAxesAndNonFiniteReadings() {
        val classifier = MotionWindowClassifier(windowSize = 50)

        assertThrows(IllegalArgumentException::class.java) {
            classifier.add(sample().copy(accelerometer = listOf(1.0, 2.0)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            classifier.add(sample().copy(gyroscope = listOf(0.0, Double.NaN, 0.0)))
        }
    }

    private fun sample(
        ax: Double = 0.0,
        ay: Double = 0.0,
        az: Double = 0.0,
        gx: Double = 0.0,
        gy: Double = 0.0,
        gz: Double = 0.0,
        label: String = "STANDING"
    ) = MotionContext(
        datasetId = "test-dataset",
        sampleIndex = 0,
        elapsedMilliseconds = 0,
        accelerometer = listOf(ax, ay, az),
        gyroscope = listOf(gx, gy, gz),
        accelerometerUnit = "g",
        gyroscopeUnit = "rad/s",
        activityLabel = label,
        movementBand = MovementBand.UNKNOWN,
        source = DataSource.DEMO
    )
}
