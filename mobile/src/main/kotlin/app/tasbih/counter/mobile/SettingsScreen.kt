package app.tasbih.counter.mobile

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tasbih.counter.mobile.ring.RingDialog
import app.tasbih.counter.mobile.update.UpdateCard
import app.tasbih.counter.mobile.ring.RingStore
import app.tasbih.counter.mobile.ring.ringStatusText
import app.tasbih.counter.shared.Prayer
import app.tasbih.counter.shared.PrayerAlert
import app.tasbih.counter.shared.PrayerMethod
import app.tasbih.counter.shared.PrayerStore
import app.tasbih.counter.shared.REMINDER_MINUTES
import app.tasbih.counter.shared.REMINDER_OFF
import app.tasbih.counter.shared.School
import app.tasbih.counter.shared.TasbihSettings
import app.tasbih.counter.shared.VIBRATION_INTERVALS
import app.tasbih.counter.shared.VIBRATION_OFF
import kotlinx.coroutines.launch
import app.tasbih.counter.shared.R as SharedR

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(settings: TasbihSettings, store: PrayerStore, location: LocationUpdater) {
    val values by settings.values.collectAsStateWithLifecycle()
    val config by store.config.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val ring by remember { RingStore.get(context) }.status.collectAsStateWithLifecycle()
    var showingLocation by remember { mutableStateOf(false) }
    var showingRing by remember { mutableStateOf(false) }

    if (showingLocation) LocationDialog(store, location, onDismiss = { showingLocation = false })
    if (showingRing) RingDialog(onDismiss = { showingRing = false })

    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(SharedR.string.settings))
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        ) {
            SectionLabel(SharedR.string.prayers)
            SoftCard(
                Modifier
                    .fillMaxWidth()
                    .clip(CardShape)
                    .clickable { showingLocation = true },
            ) {
                SettingRow(
                    SharedR.drawable.ic_pin,
                    stringResource(SharedR.string.location),
                    config.placeName
                        ?: stringResource(
                            if (config.hasLocation) SharedR.string.location_current else SharedR.string.location_not_set
                        ),
                )
            }
            SettingCard(
                SharedR.drawable.ic_book,
                stringResource(SharedR.string.school),
                stringResource(config.school.labelRes),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    School.entries.forEach { school ->
                        Pill(stringResource(school.labelRes), school == config.school) {
                            store.update { it.copy(school = school) }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(stringResource(SharedR.string.school_note), color = colors.muted, fontSize = 14.sp)
            }
            SettingCard(
                SharedR.drawable.ic_clock,
                stringResource(SharedR.string.calculation_method),
                stringResource(config.method.labelRes),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PrayerMethod.entries.forEach { method ->
                        Pill(stringResource(method.labelRes), method == config.method) {
                            store.update { it.copy(method = method) }
                        }
                    }
                }
            }
            SettingCard(
                SharedR.drawable.ic_bell,
                stringResource(SharedR.string.prayer_alerts),
                when {
                    config.soundUri == null -> stringResource(SharedR.string.sound_built_in)
                    config.soundName != null -> stringResource(SharedR.string.sound_named, config.soundName.orEmpty())
                    else -> stringResource(SharedR.string.sound_custom)
                },
            ) {
                AlertSettings(store)
            }

            SwitchCard(
                SharedR.drawable.ic_clock,
                stringResource(SharedR.string.countdown_setting),
                stringResource(SharedR.string.countdown_setting_note),
                config.countdownEnabled,
            ) { on -> store.update { it.copy(countdownEnabled = on) } }

            SwitchCard(
                SharedR.drawable.ic_bell,
                stringResource(SharedR.string.watch_alerts_setting),
                stringResource(SharedR.string.watch_alerts_note),
                config.watchAlerts,
            ) { on -> store.update { it.copy(watchAlerts = on) } }

            SectionLabel(SharedR.string.counting)
            SoftCard(
                Modifier
                    .fillMaxWidth()
                    .clip(CardShape)
                    .clickable { showingRing = true },
            ) {
                SettingRow(SharedR.drawable.ic_ring, stringResource(SharedR.string.ring), ringStatusText(ring))
            }
            SettingCard(
                SharedR.drawable.ic_vibrate,
                stringResource(SharedR.string.vibrate_every),
                if (values.vibrationInterval == VIBRATION_OFF) stringResource(SharedR.string.vibrate_off)
                else stringResource(SharedR.string.vibrate_interval, values.vibrationInterval),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    VIBRATION_INTERVALS.forEach { interval ->
                        Pill(
                            if (interval == VIBRATION_OFF) stringResource(SharedR.string.vibrate_off) else interval.toString(),
                            interval == values.vibrationInterval,
                        ) { settings.update { it.copy(vibrationInterval = interval) } }
                    }
                }
            }
            SwitchCard(
                SharedR.drawable.ic_screen,
                stringResource(SharedR.string.keep_screen_on),
                stringResource(SharedR.string.keep_screen_on_note),
                values.keepScreenOn,
            ) { on -> settings.update { it.copy(keepScreenOn = on) } }

            UpdateCard()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlertSettings(store: PrayerStore) {
    val config by store.config.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val soundPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.let {
            IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        }
        if (uri != null) {
            val name = RingtoneManager.getRingtone(context, uri)?.getTitle(context)
            store.update { it.copy(soundUri = uri.toString(), soundName = name) }
        }
    }
    val scope = rememberCoroutineScope()
    var soundFailed by remember { mutableStateOf(false) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { source ->
        if (source != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            scope.launch {
                val sound = importSound(context, source)
                soundFailed = sound == null
                if (sound != null) store.update { it.copy(soundUri = sound.uri.toString(), soundName = sound.name) }
            }
        }
    }

    Prayer.entries.forEach { prayer ->
        val alert = config.alerts.getValue(prayer)
        fun change(next: PrayerAlert) = store.update { it.copy(alerts = it.alerts + (prayer to next)) }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(prayer.nameRes), fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Switch(checked = alert.notify, onCheckedChange = { change(alert.copy(notify = it)) })
        }
        Pill(
            if (alert.reminderMinutes == REMINDER_OFF) stringResource(SharedR.string.reminder_off)
            else stringResource(SharedR.string.reminder_minutes, alert.reminderMinutes),
        ) {
            val index = REMINDER_MINUTES.indexOf(alert.reminderMinutes)
            change(alert.copy(reminderMinutes = REMINDER_MINUTES[(index + 1) % REMINDER_MINUTES.size]))
        }
        Spacer(Modifier.height(10.dp))
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Pill(stringResource(SharedR.string.notification_sound)) {
            soundPicker.launch(
                Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                    .putExtra(
                        RingtoneManager.EXTRA_RINGTONE_TYPE,
                        RingtoneManager.TYPE_NOTIFICATION or RingtoneManager.TYPE_ALARM,
                    )
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, config.soundUri?.let(Uri::parse))
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Pill(stringResource(SharedR.string.sound_from_file)) { filePicker.launch(arrayOf("audio/*")) }
        }
        if (config.soundUri != null) {
            Pill(stringResource(SharedR.string.built_in_sound)) {
                store.update { it.copy(soundUri = null, soundName = null) }
            }
        }
        Pill(stringResource(SharedR.string.test_alert)) {
            PrayerNotifications.showPrayerTime(context, Prayer.FAJR, config.soundUri)
            PrayerNotifications.showReminder(context, Prayer.FAJR, System.currentTimeMillis(), day = null)
        }
    }
    if (soundFailed) {
        Text(stringResource(SharedR.string.sound_failed), color = colors.muted, fontSize = 14.sp)
    }
}

@Composable
private fun SectionLabel(labelRes: Int) {
    Text(stringResource(labelRes), color = colors.muted, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun SettingRow(iconRes: Int, title: String, subtitle: String, trailing: @Composable () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(iconRes), contentDescription = null, tint = colors.accent, modifier = Modifier.size(26.dp))
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = colors.muted)
        }
        trailing()
    }
}

/** A row that opens to show its choices. */
@Composable
private fun SettingCard(
    iconRes: Int,
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    SoftCard(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .clickable { open = !open }
            .animateContentSize(),
    ) {
        SettingRow(iconRes, title, subtitle)
        if (open) {
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun SwitchCard(iconRes: Int, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    SoftCard(Modifier.fillMaxWidth()) {
        SettingRow(iconRes, title, subtitle) {
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}
