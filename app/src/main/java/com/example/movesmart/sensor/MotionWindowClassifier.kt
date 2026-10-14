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
        currentBand = MovementBand.UNKNOWN
        sampleCount = 0
        accelerationDeviationSum = 0.0
        gyroscopeMagnitudeSum = 0.0
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
