package app.tasbih.counter.mobile

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tasbih.counter.shared.PrayerStore
import app.tasbih.counter.shared.ui.MAX_HIJRI_ADJUSTMENT
import app.tasbih.counter.shared.ui.effectiveHijriAdjustment
import app.tasbih.counter.shared.ui.hijriDateNow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Locale
import app.tasbih.counter.shared.R as SharedR

@Composable
fun HijriDateDialog(store: PrayerStore, onDismiss: () -> Unit) {
    val config by store.config.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val adjustment = config.effectiveHijriAdjustment
    val gregorian = remember { DateFormat.getLongDateFormat(context).format(Date()) }

    fun adjust(by: Int) = store.update {
        it.copy(hijriAdjustment = (adjustment + by).coerceIn(-MAX_HIJRI_ADJUSTMENT, MAX_HIJRI_ADJUSTMENT))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.card,
        shape = CardShape,
        title = { Text(config.hijriDateNow(), fontSize = 22.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(gregorian, color = colors.muted)
                Text(stringResource(SharedR.string.hijri_adjustment), fontWeight = FontWeight.Medium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill("−") { adjust(-1) }
                    Text(
                        stringResource(SharedR.string.hijri_days, adjustment),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    Pill("+") { adjust(1) }
                }
                if (config.hijriAdjustment != null) {
                    Pill(stringResource(SharedR.string.hijri_automatic)) {
                        store.update { it.copy(hijriAdjustment = null) }
                    }
                }
                Text(stringResource(SharedR.string.hijri_note), color = colors.muted, fontSize = 14.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) }
        },
    )
}

@Composable
fun LocationDialog(store: PrayerStore, location: LocationUpdater, onDismiss: () -> Unit) {
    val config by store.config.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Address>?>(null) }
    var searching by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.card,
        shape = CardShape,
        title = {
            Text(
                config.placeName ?: stringResource(SharedR.string.location_not_set),
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val latitude = config.latitude
                val longitude = config.longitude
                if (latitude != null && longitude != null) {
                    Text(
                        String.format(Locale.ROOT, "%.3f, %.3f", latitude, longitude),
                        color = colors.muted,
                    )
                }
                Text(
                    stringResource(
                        if (config.manualLocation) SharedR.string.location_is_manual else SharedR.string.location_is_automatic
                    ),
                    color = colors.muted,
                )
                Pill(stringResource(SharedR.string.use_current_location)) {
                    location.useCurrent()
                    onDismiss()
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(SharedR.string.search_city)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Pill(stringResource(SharedR.string.search)) {
                    if (query.isNotBlank() && !searching) {
                        searching = true
                        scope.launch {
                            results = searchPlaces(context, query)
                            searching = false
                        }
                    }
                }
                results?.let { found ->
                    if (found.isEmpty()) {
                        Text(stringResource(SharedR.string.no_places_found), color = colors.muted)
                    }
                    found.forEach { address ->
                        Text(
                            address.label(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    store.update {
                                        it.copy(
                                            latitude = address.latitude,
                                            longitude = address.longitude,
                                            placeName = address.shortName(),
                                            countryCode = address.countryCode,
                                            manualLocation = true,
                                        )
                                    }
                                    onDismiss()
                                }
                                .padding(vertical = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(SharedR.string.cancel)) }
        },
    )
}

private fun Address.shortName(): String = locality ?: subAdminArea ?: adminArea ?: featureName ?: countryName.orEmpty()

private fun Address.label(): String = listOfNotNull(shortName(), adminArea, countryName).distinct().joinToString(", ")

@Suppress("DEPRECATION") // The listener version needs API 33.
private suspend fun searchPlaces(context: Context, query: String): List<Address> = withContext(Dispatchers.IO) {
    try {
        Geocoder(context).getFromLocationName(query, 5).orEmpty().filter { it.hasLatitude() && it.hasLongitude() }
    } catch (e: Exception) {
        emptyList() // Usually no network, which reads the same as no match.
    }
}
