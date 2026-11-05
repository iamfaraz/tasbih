package app.tasbih.counter.shared

import android.content.Context
import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.SunnahTimes
import com.batoulapps.adhan.data.DateComponents
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.util.Date

/**
 * What each school changes in the calculation.
 *
 * @param doubleShadow Asr begins when a shadow is twice an object's length
 *   (Hanafi) rather than once.
 * @param ishaAngle sun depression at which Isha begins: 18° for the end of the
 *   white twilight (Hanafi), 15° for the end of the red twilight (the others).
 *   These are conventional approximations, as twilight has no single fixed angle.
 */
enum class School(val labelRes: Int, val doubleShadow: Boolean, val ishaAngle: Double) {
    HANAFI(R.string.school_hanafi, true, 18.0),
    SHAFII(R.string.school_shafii, false, 15.0),
    MALIKI(R.string.school_maliki, false, 15.0),
    HANBALI(R.string.school_hanbali, false, 15.0),
}

/** The authority whose Fajr angle is used. */
enum class PrayerMethod(val labelRes: Int, val adhan: CalculationMethod) {
    KARACHI(R.string.method_karachi, CalculationMethod.KARACHI),
    MUSLIM_WORLD_LEAGUE(R.string.method_mwl, CalculationMethod.MUSLIM_WORLD_LEAGUE),
    NORTH_AMERICA(R.string.method_isna, CalculationMethod.NORTH_AMERICA),
    EGYPTIAN(R.string.method_egypt, CalculationMethod.EGYPTIAN),
    UMM_AL_QURA(R.string.method_umm_al_qura, CalculationMethod.UMM_AL_QURA),
    DUBAI(R.string.method_dubai, CalculationMethod.DUBAI),
}

data class PrayerConfig(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val method: PrayerMethod = PrayerMethod.KARACHI,
    val school: School = School.HANAFI,
    val alerts: Map<Prayer, PrayerAlert> = Prayer.entries.associateWith { PrayerAlert() },
    val soundUri: String? = null,
    val soundName: String? = null,
    val placeName: String? = null,
    val countryCode: String? = null,
    /** Set when a place was chosen by hand, so location updates leave it alone. */
    val manualLocation: Boolean = false,
    /** Days to move the Hijri date, or null to use the default for [countryCode]. */
    val hijriAdjustment: Int? = null,
    /** Shows the ongoing notification with the time left for the current prayer. */
    val countdownEnabled: Boolean = false,
    /** Has the watch raise its own alert at each prayer time. */
    val watchAlerts: Boolean = false,
) {
    val hasLocation: Boolean
        get() = latitude != null && longitude != null
}

/** @param tahajjud start of the last third of the night that follows this day's Maghrib. */
data class PrayerDay(
    val fajr: Date,
    val sunrise: Date,
    val dhuhr: Date,
    val asr: Date,
    val maghrib: Date,
    val isha: Date,
    val tahajjud: Date,
) {
    /** Each time with the string naming it, in the order of the day. */
    val entries: List<Pair<Int, Date>>
        get() = listOf(
            R.string.prayer_fajr to fajr,
            R.string.prayer_sunrise to sunrise,
            R.string.prayer_dhuhr to dhuhr,
            R.string.prayer_asr to asr,
            R.string.prayer_maghrib to maghrib,
            R.string.prayer_isha to isha,
            R.string.prayer_tahajjud to tahajjud,
        )
}

fun calculatePrayerDay(
    latitude: Double,
    longitude: Double,
    method: PrayerMethod,
    school: School,
    date: LocalDate,
): PrayerDay {
    val parameters = method.adhan.parameters.apply {
        madhab = if (school.doubleShadow) Madhab.HANAFI else Madhab.SHAFI
        // Methods that set Isha a fixed interval after Maghrib ignore this angle.
        ishaAngle = school.ishaAngle
    }
    val times = PrayerTimes(
        Coordinates(latitude, longitude),
        DateComponents(date.year, date.monthValue, date.dayOfMonth),
        parameters,
    )
    return PrayerDay(
        fajr = times.fajr.toWholeMinute(),
        sunrise = times.sunrise.toWholeMinute(),
        dhuhr = times.dhuhr.toWholeMinute(),
        asr = times.asr.toWholeMinute(),
        maghrib = times.maghrib.toWholeMinute(),
        isha = times.isha.toWholeMinute(),
        tahajjud = SunnahTimes(times).lastThirdOfTheNight.toWholeMinute(),
    )
}

/** adhan rounds to the minute but leaves arbitrary milliseconds behind. */
private fun Date.toWholeMinute() = Date(time / 60_000 * 60_000)

/** The times for [date], which defaults to today, or null until a location is known. */
fun PrayerConfig.today(date: LocalDate = LocalDate.now()): PrayerDay? {
    return calculatePrayerDay(latitude ?: return null, longitude ?: return null, method, school, date)
}

/** The saved prayer settings. The phone owns them and sends them to the watch. */
class PrayerStore(context: Context) {
    private val prefs = context.getSharedPreferences("prayer", Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(
        PrayerConfig(
            latitude = prefs.getString(KEY_LATITUDE, null)?.toDoubleOrNull(),
            longitude = prefs.getString(KEY_LONGITUDE, null)?.toDoubleOrNull(),
            method = prefs.getString(KEY_METHOD, null).toEnumOr(PrayerMethod.KARACHI),
            school = prefs.getString(KEY_SCHOOL, null).toEnumOr(School.HANAFI),
            alerts = Prayer.entries.associateWith { prayer ->
                val defaults = PrayerAlert()
                PrayerAlert(
                    notify = prefs.getBoolean(notifyKey(prayer), defaults.notify),
                    reminderMinutes = prefs.getInt(reminderKey(prayer), defaults.reminderMinutes),
                )
            },
            soundUri = prefs.getString(KEY_SOUND, null),
            soundName = prefs.getString(KEY_SOUND_NAME, null),
            placeName = prefs.getString(KEY_PLACE, null),
            countryCode = prefs.getString(KEY_COUNTRY, null),
            manualLocation = prefs.getBoolean(KEY_MANUAL, false),
            hijriAdjustment = prefs.getString(KEY_HIJRI, null)?.toIntOrNull(),
            countdownEnabled = prefs.getBoolean(KEY_COUNTDOWN, false),
            watchAlerts = prefs.getBoolean(KEY_WATCH_ALERTS, false),
        )
    )
    val config: StateFlow<PrayerConfig> = _config.asStateFlow()

    private val _prayed = MutableStateFlow(prefs.getStringSet(KEY_PRAYED, null).orEmpty().toSet())

    /** Prayers marked as prayed, as `date:PRAYER`. Read through [hasPrayed]. */
    val prayed: StateFlow<Set<String>> = _prayed.asStateFlow()

    fun hasPrayed(day: LocalDate, prayer: Prayer): Boolean = prayedKey(day, prayer) in _prayed.value

    @Synchronized
    fun setPrayed(day: LocalDate, prayer: Prayer, prayed: Boolean) {
        val key = prayedKey(day, prayer)
        val cutoff = LocalDate.now().minusDays(PRAYED_DAYS_KEPT).toString()
        // ISO dates sort by date, so this drops old entries.
        val next = (if (prayed) _prayed.value + key else _prayed.value - key).filter { it >= cutoff }.toSet()
        prefs.edit().putStringSet(KEY_PRAYED, next).apply()
        _prayed.value = next
    }

    @Synchronized
    fun update(change: (PrayerConfig) -> PrayerConfig) {
        val next = change(_config.value)
        prefs.edit()
            .putString(KEY_LATITUDE, next.latitude?.toString())
            .putString(KEY_LONGITUDE, next.longitude?.toString())
            .putString(KEY_METHOD, next.method.name)
            .putString(KEY_SCHOOL, next.school.name)
            .putString(KEY_SOUND, next.soundUri)
            .putString(KEY_SOUND_NAME, next.soundName)
            .putString(KEY_PLACE, next.placeName)
            .putString(KEY_COUNTRY, next.countryCode)
            .putBoolean(KEY_MANUAL, next.manualLocation)
            .putString(KEY_HIJRI, next.hijriAdjustment?.toString())
            .putBoolean(KEY_COUNTDOWN, next.countdownEnabled)
            .putBoolean(KEY_WATCH_ALERTS, next.watchAlerts)
            .apply {
                next.alerts.forEach { (prayer, alert) ->
                    putBoolean(notifyKey(prayer), alert.notify)
                    putInt(reminderKey(prayer), alert.reminderMinutes)
                }
            }
            .apply()
        _config.value = next
    }

    private companion object {
        const val KEY_LATITUDE = "latitude"
        const val KEY_LONGITUDE = "longitude"
        const val KEY_METHOD = "method"
        const val KEY_SCHOOL = "school"
        const val KEY_SOUND = "sound_uri"
        const val KEY_SOUND_NAME = "sound_name"
        const val KEY_PLACE = "place_name"
        const val KEY_COUNTRY = "country_code"
        const val KEY_MANUAL = "manual_location"
        const val KEY_HIJRI = "hijri_adjustment"
        const val KEY_COUNTDOWN = "countdown_enabled"
        const val KEY_WATCH_ALERTS = "watch_alerts"
        const val KEY_PRAYED = "prayed"
        const val PRAYED_DAYS_KEPT = 7L

        fun notifyKey(prayer: Prayer) = "notify_${prayer.name}"
        fun reminderKey(prayer: Prayer) = "reminder_${prayer.name}"
        fun prayedKey(day: LocalDate, prayer: Prayer) = "$day:${prayer.name}"
    }
}

inline fun <reified T : Enum<T>> String?.toEnumOr(default: T): T =
    enumValues<T>().firstOrNull { it.name == this } ?: default
