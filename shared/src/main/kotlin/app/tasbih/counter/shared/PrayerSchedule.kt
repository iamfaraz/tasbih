package app.tasbih.counter.shared

import java.time.LocalDate
import java.util.Date

/** The five daily prayers and when each one's time starts and ends. */
enum class Prayer(val nameRes: Int) {
    FAJR(R.string.prayer_fajr),
    DHUHR(R.string.prayer_dhuhr),
    ASR(R.string.prayer_asr),
    MAGHRIB(R.string.prayer_maghrib),
    ISHA(R.string.prayer_isha);

    fun start(day: PrayerDay): Date = when (this) {
        FAJR -> day.fajr
        DHUHR -> day.dhuhr
        ASR -> day.asr
        MAGHRIB -> day.maghrib
        ISHA -> day.isha
    }

    /** Isha is taken to last until the next day's Fajr. */
    fun end(day: PrayerDay, nextDay: PrayerDay): Date = when (this) {
        FAJR -> day.sunrise
        DHUHR -> day.asr
        ASR -> day.maghrib
        MAGHRIB -> day.isha
        ISHA -> nextDay.fajr
    }
}

/** Value of [PrayerAlert.reminderMinutes] that turns the reminder off. */
const val REMINDER_OFF = 0

/** The reminder lead times offered in settings. */
val REMINDER_MINUTES = listOf(REMINDER_OFF, 10, 15, 20, 30, 45, 60)

/**
 * @param notify alert when the prayer's time starts.
 * @param reminderMinutes ask "have you prayed?" this long before the time ends.
 */
data class PrayerAlert(val notify: Boolean = true, val reminderMinutes: Int = 30)

/** One alert to fire. [day] is the day the prayer belongs to, which for Isha may be yesterday. */
data class PrayerEvent(
    val time: Date,
    val prayer: Prayer,
    val day: LocalDate,
    val isReminder: Boolean,
    val endsAt: Date,
)

/** The next alert after [now], or null if there's no location or nothing is turned on. */
fun nextPrayerEvent(
    config: PrayerConfig,
    now: Date,
    today: LocalDate,
    hasPrayed: (LocalDate, Prayer) -> Boolean,
): PrayerEvent? {
    val latitude = config.latitude ?: return null
    val longitude = config.longitude ?: return null
    fun timesOn(day: LocalDate) = calculatePrayerDay(latitude, longitude, config.method, config.school, day)

    // Starts from yesterday because last night's Isha can still be running.
    return (-1L..1L).flatMap { offset ->
        val day = today.plusDays(offset)
        val times = timesOn(day)
        val nextDay = timesOn(day.plusDays(1))
        Prayer.entries.flatMap { prayer ->
            val start = prayer.start(times)
            val end = prayer.end(times, nextDay)
            val alert = config.alerts.getValue(prayer)
            buildList {
                if (alert.notify) add(PrayerEvent(start, prayer, day, isReminder = false, endsAt = end))
                val reminderAt = Date(end.time - alert.reminderMinutes * 60_000L)
                if (alert.reminderMinutes != REMINDER_OFF && reminderAt.after(start) && !hasPrayed(day, prayer)) {
                    add(PrayerEvent(reminderAt, prayer, day, isReminder = true, endsAt = end))
                }
            }
        }
    }.filter { it.time.after(now) }.minByOrNull { it.time }
}

/** The prayer period [now] falls in, running from one prayer's start to the next's. */
data class PrayerPeriod(val current: Prayer, val start: Date, val next: Prayer, val nextStart: Date) {
    fun progress(now: Date): Float =
        ((now.time - start.time).toFloat() / (nextStart.time - start.time)).coerceIn(0f, 1f)
}

/** Null until a location is known. */
fun currentPrayerPeriod(config: PrayerConfig, now: Date, today: LocalDate): PrayerPeriod? {
    val latitude = config.latitude ?: return null
    val longitude = config.longitude ?: return null
    fun timesOn(day: LocalDate) = calculatePrayerDay(latitude, longitude, config.method, config.school, day)

    val todays = timesOn(today)
    val starts = listOf(Prayer.ISHA to Prayer.ISHA.start(timesOn(today.minusDays(1)))) +
        Prayer.entries.map { it to it.start(todays) } +
        (Prayer.FAJR to Prayer.FAJR.start(timesOn(today.plusDays(1))))
    val index = starts.indexOfLast { !it.second.after(now) }.coerceAtLeast(0)
    val (current, start) = starts[index]
    val (next, nextStart) = starts[index + 1]
    return PrayerPeriod(current, start, next, nextStart)
}

/**
 * The stretch of the day [now] falls in. When [active], [prayer]'s time runs from [start]
 * to [end]. Otherwise it's the gap after sunrise, and [end] is when [prayer] begins.
 */
data class PrayerWindow(
    val prayer: Prayer,
    val start: Date,
    val end: Date,
    val active: Boolean,
    /** The day [prayer] belongs to, which for Isha before dawn is yesterday. */
    val day: LocalDate,
) {
    fun progress(now: Date): Float =
        ((now.time - start.time).toFloat() / (end.time - start.time)).coerceIn(0f, 1f)

    /**
     * Whether this window's prayer is already marked as prayed. The gap after sunrise
     * counts as part of Fajr, so marking Fajr keeps things quiet until Dhuhr starts.
     */
    fun isPrayed(hasPrayed: (LocalDate, Prayer) -> Boolean): Boolean =
        hasPrayed(day, if (active) prayer else Prayer.FAJR)
}

/** Null until a location is known. */
fun currentPrayerWindow(config: PrayerConfig, now: Date, today: LocalDate): PrayerWindow? {
    val period = currentPrayerPeriod(config, now, today) ?: return null
    val times = config.today(today) ?: return null
    if (period.current != Prayer.FAJR) {
        val day = if (now.before(times.fajr)) today.minusDays(1) else today
        return PrayerWindow(period.current, period.start, period.nextStart, active = true, day = day)
    }
    return if (now.before(times.sunrise)) {
        PrayerWindow(Prayer.FAJR, period.start, times.sunrise, active = true, day = today)
    } else {
        PrayerWindow(period.next, times.sunrise, period.nextStart, active = false, day = today)
    }
}

/** Time left as "1h 09m" or "9m", rounded up so it never reads 0m with time still to go. */
fun formatTimeLeft(millis: Long): String {
    val minutes = (millis.coerceAtLeast(0) + 59_999) / 60_000
    return if (minutes >= 60) "%dh %02dm".format(minutes / 60, minutes % 60) else "${minutes}m"
}
