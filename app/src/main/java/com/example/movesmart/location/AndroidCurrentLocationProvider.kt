package com.example.movesmart.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.location.LocationRequest
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.example.movesmart.shared.GeoLocation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One foreground fix only. Cancellation unregisters the framework request. */
class AndroidCurrentLocationProvider(context: Context) : CurrentLocationProvider {
    private val app = context.applicationContext

    override suspend fun locate(): GeoLocation {
        val fine = ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) throw LocationFailure(LocationProblem.PERMISSION_DENIED,
            "Location permission denied. Retry to grant access, enable it in app settings, or enter a suburb/postcode.")
        val manager = app.getSystemService(LocationManager::class.java)
            ?: throw LocationFailure(LocationProblem.UNAVAILABLE, "Device location is unavailable. Retry or enter a suburb/postcode.")
        if (!manager.isLocationEnabled) throw LocationFailure(LocationProblem.SERVICES_DISABLED,
            "Location services are off. Turn them on and retry, or enter a suburb/postcode.")
        val providers = if (fine) listOf(LocationManager.FUSED_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            else listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER)
        val provider = providers.firstOrNull { manager.hasProvider(it) && manager.isProviderEnabled(it) }
            ?: throw LocationFailure(LocationProblem.UNAVAILABLE, "No location provider is available. Retry or enter a suburb/postcode.")
        return suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            try {
                val request = LocationRequest.Builder(0)
                    .setQuality(if (fine) LocationRequest.QUALITY_HIGH_ACCURACY else LocationRequest.QUALITY_BALANCED_POWER_ACCURACY)
                    .setDurationMillis(30_000).setMaxUpdates(1).build()
                manager.getCurrentLocation(provider, request, signal, ContextCompat.getMainExecutor(app)) { fix ->
                    if (continuation.isActive) {
                        val location = fix?.let { GeoLocation(if (fine) "Current location" else "Current location (approximate)", it.latitude, it.longitude) }
                        val age = fix?.let { (SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos) / 1_000_000 } ?: -1
                        if (location != null && fix.hasAccuracy() && validFix(location, age, fix.accuracy)) {
                            continuation.resume(location)
                        } else continuation.resumeWithException(LocationFailure(LocationProblem.UNAVAILABLE,
                            "Could not obtain a recent, usable location fix. Retry outdoors or enter a suburb/postcode."))
                    }
                }
            } catch (_: SecurityException) {
                if (continuation.isActive) continuation.resumeWithException(LocationFailure(LocationProblem.PERMISSION_DENIED,
                    "Location permission denied. Retry to grant access, enable it in app settings, or enter a suburb/postcode."))
            } catch (_: Exception) {
                if (continuation.isActive) continuation.resumeWithException(LocationFailure(LocationProblem.UNAVAILABLE,
                    "Device location retrieval failed. Retry or enter a suburb/postcode."))
            }
        }
    }
}
