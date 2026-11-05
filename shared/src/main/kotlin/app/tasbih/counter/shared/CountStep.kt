package app.tasbih.counter.shared

/** Far above any real session. The ring has no authentication, so its numbers can't be trusted. */
const val MAX_RING_COUNT = 99_999L

/** The counters and current tasbih after a change, and whether it reached a milestone. */
data class CountStep(val states: Map<Tasbih, TasbihState>, val settings: Settings, val reached: Boolean)

/**
 * Adds one count to the current tasbih. In after-prayer mode, finishing a tasbih moves on
 * to the next, and finishing the last clears the sequence so the next round starts at 0.
 */
fun countOne(states: Map<Tasbih, TasbihState>, settings: Settings, deviceId: String): CountStep {
    val tasbih = settings.selected
    val before = states.getValue(tasbih).total
    val counted = states + (tasbih to states.getValue(tasbih).increment(deviceId))
    val reached = reachesMultiple(before, 1, settings.milestoneInterval(tasbih))
    if (!reached || !settings.afterPrayerMode || tasbih.afterPrayerTarget == null) {
        return CountStep(counted, settings, reached)
    }
    val next = tasbih.nextAfterPrayer
    val finishedSequence = next == Tasbih.entries.first { it.afterPrayerTarget != null }
    return CountStep(
        states = if (finishedSequence) clearSequence(counted) else counted,
        settings = settings.copy(selected = next),
        reached = true,
    )
}

/**
 * Sets the counters from the ring's number, which overrides anything counted elsewhere.
 * With the sequence on, 70 means Subhan Allah 33, Alhamdulillah 33 and Allahu Akbar 4.
 * A finished sequence reads as all zeros, ready for the next round.
 */
fun syncToRing(states: Map<Tasbih, TasbihState>, settings: Settings, ringCount: Long, deviceId: String): CountStep {
    val count = ringCount.coerceIn(0, MAX_RING_COUNT)
    if (!settings.afterPrayerMode) {
        val current = settings.selected
        val synced = states + (current to states.getValue(current).withTotal(count, deviceId))
        return CountStep(synced, settings, reached = false)
    }
    val sequence = Tasbih.entries.filter { it.afterPrayerTarget != null }
    var remaining = count % sequence.sumOf { it.afterPrayerTarget ?: 0L }
    if (count > 0 && remaining == 0L) {
        // Already cleared, so a repeat of this number mustn't count the round again.
        if (sequence.all { states.getValue(it).total == 0L }) {
            return CountStep(states, settings.copy(selected = sequence.first()), reached = false)
        }
        // Finish the round first, so its last counts reach the all-time totals.
        val finished = states.mapValues { (tasbih, state) ->
            tasbih.afterPrayerTarget?.let { state.withTotal(it, deviceId) } ?: state
        }
        return CountStep(clearSequence(finished), settings.copy(selected = sequence.first()), reached = false)
    }
    val totals = sequence.associateWith { tasbih ->
        val taken = minOf(remaining, tasbih.afterPrayerTarget ?: 0L)
        remaining -= taken
        taken
    }
    val current = sequence.firstOrNull { totals.getValue(it) < (it.afterPrayerTarget ?: 0L) } ?: sequence.first()
    val synced = states.mapValues { (tasbih, state) ->
        totals[tasbih]?.let { state.withTotal(it, deviceId) } ?: state
    }
    return CountStep(synced, settings.copy(selected = current), reached = false)
}

private fun clearSequence(states: Map<Tasbih, TasbihState>) =
    states.mapValues { (tasbih, state) -> if (tasbih.afterPrayerTarget != null) state.reset() else state }
