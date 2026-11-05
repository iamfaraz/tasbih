package app.tasbih.counter.shared

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService

/** Merges state from the paired device, even when the app isn't open. */
class TasbihSyncService : WearableListenerService() {
    private companion object {
        const val TAG = "TasbihSync"
    }

    override fun onDataChanged(events: DataEventBuffer) {
        val app = application as TasbihApplication
        val items = events.filter { it.type == DataEvent.TYPE_CHANGED }.map { it.dataItem }
        val before = app.repository.states.value
        items.filter { it.uri.path == WearSync.STATE_PATH }
            .map(WearSync::readStates)
            .forEach(app.repository::merge)
        items.filter { it.uri.path == WearSync.SETTINGS_PATH }
            .map(WearSync::readSettings)
            .forEach(app.settings::applyRemote)
        if (WearSync.isWatch(this)) {
            items.filter { it.uri.path == WearSync.PRAYER_PATH }
                .map(WearSync::readPrayerConfig)
                .forEach { config -> WearSync.applyPrayerConfig(this, app.prayerStore, config) }
            items.filter { it.uri.path == WearSync.RING_PATH }
                .map(WearSync::readRingSummary)
                .forEach { app.ringSummary.value = it }
            // Counts made here already vibrated, and reach this point with nothing new to add.
            if (milestoneReached(before, app.repository.states.value, app.settings.values.value)) {
                val worn = isWorn(this)
                Log.i(TAG, "Milestone from phone, worn=$worn")
                if (worn) vibrateMilestone()
            }
        }
    }
}
