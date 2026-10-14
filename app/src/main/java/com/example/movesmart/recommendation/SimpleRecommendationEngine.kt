package com.example.movesmart.recommendation

import com.example.movesmart.shared.*
import java.time.Duration
import java.util.UUID

// Recommendations: Suggest suitable activities
class SimpleRecommendationEngine : RecommendationEngine {
    override fun evaluate(input: ContextInput): ContextDecision {
        val plan = input.plan
        require(plan.availableMinutes > 0) { "Choose at least one minute for your activity." }
        require(input.preferences.userId.isNotBlank()) { "A user ID is required." }
        require(plan.location.label.isNotBlank() &&
            plan.location.latitude.isFinite() && plan.location.latitude in -90.0..90.0 &&
            plan.location.longitude.isFinite() && plan.location.longitude in -180.0..180.0) {
            "Choose a valid activity location."
        }
        val reasons = mutableListOf<String>()
        val environment = checkEnvironment(input, reasons)
        checkIntensity(input, reasons)
        val activity = getActivity(input, environment)
        val minutes = minOf(plan.availableMinutes, activity.maxMinutes, getGoalMinutes(input, reasons))
            .coerceAtLeast(activity.minMinutes)
        reasons.add("You have ${plan.availableMinutes} minutes, and this $minutes-minute activity fits your time.")
        if (input.preferences.activityNames.isNotEmpty()) {
            reasons.add("This activity is in your preferred activities.")
        }
        val recommendation = Recommendation(
            id = UUID.nameUUIDFromBytes(input.toString().toByteArray(Charsets.UTF_8)).toString(),
            activityName = activity.name,
            minutes = minutes,
            intensity = activity.intensity,
            environment = activity.environment,
            plannedDate = plan.date,
            location = plan.location,
            reasons = reasons
        )
        return ContextDecision(recommendation, checkSafety(input.motion))
    }

    private fun checkIntensity(input: ContextInput, reasons: MutableList<String>) {
        if (input.plan.intensity == input.preferences.preferredIntensity) {
            reasons.add("This activity matches your preferred intensity.")
        } else {
            reasons.add("This activity uses the intensity in your plan instead of your saved intensity.")
        }
    }

    private fun checkEnvironment(input: ContextInput, reasons: MutableList<String>): Environment {
        var environment = input.plan.environment
        val saved = input.preferences.environment
        if (environment == Environment.ANY) {
            environment = saved
        } else if (saved != Environment.ANY && environment != saved) {
            reasons.add("Your plan's environment is used instead of your saved environment.")
        }
        val weather = getWeather(input)
        val isRainy = (weather?.rainProbabilityPercent ?: 0) >= 60
        val isWindy = (weather?.windSpeedKmh ?: 0.0) >= 30.0
        val isOld = weather != null && (weather.isStale ||
            weather.fetchedAt > input.evaluatedAt ||
            Duration.between(weather.fetchedAt, input.evaluatedAt) >= Duration.ofHours(1))
        when {
            weather == null -> reasons.add("Weather information is unavailable for this plan.")
            isOld -> reasons.add("Weather information is old. Check the forecast before going outside.")
            weather.source == DataSource.CACHE -> reasons.add("This plan uses saved weather information.")
        }
        if (weather != null && (weather.rainProbabilityPercent == null || weather.windSpeedKmh == null)) {
            reasons.add("Some rain or wind information is unavailable. Check the forecast before going outside.")
        }
        if (isRainy) reasons.add("Rain probability is at least 60%, so an indoor activity is better.")
        if (isWindy) reasons.add("Wind speed is at least 30 km/h, so an indoor activity is better.")
        if (isRainy || isWindy) {
            if (environment == Environment.OUTDOOR) {
                reasons.add("An indoor activity is suggested instead of your outdoor choice because of the weather.")
            }
            return Environment.INDOOR
        }
        if (environment != Environment.ANY) {
            reasons.add(if (environment == Environment.INDOOR) "This activity matches your indoor choice."
                else "This activity matches your outdoor choice.")
        } else if (weather == null || isOld || weather.rainProbabilityPercent == null || weather.windSpeedKmh == null) {
            reasons.add("An indoor activity is suggested because the weather is not fully known.")
            return Environment.INDOOR
        }
        return environment
    }

    private fun getWeather(input: ContextInput): WeatherContext? {
        val weather = input.weather ?: return null
        if (weather.query != WeatherQuery(input.plan.location, input.plan.date)) return null
        if (weather.source == DataSource.DEMO || !weather.temperatureC.isFinite()) return null
        if (weather.rainProbabilityPercent != null && weather.rainProbabilityPercent !in 0..100) return null
        if (weather.windSpeedKmh != null && (!weather.windSpeedKmh.isFinite() || weather.windSpeedKmh < 0)) return null
        return weather
    }

    private fun getActivity(input: ContextInput, environment: Environment): Activity {
        val choices = activities.filter { activity ->
            activity.intensity == input.plan.intensity &&
                activity.minMinutes <= input.plan.availableMinutes &&
                (environment == Environment.ANY || activity.environment == environment)
        }
        val names = input.preferences.activityNames
        if (names.isNotEmpty()) {
            for (name in names) {
                val activity = choices.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
                if (activity != null) return activity
            }
            throw IllegalArgumentException("No preferred activity fits this time, intensity and environment. Change your plan or preferred activities.")
        }
        return choices.firstOrNull()
            ?: throw IllegalArgumentException("No activity fits this time, intensity and environment. Change your plan.")
    }

    private fun getGoalMinutes(input: ContextInput, reasons: MutableList<String>): Int {
        return when (input.preferences.goal) {
            "Stay Active" -> {
                reasons.add("Your Stay Active goal sets a target of up to 30 minutes, within the activity's time range.")
                30
            }
            "Improve Fitness" -> {
                reasons.add("Your Improve Fitness goal sets a target of up to 45 minutes, within the activity's time range.")
                45
            }
            "Exercise More Regularly" -> {
                reasons.add("Your Exercise More Regularly goal sets a short target of 15 minutes, within the activity's time range.")
                15
            }
            else -> input.plan.availableMinutes
        }
    }

    private fun checkSafety(motion: MotionContext?): String? {
        return if (motion?.movementBand == MovementBand.HIGH) {
            "Your movement is high. Consider taking a short break."
        } else null
    }

    private data class Activity(
        val name: String,
        val intensity: Intensity,
        val environment: Environment,
        val minMinutes: Int,
        val maxMinutes: Int
    )

    private val activities = listOf(
        Activity("Walking", Intensity.LOW, Environment.OUTDOOR, 5, 45),
        Activity("Yoga", Intensity.LOW, Environment.INDOOR, 5, 30),
        Activity("Swimming", Intensity.MEDIUM, Environment.INDOOR, 20, 60),
        Activity("Badminton", Intensity.MEDIUM, Environment.INDOOR, 15, 60),
        Activity("Hiking", Intensity.MEDIUM, Environment.OUTDOOR, 30, 60),
        Activity("Gym", Intensity.MEDIUM, Environment.INDOOR, 15, 60),
        Activity("Home Workout", Intensity.MEDIUM, Environment.INDOOR, 5, 30),
        Activity("Jogging", Intensity.HIGH, Environment.OUTDOOR, 10, 45),
        Activity("Cycling", Intensity.HIGH, Environment.OUTDOOR, 15, 60),
        Activity("Gym", Intensity.HIGH, Environment.INDOOR, 15, 60),
        Activity("Home Workout", Intensity.HIGH, Environment.INDOOR, 5, 30)
    )
}
