package app.tasbih.counter.shared

/** The ring's charge state, in the order of the codes it reports. */
enum class RingCharge {
    NONE, CHARGING, FULL, LOW;

    companion object {
        fun fromCode(code: Int?): RingCharge = entries.getOrElse(code ?: 0) { NONE }
    }
}

/** What the phone knows about the ring, shared with the watch. */
data class RingSummary(
    val paired: Boolean = false,
    val connected: Boolean = false,
    /** On the ring's own scale of 0 to 4. */
    val batteryLevel: Int? = null,
    val charge: RingCharge = RingCharge.NONE,
)
