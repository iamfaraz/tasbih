package app.tasbih.counter.shared

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Keeps the counter in step with the paired device over the Wearable Data Layer.
 *
 * Each device publishes every tasbih's state in one data item, which Google Play
 * services delivers to the other device, queueing it while they're apart.
 * Incoming state arrives through [TasbihSyncService]. Settings travel the same
 * way. The phone also publishes its prayer settings, so the watch needs no
 * location access of its own.
 */
class WearSync(
    context: Context,
    private val repository: TasbihRepository,
    private val settings: TasbihSettings,
    private val prayerStore: PrayerStore,
    private val ringSummary: MutableStateFlow<RingSummary>,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val dataClient = Wearable.getDataClient(context)
    private val onWatch = isWatch(context)

    @OptIn(FlowPreview::class)
    fun start() {
        scope.launch { pullExisting() }
        scope.launch {
            // StateFlow replays the current value, so this also republishes anything
            // left unsent if the process died during the debounce window.
            repository.states.debounce(PUBLISH_DEBOUNCE_MS).collect { publish(it) }
        }
        scope.launch { settings.values.collect { publishSettings(it) } }
        if (!onWatch) {
            scope.launch { prayerStore.config.collect { publishPrayerConfig(it) } }
            scope.launch { ringSummary.collect { publishRingSummary(it) } }
        }
    }

    /** Catches up on anything published while this app wasn't listening. */
    private suspend fun pullExisting() {
        try {
            val items = dataClient.dataItems.await()
            try {
                items.filter { it.uri.path == STATE_PATH }
                    .map(::readStates)
                    .forEach(repository::merge)
                items.filter { it.uri.path == SETTINGS_PATH }
                    .map(::readSettings)
                    .forEach(settings::applyRemote)
                if (onWatch) {
                    items.filter { it.uri.path == PRAYER_PATH }
                        .map(::readPrayerConfig)
                        .forEach { config -> applyPrayerConfig(appContext, prayerStore, config) }
                    items.filter { it.uri.path == RING_PATH }
                        .map(::readRingSummary)
                        .forEach { ringSummary.value = it }
                }
            } finally {
                items.release()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Expected on phones without a paired watch. Counting still works locally.
            Log.w(TAG, "Couldn't read synced state", e)
        }
    }

    private suspend fun publish(states: Map<Tasbih, TasbihState>) = put(STATE_PATH) {
        states.forEach { (tasbih, state) -> putString(tasbih.id, state.encode()) }
    }

    private suspend fun publishSettings(values: Settings) = put(SETTINGS_PATH) {
        putLong(INTERVAL_KEY, values.vibrationInterval)
        putBoolean(AFTER_PRAYER_KEY, values.afterPrayerMode)
        putBoolean(SCREEN_ON_KEY, values.keepScreenOn)
        putString(SELECTED_KEY, values.selected.id)
        putString(CUSTOM_NAMES_KEY, encodeCustomNames(values.customNames))
        putLong(UPDATED_AT_KEY, values.updatedAt)
    }

    private suspend fun publishPrayerConfig(config: PrayerConfig) = put(PRAYER_PATH) {
        config.latitude?.let { putDouble(LATITUDE_KEY, it) }
        config.longitude?.let { putDouble(LONGITUDE_KEY, it) }
        putString(METHOD_KEY, config.method.name)
        putString(SCHOOL_KEY, config.school.name)
        putBoolean(WATCH_ALERTS_KEY, config.watchAlerts)
        putString(ALERT_PRAYERS_KEY, config.alerts.filterValues { it.notify }.keys.joinToString(",") { it.name })
    }

    private suspend fun publishRingSummary(summary: RingSummary) = put(RING_PATH) {
        putBoolean(RING_PAIRED_KEY, summary.paired)
        putBoolean(RING_CONNECTED_KEY, summary.connected)
        summary.batteryLevel?.let { putInt(RING_BATTERY_KEY, it) }
        putString(RING_CHARGE_KEY, summary.charge.name)
    }

    private suspend fun put(path: String, fill: DataMap.() -> Unit) {
        val request = PutDataMapRequest.create(path).apply { dataMap.fill() }.asPutDataRequest().setUrgent()
        try {
            dataClient.putDataItem(request).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't publish $path", e)
        }
    }

    companion object {
        private const val TAG = "WearSync"
        private const val PUBLISH_DEBOUNCE_MS = 300L

        private const val INTERVAL_KEY = "vibration_interval"
        private const val AFTER_PRAYER_KEY = "after_prayer_mode"
        private const val SCREEN_ON_KEY = "keep_screen_on"
        private const val SELECTED_KEY = "selected"
        private const val CUSTOM_NAMES_KEY = "custom_names"
        private const val UPDATED_AT_KEY = "updated_at"
        private const val RING_PAIRED_KEY = "paired"
        private const val RING_CONNECTED_KEY = "connected"
        private const val RING_BATTERY_KEY = "battery"
        private const val RING_CHARGE_KEY = "charge"
        private const val LATITUDE_KEY = "latitude"
        private const val LONGITUDE_KEY = "longitude"
        private const val METHOD_KEY = "method"
        private const val SCHOOL_KEY = "school"
        private const val WATCH_ALERTS_KEY = "watch_alerts"
        private const val ALERT_PRAYERS_KEY = "alert_prayers"

        const val STATE_PATH = "/tasbih/state"
        const val SETTINGS_PATH = "/tasbih/settings"
        const val PRAYER_PATH = "/tasbih/prayer"
        const val RING_PATH = "/tasbih/ring"

        /** Tells the watch app that its prayer settings changed, so it can reschedule. */
        const val ACTION_PRAYER_CONFIG = "app.tasbih.counter.action.PRAYER_CONFIG"

        fun applyPrayerConfig(context: Context, store: PrayerStore, config: PrayerConfig) {
            store.update { config }
            context.sendBroadcast(Intent(ACTION_PRAYER_CONFIG).setPackage(context.packageName))
        }

        fun isWatch(context: Context): Boolean =
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)

        /** Everything stays on if the phone hasn't said otherwise. */
        internal fun alertsFrom(encoded: String?): Map<Prayer, PrayerAlert> {
            val enabled = encoded?.split(",")
            return Prayer.entries.associateWith { PrayerAlert(notify = enabled == null || it.name in enabled) }
        }

        fun readSettings(item: DataItem): Settings {
            val dataMap = DataMapItem.fromDataItem(item).dataMap
            val defaults = Settings()
            return Settings(
                vibrationInterval = dataMap.getLong(INTERVAL_KEY, defaults.vibrationInterval),
                afterPrayerMode = dataMap.getBoolean(AFTER_PRAYER_KEY, defaults.afterPrayerMode),
                keepScreenOn = dataMap.getBoolean(SCREEN_ON_KEY, defaults.keepScreenOn),
                selected = Tasbih.fromId(dataMap.getString(SELECTED_KEY)) ?: defaults.selected,
                customNames = decodeCustomNames(dataMap.getString(CUSTOM_NAMES_KEY)),
                updatedAt = dataMap.getLong(UPDATED_AT_KEY),
            )
        }

        fun readRingSummary(item: DataItem): RingSummary {
            val dataMap = DataMapItem.fromDataItem(item).dataMap
            return RingSummary(
                paired = dataMap.getBoolean(RING_PAIRED_KEY),
                connected = dataMap.getBoolean(RING_CONNECTED_KEY),
                batteryLevel = dataMap.takeIf { it.containsKey(RING_BATTERY_KEY) }?.getInt(RING_BATTERY_KEY),
                charge = dataMap.getString(RING_CHARGE_KEY).toEnumOr(RingCharge.NONE),
            )
        }

        fun readPrayerConfig(item: DataItem): PrayerConfig {
            val dataMap = DataMapItem.fromDataItem(item).dataMap
            return PrayerConfig(
                latitude = dataMap.takeIf { it.containsKey(LATITUDE_KEY) }?.getDouble(LATITUDE_KEY),
                longitude = dataMap.takeIf { it.containsKey(LONGITUDE_KEY) }?.getDouble(LONGITUDE_KEY),
                method = dataMap.getString(METHOD_KEY).toEnumOr(PrayerMethod.KARACHI),
                school = dataMap.getString(SCHOOL_KEY).toEnumOr(School.HANAFI),
                watchAlerts = dataMap.getBoolean(WATCH_ALERTS_KEY),
                alerts = alertsFrom(dataMap.getString(ALERT_PRAYERS_KEY)),
            )
        }

        fun readStates(item: DataItem): Map<Tasbih, TasbihState> {
            val dataMap = DataMapItem.fromDataItem(item).dataMap
            return Tasbih.entries.mapNotNull { tasbih ->
                dataMap.getString(tasbih.id)?.let(TasbihState::decode)?.let { tasbih to it }
            }.toMap()
        }
    }
}
