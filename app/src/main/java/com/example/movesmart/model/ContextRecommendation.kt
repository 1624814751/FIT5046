package com.example.movesmart.model

/** Deterministic A2 context demonstration. No live weather, sensor or medical assessment. */
data class ContextRecommendation(val title: String, val minutes: Int, val weatherReason: String, val preferenceReason: String)

fun contextRecommendation(availableMinutes: Int, intensity: String, location: String, preference: String, rainy: Boolean): ContextRecommendation {
    val indoor = rainy || preference == "Indoor" || location == "Home"
    val title = if (indoor) "Indoor Mobility" else if (intensity == "High") "Outdoor Jogging" else "Outdoor Walk"
    return ContextRecommendation(title, availableMinutes.coerceIn(1, 45),
        if (rainy) "The sample forecast is rainy, so an indoor option is suggested." else "The sample forecast is clear and mild.",
        when {
            rainy && preference == "Outdoor" -> "An indoor alternative is suggested despite your outdoor preference because of the sample rain."
            location == "Home" -> "This option can be done at your selected location: Home."
            indoor -> "This matches your indoor activity preference."
            else -> "An outdoor activity matches your selected context."
        })
}
