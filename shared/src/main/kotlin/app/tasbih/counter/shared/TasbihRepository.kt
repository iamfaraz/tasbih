package app.tasbih.counter.shared

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/** Holds each tasbih's counter on this device and saves every change. */
class TasbihRepository(context: Context, private val settings: TasbihSettings) {
    private val prefs = context.getSharedPreferences("tasbih", Context.MODE_PRIVATE)

    // Random rather than the Wear node ID, so a reinstall gets a fresh entry and
    // can't collide with the higher count the paired device remembers for the old one.
    private val deviceId: String = prefs.getString(KEY_DEVICE_ID, null)
        ?: UUID.randomUUID().toString().also { prefs.edit().putString(KEY_DEVICE_ID, it).apply() }

    private val _states = MutableStateFlow(
        Tasbih.entries.associateWith { tasbih ->
            prefs.getString(stateKey(tasbih), null)?.let(TasbihState::decode) ?: TasbihState()
        }
    )
    val states: StateFlow<Map<Tasbih, TasbihState>> = _states.asStateFlow()

    fun select(tasbih: Tasbih) = settings.update { it.copy(selected = tasbih) }

    fun total(tasbih: Tasbih): Long = _states.value.getValue(tasbih).total

    /**
     * Adds to the selected tasbih one count at a time, so a batch from the ring moves
     * through the after-prayer sequence correctly. Returns true if any count should vibrate.
     */
    fun count(by: Long = 1): Boolean {
        var reached = false
        repeat(by.toInt()) {
            val step = countOne(_states.value, settings.values.value, deviceId)
            apply(step)
            reached = reached || step.reached
        }
        return reached
    }

    /** Makes the counters match the ring, which takes priority over any other count. */
    fun syncToRing(ringCount: Long) = apply(syncToRing(_states.value, settings.values.value, ringCount, deviceId))

    private fun apply(step: CountStep) {
        // Merging keeps anything that arrived from the watch in the meantime.
        step.states.forEach { (tasbih, state) -> mutate(tasbih) { it.merge(state) } }
        select(step.settings.selected)
    }

    fun undo(tasbih: Tasbih) = mutate(tasbih) { it.undo(deviceId) }

    fun reset(tasbih: Tasbih) = mutate(tasbih) { it.reset() }

    fun merge(remote: Map<Tasbih, TasbihState>) {
        remote.forEach { (tasbih, state) -> mutate(tasbih) { it.merge(state) } }
    }

    // Locked so saves happen in the same order as the changes they record.
    @Synchronized
    private fun mutate(tasbih: Tasbih, change: (TasbihState) -> TasbihState) {
        val current = _states.value.getValue(tasbih)
        val next = change(current)
        if (next == current) return
        prefs.edit().putString(stateKey(tasbih), next.encode()).apply()
        _states.value += tasbih to next
    }

    private companion object {
        const val KEY_DEVICE_ID = "device_id"

        fun stateKey(tasbih: Tasbih) = "state_${tasbih.id}"
    }
}
