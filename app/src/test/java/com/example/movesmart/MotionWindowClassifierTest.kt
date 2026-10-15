package com.example.movesmart

import com.example.movesmart.sensor.MotionWindowClassifier
import com.example.movesmart.shared.DataSource
import com.example.movesmart.shared.MotionContext
import com.example.movesmart.shared.MovementBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionWindowClassifierTest {
    @Test
    fun reportsUnknownOnlyDuringNumericWarmupThenUsesMeasuredValues() {
        val classifier = MotionWindowClassifier(windowSize = 50, minimumSamples = 5)

        repeat(4) {
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
    fun activityLabelChangeDoesNotResetTheMeasuredRollingWindow() {
        val classifier = MotionWindowClassifier(windowSize = 5, minimumSamples = 2)
        assertEquals(MovementBand.UNKNOWN, classifier.add(sample(az = 1.0, label = "STANDING")))
        assertEquals(MovementBand.LOW, classifier.add(sample(az = 1.0, label = "STANDING")))

        val firstChangedLabel = classifier.add(sample(ax = 1.80, label = "WALKING"))
        assertTrue(firstChangedLabel != MovementBand.UNKNOWN)
        assertEquals(MovementBand.HIGH, classifier.add(sample(ax = 1.80, label = "WALKING")))
    }

    @Test
    fun activityLabelsNeverOverrideSensorMeasurements() {
        val lowClassifier = MotionWindowClassifier(windowSize = 2, minimumSamples = 2)
        lowClassifier.add(sample(az = 1.0, label = "RUNNING"))
        assertEquals(MovementBand.LOW, lowClassifier.add(sample(az = 1.0, label = "RUNNING")))

        val highClassifier = MotionWindowClassifier(windowSize = 2, minimumSamples = 2)
        highClassifier.add(sample(ax = 1.80, label = "STANDING"))
        assertEquals(MovementBand.HIGH, highClassifier.add(sample(ax = 1.80, label = "STANDING")))
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
