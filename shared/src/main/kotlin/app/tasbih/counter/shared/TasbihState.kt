package app.tasbih.counter.shared

/** Beads in one round of the tasbih. */
const val ROUND_SIZE = 33L

/**
 * The counter, in a form two devices can merge without losing counts.
 *
 * Each device only ever raises its own entry in [counts], and a reset copies
 * [counts] into [baselines] instead of clearing anything. Both maps therefore
 * only grow, so merging is a per-device maximum: the result is the same in any
 * order and however often it is repeated. That matters because the watch and
 * phone can both count while disconnected. Counts made on one device after a
 * reset it hasn't seen yet survive, because the reset only covers what the
 * resetting device had seen.
 */
data class TasbihState(
    val counts: Map<String, Long> = emptyMap(),
    val baselines: Map<String, Long> = emptyMap(),
) {
    val total: Long
        get() = counts.entries.sumOf { (device, count) -> count - (baselines[device] ?: 0L) }

    /** Everything ever counted, including counts since reset. Undone counts stay included. */
    val allTime: Long
        get() = counts.values.sum()

    /** Beads lit in the current round. A finished round shows every bead, not none. */
    fun beadsFilled(roundSize: Long = ROUND_SIZE): Int =
        if (total == 0L) 0 else ((total - 1) % roundSize + 1).toInt()

    fun increment(deviceId: String, by: Long = 1): TasbihState =
        copy(counts = counts + (deviceId to (counts[deviceId] ?: 0L) + by))

    fun reset(): TasbihState = copy(baselines = counts)

    /**
     * Moves [total] to [target], keeping all-time totals. It works directly on each device's
     * tally, as [target] can come from the ring and mustn't decide how long this takes.
     */
    fun withTotal(target: Long, deviceId: String): TasbihState {
        val wanted = target.coerceAtLeast(0)
        if (wanted >= total) return increment(deviceId, wanted - total)
        var toRemove = total - wanted
        val raised = baselines.toMutableMap()
        for ((device, count) in counts) {
            val baseline = raised[device] ?: 0L
            val taken = minOf(toRemove, count - baseline)
            if (taken > 0) raised[device] = baseline + taken
            toRemove -= taken
        }
        return copy(baselines = raised)
    }

    /**
     * Takes one off [total] by raising a baseline, since counts can only grow.
     * Prefers this device's own tally, then falls back to any other with counts left.
     */
    fun undo(deviceId: String): TasbihState {
        val device = (listOf(deviceId) + counts.keys)
            .firstOrNull { (counts[it] ?: 0L) > (baselines[it] ?: 0L) }
            ?: return this
        return copy(baselines = baselines + (device to (baselines[device] ?: 0L) + 1))
    }

    fun merge(other: TasbihState): TasbihState = TasbihState(
        counts = maxPerDevice(counts, other.counts),
        baselines = maxPerDevice(baselines, other.baselines),
    )

    /** One `device:count:baseline` entry per device, separated by `;`. */
    fun encode(): String = counts.entries.joinToString(";") { (device, count) ->
        "$device:$count:${baselines[device] ?: 0L}"
    }

    companion object {
        /** Returns null if [encoded] is malformed, so bad input can't corrupt the local count. */
        fun decode(encoded: String): TasbihState? {
            if (encoded.isEmpty()) return TasbihState()
            val counts = mutableMapOf<String, Long>()
            val baselines = mutableMapOf<String, Long>()
            for (entry in encoded.split(';')) {
                val parts = entry.split(':')
                if (parts.size != 3 || parts[0].isEmpty()) return null
                val count = parts[1].toLongOrNull() ?: return null
                val baseline = parts[2].toLongOrNull() ?: return null
                if (baseline !in 0..count) return null
                counts[parts[0]] = count
                if (baseline > 0) baselines[parts[0]] = baseline
            }
            return TasbihState(counts, baselines)
        }

        private fun maxPerDevice(a: Map<String, Long>, b: Map<String, Long>): Map<String, Long> =
            (a.keys + b.keys).associateWith { maxOf(a[it] ?: 0L, b[it] ?: 0L) }
    }
}
