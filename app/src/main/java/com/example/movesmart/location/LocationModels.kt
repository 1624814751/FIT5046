package com.example.movesmart.location

import com.example.movesmart.shared.GeoLocation

fun GeoLocation.hasValidCoordinates(): Boolean = label.isNotBlank() &&
    latitude.isFinite() && latitude in -90.0..90.0 &&
    longitude.isFinite() && longitude in -180.0..180.0

enum class LocationProblem {
    PERMISSION_DENIED, SERVICES_DISABLED, TIMEOUT, UNAVAILABLE, INVALID_RESULT, NETWORK, SERVICE, INVALID_INPUT
}

class LocationFailure(val problem: LocationProblem, message: String) : Exception(message)

fun interface CurrentLocationProvider { suspend fun locate(): GeoLocation }
fun interface LocationSearch { suspend fun search(input: String): List<LocationMatch> }

data class LocationMatch(val location: GeoLocation, val source: String)

/** Reject old, future-dated or unusably inaccurate fixes, including framework cache hits. */
internal fun validFix(location: GeoLocation, ageMillis: Long, accuracyMetres: Float): Boolean =
    location.hasValidCoordinates() && ageMillis in 0..120_000 &&
        accuracyMetres.isFinite() && accuracyMetres > 0 && accuracyMetres <= 10_000
