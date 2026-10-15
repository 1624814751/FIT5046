package com.example.movesmart.sensor

import com.example.movesmart.shared.MotionContext
import com.example.movesmart.shared.MovementBand
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Transparent demonstration rule over one-second UCI sensor windows.
 * It is not a trained activity-recognition or medical model.
 */
class MotionWindowClassifier(private val windowSize: Int = DEFAULT_WINDOW_SIZE) {
    init {
        require(windowSize > 0) { "Window size must be positive." }
    }

    var currentBand: MovementBand = MovementBand.UNKNOWN
        private set

    private var currentLabel: String? = null
    private var sampleCount = 0
    private var accelerationDeviationSum = 0.0
    private var gyroscopeMagnitudeSum = 0.0

    fun add(context: MotionContext): MovementBand {
        requireAxes(context.accelerometer, "Accelerometer")
        requireAxes(context.gyroscope, "Gyroscope")

        if (context.activityLabel != currentLabel) {
            resetForLabel(context.activityLabel)
        }

        accelerationDeviationSum += abs(magnitude(context.accelerometer) - EARTH_GRAVITY_G)
        gyroscopeMagnitudeSum += magnitude(context.gyroscope)
        sampleCount += 1

        if (sampleCount == windowSize) {
            currentBand = classify(
                meanAccelerationDeviation = accelerationDeviationSum / windowSize,
                meanGyroscopeMagnitude = gyroscopeMagnitudeSum / windowSize
            )
            sampleCount = 0
            accelerationDeviationSum = 0.0
            gyroscopeMagnitudeSum = 0.0
        }

        return currentBand
    }

    private fun resetForLabel(label: String?) {
        currentLabel = label
        currentBand = provisionalBand(label)
        sampleCount = 0
        accelerationDeviationSum = 0.0
        gyroscopeMagnitudeSum = 0.0
    }

    /**
     * The public dataset already provides an activity label for every sample.
     * Use it only as a short warm-up hint; the measured sensor window replaces
     * this value as soon as enough samples are available.
     */
    private fun provisionalBand(label: String?): MovementBand = when {
        label == null -> MovementBand.UNKNOWN
        label.uppercase().contains("RUNNING") ||
            label.uppercase().contains("JOGGING") -> MovementBand.HIGH
        label.uppercase().contains("WALKING") -> MovementBand.MODERATE
        label.uppercase().contains("STANDING") ||
            label.uppercase().contains("SITTING") ||
            label.uppercase().contains("LAYING") -> MovementBand.LOW
        else -> MovementBand.UNKNOWN
    }

    private fun classify(
        meanAccelerationDeviation: Double,
        meanGyroscopeMagnitude: Double
    ): MovementBand = when {
        meanAccelerationDeviation + EPSILON >= HIGH_ACCELERATION_DEVIATION_G ||
            meanGyroscopeMagnitude + EPSILON >= HIGH_GYROSCOPE_RAD_S -> MovementBand.HIGH
        meanAccelerationDeviation + EPSILON >= MODERATE_ACCELERATION_DEVIATION_G ||
            meanGyroscopeMagnitude + EPSILON >= MODERATE_GYROSCOPE_RAD_S -> MovementBand.MODERATE
        else -> MovementBand.LOW
    }

    private fun requireAxes(values: List<Double>, name: String) {
        require(values.size == AXIS_COUNT) { "$name must contain exactly three axes." }
        require(values.all(Double::isFinite)) { "$name values must be finite." }
    }

    private fun magnitude(values: List<Double>): Double =
        sqrt(values.sumOf { value -> value * value })

    companion object {
        const val DEFAULT_WINDOW_SIZE = 50
        const val MODERATE_ACCELERATION_DEVIATION_G = 0.08
        const val MODERATE_GYROSCOPE_RAD_S = 0.30
        const val HIGH_ACCELERATION_DEVIATION_G = 0.30
        const val HIGH_GYROSCOPE_RAD_S = 1.50

        private const val AXIS_COUNT = 3
        private const val EARTH_GRAVITY_G = 1.0
        private const val EPSILON = 1e-9
    }
}
