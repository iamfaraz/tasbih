package app.tasbih.counter.shared.ui

import app.tasbih.counter.shared.PrayerConfig
import app.tasbih.counter.shared.today
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import java.util.Date

/** The furthest the date can be moved either way in settings. */
const val MAX_HIJRI_ADJUSTMENT = 2

private val MONTHS = listOf(
    "Muharram", "Safar", "Rabi al-Awwal", "Rabi al-Thani", "Jumada al-Ula", "Jumada al-Akhirah",
    "Rajab", "Shaban", "Ramadan", "Shawwal", "Dhu al-Qadah", "Dhu al-Hijjah",
)

/** These countries follow local moon sighting, which usually runs a day behind Umm al-Qura. */
private val ONE_DAY_BEHIND = setOf("PK", "IN", "BD", "LK", "NP")

/** The adjustment to use when the person hasn't chosen one. */
fun regionalHijriAdjustment(countryCode: String?): Int = if (countryCode in ONE_DAY_BEHIND) -1 else 0

/**
 * The Hijri date on [today], from the Umm al-Qura calendar moved by [adjustment] days.
 * The Islamic day starts at Maghrib, so [afterMaghrib] moves on to the next date.
 */
fun hijriDate(today: LocalDate, afterMaghrib: Boolean, adjustment: Int): String {
    val days = adjustment + if (afterMaghrib) 1 else 0
    val date = HijrahDate.from(today).plus(days.toLong(), ChronoUnit.DAYS)
    val day = date.get(ChronoField.DAY_OF_MONTH)
    val month = MONTHS[date.get(ChronoField.MONTH_OF_YEAR) - 1]
    return "$day $month ${date.get(ChronoField.YEAR)}"
}

val PrayerConfig.effectiveHijriAdjustment: Int
    get() = hijriAdjustment ?: regionalHijriAdjustment(countryCode)

/** Today's Hijri date for this location and adjustment. */
fun PrayerConfig.hijriDateNow(): String {
    val maghrib = today()?.maghrib
    return hijriDate(LocalDate.now(), maghrib != null && Date().after(maghrib), effectiveHijriAdjustment)
}
