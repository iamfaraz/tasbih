package app.tasbih.counter.shared

import android.content.Context
import android.media.AudioAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Value of [Settings.vibrationInterval] that turns vibration off. */
const val VIBRATION_OFF = 0L

/** The intervals offered in settings. */
val VIBRATION_INTERVALS = listOf(VIBRATION_OFF, 10L, 33L, 99L, 100L, 1000L)

data class Settings(
    val vibrationInterval: Long = 33L,
    val afterPrayerMode: Boolean = true,
    val keepScreenOn: Boolean = false,
    val selected: Tasbih = Tasbih.SUBHAN_ALLAH,
    val customNames: Map<Tasbih, String> = emptyMap(),
    val updatedAt: Long = 0,
) {
    /** Chooses the 33/33/34 sequence. The ring's number then decides where in it you are. */
    fun useSequence() = copy(afterPrayerMode = true, selected = Tasbih.SUBHAN_ALLAH)

    fun useSingle(tasbih: Tasbih) = copy(afterPrayerMode = false, selected = tasbih)

    /** Adds to the first free slot. Does nothing when the name is blank or the slots are full. */
    fun addCustom(name: String): Settings {
        val clean = name.replace(Regex("\\s+"), " ").trim()
        val slot = Tasbih.entries.firstOrNull { it.isCustom && it !in customNames }
        return if (clean.isEmpty() || slot == null) this else copy(customNames = customNames + (slot to clean))
    }

    fun removeCustom(tasbih: Tasbih): Settings {
        val removed = copy(customNames = customNames - tasbih)
        return if (!afterPrayerMode && selected == tasbih) removed.useSequence() else removed
    }

    /** The sequence, then each dhikr in turn, for stepping through them on the watch. */
    fun nextChoice(): Settings {
        val singles = Tasbih.builtIn + customNames.keys.sortedBy { it.ordinal }
        val next = if (afterPrayerMode) 0 else singles.indexOf(selected) + 1
        return if (next < singles.size) useSingle(singles[next]) else useSequence()
    }
}

fun encodeCustomNames(names: Map<Tasbih, String>): String =
    names.entries.joinToString("\n") { (tasbih, name) -> "${tasbih.id}\t$name" }

fun decodeCustomNames(encoded: String?): Map<Tasbih, String> =
    encoded.orEmpty().lines().mapNotNull { line ->
        val parts = line.split('\t', limit = 2)
        val tasbih = Tasbih.fromId(parts[0])
        if (tasbih != null && parts.size == 2) tasbih to parts[1] else null
    }.toMap()

/** The count at whose multiples [tasbih] should vibrate. */
fun Settings.milestoneInterval(tasbih: Tasbih): Long =
    tasbih.afterPrayerTarget.takeIf { afterPrayerMode } ?: vibrationInterval

/** Whether going from [before] up by [added] reaches a multiple of [interval]. */
fun reachesMultiple(before: Long, added: Long, interval: Long): Boolean =
    interval != VIBRATION_OFF && (before + added) / interval > before / interval

/**
 * Whether the change from [before] to [after] reached a milestone on any tasbih. Goes by
 * all-time totals, because finishing the sequence clears the total in the same step.
 */
fun milestoneReached(before: Map<Tasbih, TasbihState>, after: Map<Tasbih, TasbihState>, settings: Settings): Boolean =
    after.any { (tasbih, state) ->
        val old = before.getValue(tasbih)
        val added = state.allTime - old.allTime
        added > 0 && reachesMultiple(old.total, added, settings.milestoneInterval(tasbih))
    }

/** Settings and the current tasbih, shared by the phone and watch. The newest change wins. */
class TasbihSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _values = MutableStateFlow(
        Settings().run {
            copy(
                vibrationInterval = prefs.getLong(KEY_INTERVAL, vibrationInterval),
                afterPrayerMode = prefs.getBoolean(KEY_AFTER_PRAYER, afterPrayerMode),
                keepScreenOn = prefs.getBoolean(KEY_SCREEN_ON, keepScreenOn),
                selected = Tasbih.fromId(prefs.getString(KEY_SELECTED, null)) ?: selected,
                customNames = decodeCustomNames(prefs.getString(KEY_CUSTOM_NAMES, null)),
                updatedAt = prefs.getLong(KEY_UPDATED_AT, updatedAt),
            )
        }
    )
    val values: StateFlow<Settings> = _values.asStateFlow()

    @Synchronized
    fun update(change: (Settings) -> Settings) {
        val current = _values.value
        val next = change(current)
        if (next != current) save(next.copy(updatedAt = maxOf(System.currentTimeMillis(), current.updatedAt + 1)))
    }

    /** Takes settings from the paired device if they're newer than these. */
    @Synchronized
    fun applyRemote(remote: Settings) {
        if (remote.updatedAt > _values.value.updatedAt) save(remote)
    }

    private fun save(next: Settings) {
        prefs.edit()
            .putLong(KEY_INTERVAL, next.vibrationInterval)
            .putBoolean(KEY_AFTER_PRAYER, next.afterPrayerMode)
            .putBoolean(KEY_SCREEN_ON, next.keepScreenOn)
            .putString(KEY_SELECTED, next.selected.id)
            .putString(KEY_CUSTOM_NAMES, encodeCustomNames(next.customNames))
            .putLong(KEY_UPDATED_AT, next.updatedAt)
            .apply()
        _values.value = next
    }

    private companion object {
        const val KEY_INTERVAL = "vibration_interval"
        const val KEY_AFTER_PRAYER = "after_prayer_mode"
        const val KEY_SCREEN_ON = "keep_screen_on"
        const val KEY_SELECTED = "selected"
        const val KEY_CUSTOM_NAMES = "custom_names"
        const val KEY_UPDATED_AT = "updated_at"
    }
}

/**
 * Long enough to notice on the wrist without looking at the screen. The alarm usage is
 * what lets this work from the background, where other vibrations are dropped.
 */
fun Context.vibrateMilestone() {
    getSystemService(Vibrator::class.java).vibrate(
        VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE),
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
    )
}
