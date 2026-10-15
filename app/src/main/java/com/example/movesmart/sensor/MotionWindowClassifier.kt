package com.example.movesmart.sensor

import com.example.movesmart.shared.MotionContext
import com.example.movesmart.shared.MovementBand
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Transparent demonstration rule over one-second UCI sensor windows.
 * It is not a trained activity-recognition or medical model.
 */
class MotionWindowClassifier(
    private val windowSize: Int = DEFAULT_WINDOW_SIZE,
    private val minimumSamples: Int = minOf(DEFAULT_MINIMUM_SAMPLES, windowSize)
) {
    init {
        require(windowSize > 0) { "Window size must be positive." }
        require(minimumSamples in 1..windowSize) {
            "Minimum samples must be between one and the window size."
        }
    }

    var currentBand: MovementBand = MovementBand.UNKNOWN
        private set

    private val accelerationDeviations = ArrayDeque<Double>()
    private val gyroscopeMagnitudes = ArrayDeque<Double>()
    private var accelerationDeviationSum = 0.0
    private var gyroscopeMagnitudeSum = 0.0

    fun add(context: MotionContext): MovementBand {
        requireAxes(context.accelerometer, "Accelerometer")
        requireAxes(context.gyroscope, "Gyroscope")

        val accelerationDeviation = abs(magnitude(context.accelerometer) - EARTH_GRAVITY_G)
        val gyroscopeMagnitude = magnitude(context.gyroscope)
        accelerationDeviations.addLast(accelerationDeviation)
        gyroscopeMagnitudes.addLast(gyroscopeMagnitude)
        accelerationDeviationSum += accelerationDeviation
        gyroscopeMagnitudeSum += gyroscopeMagnitude

        if (accelerationDeviations.size > windowSize) {
            accelerationDeviationSum -= accelerationDeviations.removeFirst()
            gyroscopeMagnitudeSum -= gyroscopeMagnitudes.removeFirst()
        }

        currentBand = if (accelerationDeviations.size < minimumSamples) {
            MovementBand.UNKNOWN
        } else {
            classify(
                meanAccelerationDeviation = accelerationDeviationSum / accelerationDeviations.size,
                meanGyroscopeMagnitude = gyroscopeMagnitudeSum / gyroscopeMagnitudes.size
            )
        }

        return currentBand
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
        private const val DEFAULT_MINIMUM_SAMPLES = 5
        private const val EARTH_GRAVITY_G = 1.0
        private const val EPSILON = 1e-9
    }
}
