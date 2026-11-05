package app.tasbih.counter.wear

import android.content.Context
import app.tasbih.counter.shared.ROUND_SIZE
import app.tasbih.counter.shared.Settings
import app.tasbih.counter.shared.Tasbih
import app.tasbih.counter.shared.TasbihApplication
import app.tasbih.counter.shared.TasbihState

/** What the complication and tile show. */
data class CounterSnapshot(val tasbih: Tasbih, val count: Long, val filled: Int, val roundSize: Long) {
    val progress: Float
        get() = filled.toFloat() / roundSize
}

fun counterSnapshot(states: Map<Tasbih, TasbihState>, settings: Settings): CounterSnapshot {
    val tasbih = settings.selected
    val state = states.getValue(tasbih)
    val roundSize = tasbih.afterPrayerTarget?.takeIf { settings.afterPrayerMode } ?: ROUND_SIZE
    return CounterSnapshot(tasbih, state.total, state.beadsFilled(roundSize), roundSize)
}

fun Context.currentSnapshot(): CounterSnapshot {
    val app = applicationContext as TasbihApplication
    return counterSnapshot(app.repository.states.value, app.settings.values.value)
}

/** The Arabic name, or whatever was typed for a custom dhikr. */
fun Context.dhikrLabel(tasbih: Tasbih): String {
    val settings = (applicationContext as TasbihApplication).settings.values.value
    return settings.customNames[tasbih] ?: getString(tasbih.nameRes)
}

/** The Latin name, which suits the small space of a complication. */
fun Context.dhikrLatinLabel(tasbih: Tasbih): String {
    val settings = (applicationContext as TasbihApplication).settings.values.value
    return settings.customNames[tasbih] ?: getString(tasbih.transliterationRes)
}
