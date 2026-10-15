package com.example.movesmart.ui.location

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale

@Composable
fun LocationInput(userId: String, model: LocationViewModel) {
    val observed by model.state.collectAsStateWithLifecycle()
    val state = observed.let { model.forUser(userId) }
    val context = LocalContext.current
    var permissionToken by remember { mutableStateOf<Long?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        permissionToken?.let { model.permissionResult(it,
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) }
        permissionToken = null
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(model, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            // A system permission dialog can pause the activity; stop only once it leaves the foreground.
            if (event == Lifecycle.Event.ON_STOP) model.cancelPending()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); model.cancelPending() }
    }
    fun requestCurrent() {
        val granted = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        val token = model.useCurrent(granted)
        if (!granted) {
            permissionToken = token
            launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Plan location", style = MaterialTheme.typography.titleMedium)
            Button(onClick = ::requestCurrent, enabled = state.status != LocationStatus.Loading && state.status != LocationStatus.Permission) {
                Text("Use Current Location")
            }
            TextButton(onClick = model::useManual) { Text("Enter Suburb or Postcode") }
            if (state.mode == LocationMode.MANUAL) {
                OutlinedTextField(state.query, model::editQuery, label = { Text("Suburb or postcode") },
                    placeholder = { Text("Clayton VIC or 3168") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(model::submitSearch, enabled = state.query.trim().length >= 2 && state.status != LocationStatus.Loading) { Text("Search locations") }
            }
            when (val status = state.status) {
                LocationStatus.Idle -> if (state.selected == null) Text("Choose a verified location for your plan.")
                LocationStatus.Permission -> Text("Waiting for location permission…")
                LocationStatus.Loading -> { CircularProgressIndicator(); Text(if (state.mode == LocationMode.CURRENT) "Finding current location…" else "Searching Australian locations…") }
                is LocationStatus.Error -> {
                    Text(status.message, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { if (state.mode == LocationMode.CURRENT) requestCurrent() else model.submitSearch() }) { Text("Retry location") }
                }
                is LocationStatus.Results -> {
                    Text(if (status.matches.isEmpty()) "No matching Australian location found. Try a suburb and state or a different postcode."
                        else "Select the correct location (${status.matches.size} results).")
                    status.matches.forEach { match ->
                        OutlinedButton({ model.select(match) }, Modifier.fillMaxWidth()) { Text(match.location.label) }
                    }
                    if (status.matches.size >= 100) Text("Refine your search with a state to see fewer results.")
                }
            }
            state.selected?.let { location ->
                Text("Selected: ${location.label}", style = MaterialTheme.typography.titleSmall)
                Text(String.format(Locale.US, "%.5f, %.5f", location.latitude, location.longitude))
                Text(state.source.orEmpty(), style = MaterialTheme.typography.bodySmall)
            }
            Text("Search data: Open-Meteo / GeoNames (suburbs); Zippopotam.us / GeoNames (postcodes). Area centres, not street addresses.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
