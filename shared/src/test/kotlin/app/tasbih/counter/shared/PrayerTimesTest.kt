package app.tasbih.counter.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PrayerTimesTest {
    private val date = LocalDate.of(2026, 11, 5)

    private fun karachi(school: School, method: PrayerMethod = PrayerMethod.KARACHI) =
        calculatePrayerDay(24.8607, 67.0011, method, school, date)

    @Test
    fun `hanafi asr is later than the other schools`() {
        assertTrue(karachi(School.HANAFI).asr.after(karachi(School.SHAFII).asr))
    }

    @Test
    fun `hanafi isha is later than the other schools`() {
        assertTrue(karachi(School.HANAFI).isha.after(karachi(School.HANBALI).isha))
    }

    @Test
    fun `shafii maliki and hanbali share the same times`() {
        assertEquals(karachi(School.SHAFII), karachi(School.MALIKI))
        assertEquals(karachi(School.SHAFII), karachi(School.HANBALI))
    }

    @Test
    fun `school leaves the other prayers alone`() {
        val hanafi = karachi(School.HANAFI)
        val shafii = karachi(School.SHAFII)

        assertEquals(shafii.fajr, hanafi.fajr)
        assertEquals(shafii.dhuhr, hanafi.dhuhr)
        assertEquals(shafii.maghrib, hanafi.maghrib)
        assertEquals(shafii.tahajjud, hanafi.tahajjud)
    }

    @Test
    fun `the day runs in order`() {
        School.entries.forEach { school ->
            val times = karachi(school).entries.map { it.second }
            assertEquals(times.sorted(), times)
        }
    }

    @Test
    fun `tahajjud falls in the last third of the night`() {
        val day = karachi(School.HANAFI)
        val next = calculatePrayerDay(24.8607, 67.0011, PrayerMethod.KARACHI, School.HANAFI, date.plusDays(1))
        val night = next.fajr.time - day.maghrib.time
        val fromMaghrib = day.tahajjud.time - day.maghrib.time

        assertEquals(2.0 / 3, fromMaghrib.toDouble() / night, 0.01)
    }

    @Test
    fun `fixed interval isha ignores school`() {
        val method = PrayerMethod.UMM_AL_QURA

        assertEquals(karachi(School.SHAFII, method).isha, karachi(School.HANAFI, method).isha)
    }

    @Test
    fun `unknown stored value falls back`() {
        assertEquals(School.HANBALI, "nonsense".toEnumOr(School.HANBALI))
        assertEquals(School.MALIKI, "MALIKI".toEnumOr(School.HANBALI))
    }
}

class PrayerScheduleTest {
    private val date = LocalDate.of(2026, 11, 5)
    private val config = PrayerConfig(latitude = 24.8607, longitude = 67.0011)
    private val day = calculatePrayerDay(24.8607, 67.0011, config.method, config.school, date)
    private val nothingPrayed = { _: LocalDate, _: Prayer -> false }

    private fun next(
        now: java.util.Date,
        config: PrayerConfig = this.config,
        hasPrayed: (LocalDate, Prayer) -> Boolean = nothingPrayed,
    ) = nextPrayerEvent(config, now, date, hasPrayed)

    private fun minutes(n: Long) = n * 60_000

    @Test
    fun `no location means no alerts`() {
        assertEquals(null, next(day.fajr, PrayerConfig()))
    }

    @Test
    fun `reminder comes the set time before the prayer ends`() {
        val event = next(java.util.Date(day.asr.time + minutes(1)))!!

        assertEquals(Prayer.ASR, event.prayer)
        assertTrue(event.isReminder)
        assertEquals(day.maghrib.time - minutes(30), event.time.time)
        assertEquals(day.maghrib, event.endsAt)
    }

    @Test
    fun `reminder is skipped once marked as prayed`() {
        val event = next(java.util.Date(day.asr.time + minutes(1)), hasPrayed = { _, p -> p == Prayer.ASR })!!

        assertEquals(Prayer.MAGHRIB, event.prayer)
        assertEquals(false, event.isReminder)
    }

    @Test
    fun `turning a prayer off skips its alert`() {
        val alerts = config.alerts + (Prayer.DHUHR to PrayerAlert(notify = false, reminderMinutes = REMINDER_OFF))
        val event = next(java.util.Date(day.sunrise.time), config.copy(alerts = alerts))!!

        assertEquals(Prayer.ASR, event.prayer)
    }

    @Test
    fun `a reminder longer than the prayer window is dropped`() {
        val alerts = config.alerts + (Prayer.FAJR to PrayerAlert(notify = false, reminderMinutes = 600))
        val event = next(java.util.Date(day.fajr.time - minutes(1)), config.copy(alerts = alerts))!!

        assertEquals(Prayer.DHUHR, event.prayer)
    }

    @Test
    fun `period runs from one prayer to the next`() {
        val period = currentPrayerPeriod(config, java.util.Date(day.asr.time + minutes(5)), date)!!

        assertEquals(Prayer.ASR, period.current)
        assertEquals(Prayer.MAGHRIB, period.next)
        assertEquals(day.maghrib, period.nextStart)
    }

    @Test
    fun `before fajr the period is last night's isha`() {
        val period = currentPrayerPeriod(config, java.util.Date(day.fajr.time - minutes(5)), date)!!

        assertEquals(Prayer.ISHA, period.current)
        assertEquals(Prayer.FAJR, period.next)
        assertEquals(day.fajr, period.nextStart)
    }

    @Test
    fun `after isha the next prayer is tomorrow's fajr`() {
        val period = currentPrayerPeriod(config, java.util.Date(day.isha.time + minutes(5)), date)!!

        assertEquals(Prayer.FAJR, period.next)
        assertTrue(period.nextStart.after(day.isha))
    }

    @Test
    fun `fajr's window ends at sunrise`() {
        val window = currentPrayerWindow(config, java.util.Date(day.fajr.time + minutes(5)), date)!!

        assertEquals(Prayer.FAJR, window.prayer)
        assertTrue(window.active)
        assertEquals(day.fajr, window.start)
        assertEquals(day.sunrise, window.end)
    }

    @Test
    fun `between sunrise and dhuhr it counts down to dhuhr`() {
        val window = currentPrayerWindow(config, java.util.Date(day.sunrise.time + minutes(5)), date)!!

        assertEquals(Prayer.DHUHR, window.prayer)
        assertEquals(false, window.active)
        assertEquals(day.sunrise, window.start)
        assertEquals(day.dhuhr, window.end)
    }

    @Test
    fun `asr's window ends at maghrib`() {
        val window = currentPrayerWindow(config, java.util.Date(day.asr.time + minutes(5)), date)!!

        assertEquals(Prayer.ASR, window.prayer)
        assertTrue(window.active)
        assertEquals(day.maghrib, window.end)
    }

    @Test
    fun `isha's window runs into the next morning`() {
        val window = currentPrayerWindow(config, java.util.Date(day.isha.time + minutes(5)), date)!!

        assertEquals(Prayer.ISHA, window.prayer)
        assertTrue(window.end.after(day.isha))
        assertTrue(window.progress(java.util.Date(day.isha.time + minutes(5))) < 0.1f)
    }

    @Test
    fun `before dawn it is still last night's isha`() {
        val window = currentPrayerWindow(config, java.util.Date(day.fajr.time - minutes(5)), date)!!

        assertEquals(Prayer.ISHA, window.prayer)
        assertEquals(day.fajr, window.end)
    }

    @Test
    fun `isha before dawn belongs to yesterday`() {
        val beforeDawn = currentPrayerWindow(config, java.util.Date(day.fajr.time - minutes(5)), date)!!
        val evening = currentPrayerWindow(config, java.util.Date(day.isha.time + minutes(5)), date)!!

        assertEquals(date.minusDays(1), beforeDawn.day)
        assertEquals(date, evening.day)
    }

    @Test
    fun `marking the current prayer hides its window`() {
        val window = currentPrayerWindow(config, java.util.Date(day.asr.time + minutes(5)), date)!!

        assertTrue(window.isPrayed { d, p -> d == date && p == Prayer.ASR })
        assertEquals(false, window.isPrayed { _, p -> p == Prayer.DHUHR })
    }

    @Test
    fun `the next prayer starts unmarked`() {
        val marked = { _: LocalDate, p: Prayer -> p == Prayer.ASR }
        val maghrib = currentPrayerWindow(config, java.util.Date(day.maghrib.time + minutes(1)), date)!!

        assertEquals(false, maghrib.isPrayed(marked))
    }

    @Test
    fun `marking fajr covers the gap until dhuhr`() {
        val gap = currentPrayerWindow(config, java.util.Date(day.sunrise.time + minutes(5)), date)!!

        assertTrue(gap.isPrayed { _, p -> p == Prayer.FAJR })
        assertEquals(false, gap.isPrayed(nothingPrayed))
    }

    @Test
    fun `the watch keeps the phone's alert choices`() {
        val alerts = WearSync.alertsFrom("DHUHR,ISHA")

        assertTrue(alerts.getValue(Prayer.ISHA).notify)
        assertEquals(false, alerts.getValue(Prayer.FAJR).notify)
        assertTrue(WearSync.alertsFrom(null).values.all { it.notify })
        assertTrue(WearSync.alertsFrom("").values.none { it.notify })
    }

    @Test
    fun `no location means no window`() {
        assertEquals(null, currentPrayerWindow(PrayerConfig(), day.asr, date))
    }

    @Test
    fun `time left reads naturally`() {
        assertEquals("1h 09m", formatTimeLeft(minutes(69)))
        assertEquals("9m", formatTimeLeft(minutes(9)))
        assertEquals("1m", formatTimeLeft(10_000))
        assertEquals("0m", formatTimeLeft(-5_000))
    }

    @Test
    fun `isha reminder before dawn belongs to the previous day`() {
        val beforeDawn = java.util.Date(day.fajr.time - minutes(90))
        val event = next(beforeDawn)!!

        assertEquals(Prayer.ISHA, event.prayer)
        assertTrue(event.isReminder)
        assertEquals(date.minusDays(1), event.day)
        assertEquals(day.fajr, event.endsAt)
    }
}
