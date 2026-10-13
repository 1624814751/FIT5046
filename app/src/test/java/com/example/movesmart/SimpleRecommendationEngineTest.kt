package com.example.movesmart

import com.example.movesmart.recommendation.SimpleRecommendationEngine
import com.example.movesmart.shared.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class SimpleRecommendationEngineTest {
    private val engine = SimpleRecommendationEngine()
    private val input = recommendationInput()

    @Test fun highRainUsesIndoor() {
        val result = engine.evaluate(input.copy(weather = input.weather!!.copy(rainProbabilityPercent = 60)))
        assertEquals(Environment.INDOOR, result.recommendation.environment)
        assertTrue(result.recommendation.reasons.any { it.contains("at least 60%") })
    }

    @Test fun lowRainAllowsOutdoor() {
        val result = engine.evaluate(input.copy(weather = input.weather!!.copy(rainProbabilityPercent = 59)))
        assertEquals("Walking", result.recommendation.activityName)
        assertFalse(result.recommendation.reasons.any { it.contains("Rain probability") })
    }

    @Test fun strongWindUsesIndoor() {
        val result = engine.evaluate(input.copy(weather = input.weather!!.copy(windSpeedKmh = 30.0)))
        assertEquals(Environment.INDOOR, result.recommendation.environment)
        assertTrue(result.recommendation.reasons.any { it.contains("30 km/h") })
    }

    @Test fun windBelowLimitAllowsOutdoor() {
        val result = engine.evaluate(input.copy(weather = input.weather!!.copy(windSpeedKmh = 29.9)))
        assertEquals(Environment.OUTDOOR, result.recommendation.environment)
    }

    @Test fun shortTimeFits() {
        val result = engine.evaluate(input.copy(plan = input.plan.copy(availableMinutes = 15)))
        assertEquals(15, result.recommendation.minutes)
    }

    @Test fun mediumShortTimeUsesShortActivity() {
        val result = engine.evaluate(input.copy(plan = input.plan.copy(availableMinutes = 5, intensity = Intensity.MEDIUM)))
        assertEquals("Home Workout", result.recommendation.activityName)
        assertEquals(5, result.recommendation.minutes)
    }

    @Test fun lowIntensityMatches() {
        val result = engine.evaluate(input).recommendation
        assertEquals(Intensity.LOW, result.intensity)
        assertEquals("Walking", result.activityName)
        assertTrue(result.reasons.contains("This activity matches your preferred intensity."))
    }

    @Test fun mediumIntensityMatches() {
        val result = engine.evaluate(input.copy(plan = input.plan.copy(intensity = Intensity.MEDIUM),
            preferences = input.preferences.copy(preferredIntensity = Intensity.MEDIUM))).recommendation
        assertEquals("Swimming", result.activityName)
        assertEquals(Intensity.MEDIUM, result.intensity)
    }

    @Test fun highIntensityMatches() {
        val result = engine.evaluate(input.copy(plan = input.plan.copy(intensity = Intensity.HIGH),
            preferences = input.preferences.copy(preferredIntensity = Intensity.HIGH))).recommendation
        assertEquals("Jogging", result.activityName)
        assertEquals(Intensity.HIGH, result.intensity)
    }

    @Test fun planIntensityOverridesSavedIntensityWithReason() {
        val result = engine.evaluate(input.copy(plan = input.plan.copy(intensity = Intensity.HIGH))).recommendation
        assertEquals(Intensity.HIGH, result.intensity)
        assertTrue(result.reasons.any { it.contains("instead of your saved intensity") })
        assertFalse(result.reasons.contains("This activity matches your preferred intensity."))
    }

    @Test fun indoorPreferenceMatches() {
        val result = engine.evaluate(input.copy(preferences = input.preferences.copy(environment = Environment.INDOOR)))
        assertEquals("Yoga", result.recommendation.activityName)
    }

    @Test fun outdoorPreferenceMatches() {
        val result = engine.evaluate(input.copy(preferences = input.preferences.copy(environment = Environment.OUTDOOR)))
        assertEquals(Environment.OUTDOOR, result.recommendation.environment)
    }

    @Test fun planEnvironmentOverridesSavedEnvironmentWithReason() {
        val result = engine.evaluate(input.copy(plan = input.plan.copy(environment = Environment.INDOOR),
            preferences = input.preferences.copy(environment = Environment.OUTDOOR))).recommendation
        assertEquals(Environment.INDOOR, result.environment)
        assertTrue(result.reasons.any { it.contains("instead of your saved environment") })
    }

    @Test fun rainyOutdoorChoiceHasClearReason() {
        val result = engine.evaluate(input.copy(plan = input.plan.copy(environment = Environment.OUTDOOR),
            weather = input.weather!!.copy(rainProbabilityPercent = 80))).recommendation
        assertEquals(Environment.INDOOR, result.environment)
        assertTrue(result.reasons.any { it.contains("instead of your outdoor choice") })
        assertFalse(result.reasons.contains("This activity matches your outdoor choice."))
    }

    @Test fun preferredActivityWins() {
        val result = engine.evaluate(input.copy(plan = input.plan.copy(intensity = Intensity.HIGH),
            preferences = input.preferences.copy(activityNames = listOf("Cycling", "Jogging")))).recommendation
        assertEquals("Cycling", result.activityName)
        assertTrue(result.reasons.contains("This activity is in your preferred activities."))
    }

    @Test fun preferredActivityMustFitTime() {
        val result = engine.evaluate(input.copy(plan = input.plan.copy(intensity = Intensity.MEDIUM, availableMinutes = 15),
            preferences = input.preferences.copy(activityNames = listOf("Swimming", "Badminton")))).recommendation
        assertEquals("Badminton", result.activityName)
    }

    @Test fun preferredActivityNamesIgnoreCaseAndSpaces() {
        val result = engine.evaluate(input.copy(preferences = input.preferences.copy(activityNames = listOf(" yoga "))))
        assertEquals("Yoga", result.recommendation.activityName)
    }

    @Test fun noPreferredMatchFailsClearly() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            engine.evaluate(input.copy(preferences = input.preferences.copy(activityNames = listOf("Swimming"))))
        }
        assertTrue(error.message!!.contains("No preferred activity fits"))
    }

    @Test fun rainDoesNotReplaceStrictActivitiesWithUnrelatedActivity() {
        assertThrows(IllegalArgumentException::class.java) {
            engine.evaluate(input.copy(weather = input.weather!!.copy(rainProbabilityPercent = 80),
                preferences = input.preferences.copy(activityNames = listOf("Walking"))))
        }
    }

    @Test fun tooLittleTimeFailsClearly() {
        assertThrows(IllegalArgumentException::class.java) {
            engine.evaluate(input.copy(plan = input.plan.copy(availableMinutes = 4)))
        }
    }

    @Test fun invalidTimeFailsClearly() {
        for (minutes in listOf(0, -1)) {
            assertThrows(IllegalArgumentException::class.java) {
                engine.evaluate(input.copy(plan = input.plan.copy(availableMinutes = minutes)))
            }
        }
    }

    @Test fun missingWeatherUsesIndoorWithoutSunnyClaim() {
        val result = engine.evaluate(input.copy(weather = null)).recommendation
        assertEquals(Environment.INDOOR, result.environment)
        assertTrue(result.reasons.contains("Weather information is unavailable for this plan."))
        assertFalse(result.reasons.any { it.contains("sunny") || it.contains("good weather") })
    }

    @Test fun missingWeatherKeepsExplicitOutdoorChoice() {
        val result = engine.evaluate(input.copy(weather = null, plan = input.plan.copy(environment = Environment.OUTDOOR)))
        assertEquals(Environment.OUTDOOR, result.recommendation.environment)
        assertTrue(result.recommendation.reasons.any { it.contains("unavailable") })
    }

    @Test fun cachedWeatherIsExplained() {
        val result = engine.evaluate(input.copy(weather = input.weather!!.copy(source = DataSource.CACHE)))
        assertTrue(result.recommendation.reasons.contains("This plan uses saved weather information."))
    }

    @Test fun staleWeatherIsExplainedAndUsesIndoor() {
        val result = engine.evaluate(input.copy(weather = input.weather!!.copy(isStale = true))).recommendation
        assertEquals(Environment.INDOOR, result.environment)
        assertTrue(result.reasons.any { it.contains("information is old") })
    }

    @Test fun oldAndFutureFetchTimesAreNotFresh() {
        for (time in listOf(input.evaluatedAt.minusSeconds(3600), input.evaluatedAt.plusSeconds(1))) {
            val result = engine.evaluate(input.copy(weather = input.weather!!.copy(fetchedAt = time)))
            assertTrue(result.recommendation.reasons.any { it.contains("information is old") })
        }
    }

    @Test fun staleRainStillUsesIndoor() {
        val result = engine.evaluate(input.copy(plan = input.plan.copy(environment = Environment.OUTDOOR),
            weather = input.weather!!.copy(isStale = true, rainProbabilityPercent = 90)))
        assertEquals(Environment.INDOOR, result.recommendation.environment)
    }

    @Test fun wrongWeatherDateOrLocationIsUnavailable() {
        val query = input.weather!!.query
        for (other in listOf(query.copy(date = query.date.plusDays(1)),
            query.copy(location = query.location.copy(latitude = -30.0)))) {
            val result = engine.evaluate(input.copy(weather = input.weather.copy(query = other)))
            assertTrue(result.recommendation.reasons.any { it.contains("unavailable for this plan") })
        }
    }

    @Test fun invalidAndDemoWeatherIsUnavailable() {
        for (weather in listOf(input.weather!!.copy(source = DataSource.DEMO),
            input.weather.copy(rainProbabilityPercent = 101), input.weather.copy(windSpeedKmh = -1.0),
            input.weather.copy(temperatureC = Double.NaN))) {
            val result = engine.evaluate(input.copy(weather = weather))
            assertTrue(result.recommendation.reasons.any { it.contains("unavailable for this plan") })
        }
    }

    @Test fun missingRainAndWindAreExplained() {
        val result = engine.evaluate(input.copy(weather = input.weather!!.copy(rainProbabilityPercent = null, windSpeedKmh = null)))
        assertEquals(Environment.INDOOR, result.recommendation.environment)
        assertTrue(result.recommendation.reasons.any { it.contains("Some rain or wind") })
    }

    @Test fun nullMotionWorks() {
        assertNull(engine.evaluate(input).safetyPrompt)
    }

    @Test fun highMovementAddsPromptWithoutChangingActivity() {
        val result = engine.evaluate(input.copy(motion = sampleMotion(MovementBand.HIGH)))
        assertEquals("Your movement is high. Consider taking a short break.", result.safetyPrompt)
        assertEquals(engine.evaluate(input).recommendation.activityName, result.recommendation.activityName)
    }

    @Test fun otherMovementBandsHaveNoPrompt() {
        for (band in listOf(MovementBand.UNKNOWN, MovementBand.LOW, MovementBand.MODERATE)) {
            assertNull(engine.evaluate(input.copy(motion = sampleMotion(band))).safetyPrompt)
        }
    }

    @Test fun sameInputHasSameDecisionAndValidId() {
        val first = engine.evaluate(input)
        assertEquals(first, engine.evaluate(input.copy()))
        assertEquals(first.recommendation.id, UUID.fromString(first.recommendation.id).toString())
        assertNotEquals(first.recommendation.id, engine.evaluate(input.copy(evaluatedAt = input.evaluatedAt.plusSeconds(1))).recommendation.id)
    }

    @Test fun goalsChangeDurationWithinAvailableTime() {
        val longPlan = input.plan.copy(availableMinutes = 60)
        for ((goal, minutes) in listOf("Stay Active" to 30, "Improve Fitness" to 45, "Exercise More Regularly" to 15)) {
            val result = engine.evaluate(input.copy(plan = longPlan, preferences = input.preferences.copy(goal = goal)))
            assertEquals(minutes, result.recommendation.minutes)
            assertTrue(result.recommendation.reasons.any { it.contains(goal) })
        }
    }

    @Test fun unknownGoalHasNoFalseGoalReason() {
        val result = engine.evaluate(input.copy(preferences = input.preferences.copy(goal = "Custom")))
        assertFalse(result.recommendation.reasons.any { it.contains("goal") })
    }

    @Test fun recommendationKeepsDateLocationAndUniqueReasons() {
        val result = engine.evaluate(input).recommendation
        assertEquals(input.plan.date, result.plannedDate)
        assertEquals(input.plan.location, result.location)
        assertEquals(result.reasons.distinct(), result.reasons)
        assertTrue(result.reasons.any { it.contains("fits your time") })
    }

    @Test fun durationAlwaysFitsAcrossChoices() {
        for (intensity in Intensity.entries) {
            for (environment in Environment.entries) {
                for (minutes in listOf(5, 10, 15, 30, 60)) {
                    val result = runCatching { engine.evaluate(input.copy(plan = input.plan.copy(
                        availableMinutes = minutes, intensity = intensity, environment = environment))) }
                    result.onSuccess { assertTrue(it.recommendation.minutes in 1..minutes) }
                    result.onFailure { assertTrue(it is IllegalArgumentException) }
                }
            }
        }
    }

    @Test fun invalidLocationAndMissingUserFail() {
        assertThrows(IllegalArgumentException::class.java) {
            engine.evaluate(input.copy(plan = input.plan.copy(location = input.plan.location.copy(latitude = Double.NaN))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            engine.evaluate(input.copy(preferences = input.preferences.copy(userId = "")))
        }
    }

    private fun sampleMotion(band: MovementBand) = MotionContext(
        "test", 1, 100, emptyList(), emptyList(), "dataset-unit", "dataset-unit", null, band, DataSource.DEMO
    )
}

internal fun recommendationInput(): ContextInput {
    val location = GeoLocation("Melbourne", -37.8136, 144.9631)
    val date = LocalDate.of(2026, 10, 13)
    val now = Instant.parse("2026-10-13T01:00:00Z")
    return ContextInput(
        UserPreferences("user-a", "Stay Active", Intensity.LOW, 150, emptyList(), Environment.ANY),
        PlanningInput(date, 30, Intensity.LOW, Environment.ANY, location),
        WeatherContext(WeatherQuery(location, date), 20.0, 10, 5.0, now, DataSource.LIVE, false),
        null, now
    )
}
