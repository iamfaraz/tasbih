package app.tasbih.counter.mobile

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import app.tasbih.counter.shared.PrayerStore
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class LocationUpdater(val failed: Boolean, val refresh: () -> Unit, val useCurrent: () -> Unit)

/** Finds the location, asking for location and notification access first if needed. */
@Composable
fun rememberLocationUpdater(store: PrayerStore): LocationUpdater {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var failed by remember { mutableStateOf(false) }

    fun fetch() {
        scope.launch { failed = !updateLocation(context, store) }
    }

    val permissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true) fetch() else failed = true
    }

    fun refresh() {
        val missing = neededPermissions().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) fetch() else permissions.launch(missing.toTypedArray())
    }

    return LocationUpdater(
        failed = failed,
        refresh = ::refresh,
        useCurrent = {
            store.update { it.copy(manualLocation = false) }
            refresh()
        },
    )
}

private fun neededPermissions() = buildList {
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
}

/** Returns whether a location was found. The permission must already be held. */
@Suppress("MissingPermission")
private suspend fun updateLocation(context: Context, store: PrayerStore): Boolean {
    if (store.config.value.manualLocation) return true
    val client = LocationServices.getFusedLocationProviderClient(context)
    val location = try {
        client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
            ?: client.lastLocation.await()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    } ?: return false
    val place = lookUpPlace(context, location)
    store.update {
        it.copy(
            latitude = location.latitude,
            longitude = location.longitude,
            placeName = place?.run { locality ?: subAdminArea ?: adminArea } ?: it.placeName,
            countryCode = place?.countryCode ?: it.countryCode,
        )
    }
    return true
}

@Suppress("DEPRECATION") // The listener version needs API 33.
private suspend fun lookUpPlace(context: Context, location: Location): Address? = withContext(Dispatchers.IO) {
    try {
        Geocoder(context).getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull()
    } catch (e: Exception) {
        null // The place is only a label and a calendar default, and prayer times don't need it.
    }
}
