package app.tasbih.counter.wear

import android.content.ComponentName
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import app.tasbih.counter.shared.TasbihApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** Keeps the complication and tile current, including for counts synced in from the ring. */
class WearApplication : TasbihApplication() {
    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            combine(repository.states, settings.values) { states, values -> counterSnapshot(states, values) }
                .distinctUntilChanged()
                .drop(1)
                .debounce(REFRESH_DEBOUNCE_MS) // A fast run of presses needs one redraw, not one each.
                .collect { refreshSurfaces() }
        }
    }

    private fun refreshSurfaces() {
        ComplicationDataSourceUpdateRequester
            .create(this, ComponentName(this, TasbihComplicationService::class.java))
            .requestUpdateAll()
        TileService.getUpdater(this).requestUpdate(TasbihTileService::class.java)
    }

    private companion object {
        const val REFRESH_DEBOUNCE_MS = 1_000L
    }
}
