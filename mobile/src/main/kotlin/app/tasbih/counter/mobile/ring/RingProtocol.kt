package app.tasbih.counter.mobile.ring

import app.tasbih.counter.shared.RingCharge
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The Zikr ring's plain-text protocol. Commands are `P<NN>,<timestamp>[,field...]`
 * and replies are `Q<NN>,...`. Only commands proven against a real ring are here.
 */
object RingCommands {
    private fun timestamp() = SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.US).format(Date())

    /**
     * Sets the ring's clock and location, in the official app's field order. Sending this
     * first is also what keeps the ring from dropping the link.
     */
    fun syncTime(is24Hour: Boolean, timeZoneHours: Double, latitude: Double, longitude: Double): String =
        String.format(
            Locale.US,
            "P01,%s,%s,1,%s,%.5f,%.5f",
            timestamp(),
            if (is24Hour) "0" else "1",
            timeZoneHours,
            latitude,
            longitude,
        )

    /**
     * Tells the ring how to work out prayer times itself. 21 is the routine the official
     * app always sends. Its school index isn't documented: 1 for Hanafi is an assumption.
     */
    fun prayerSetup(hanafi: Boolean, timeZoneHours: Double, latitude: Double, longitude: Double): String =
        String.format(
            Locale.US,
            "P09,%s,1,21,%d,%.5f,%.5f,%s",
            timestamp(),
            if (hanafi) 1 else 0,
            latitude,
            longitude,
            timeZoneHours,
        )

    /** Turns the ring's own reminder on or off for each prayer, from Fajr to Isha. */
    fun prayerReminders(enabled: List<Boolean>): String =
        "P23,${timestamp()},${enabled.joinToString(":") { "${if (it) 1 else 0}#+00#+00" }}"

    fun battery() = "P02,${timestamp()}"

    /** Asks for the ring's count, which comes back in Q12. */
    fun status() = "P12,${timestamp()}"

    /** The only way to buzz the ring: start "find device", then stop it. */
    fun buzz(on: Boolean) = "P04,${timestamp()},${if (on) 1 else 0}"
}

sealed interface RingMessage {
    /**
     * The total on the ring's screen, sent as Q07 after every press and as Q12 when asked.
     * Q12 goes stale while a ring task is running, but Tasbih never leaves one running.
     */
    data class Count(val count: Int) : RingMessage

    /** @param level battery on the ring's own scale of 0 to 4. */
    data class Battery(val level: Int, val charge: RingCharge) : RingMessage

    data object Other : RingMessage
}

fun parseRingMessage(text: String): RingMessage {
    val fields = text.trim().split(",")
    fun number(index: Int) = fields.getOrNull(index)?.trim()?.toIntOrNull()
    return when (fields[0]) {
        "Q07" -> number(1)?.let(RingMessage::Count)
        "Q12" -> number(2)?.let(RingMessage::Count)
        "Q02" -> number(2)?.let { RingMessage.Battery(it, RingCharge.fromCode(number(1))) }
        else -> null
    } ?: RingMessage.Other
}
