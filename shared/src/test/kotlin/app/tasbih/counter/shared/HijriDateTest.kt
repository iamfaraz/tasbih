package app.tasbih.counter.shared

import app.tasbih.counter.shared.ui.hijriDate
import app.tasbih.counter.shared.ui.regionalHijriAdjustment
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class HijriDateTest {
    private val date = LocalDate.of(2026, 11, 5)

    @Test
    fun `unadjusted date follows umm al-qura`() {
        assertEquals("25 Jumada al-Ula 1448", hijriDate(date, afterMaghrib = false, adjustment = 0))
    }

    @Test
    fun `adjustment moves the date`() {
        assertEquals("24 Jumada al-Ula 1448", hijriDate(date, afterMaghrib = false, adjustment = -1))
        assertEquals("27 Jumada al-Ula 1448", hijriDate(date, afterMaghrib = false, adjustment = 2))
    }

    @Test
    fun `the date moves on at maghrib`() {
        assertEquals("25 Jumada al-Ula 1448", hijriDate(date, afterMaghrib = true, adjustment = -1))
    }

    @Test
    fun `adjustment carries across a month end`() {
        val first = hijriDate(LocalDate.of(2026, 11, 11), afterMaghrib = false, adjustment = 0)
        val back = hijriDate(LocalDate.of(2026, 11, 11), afterMaghrib = false, adjustment = -2)

        assertEquals("1 Jumada al-Akhirah 1448", first)
        assertEquals("29 Jumada al-Ula 1448", back)
    }

    @Test
    fun `region sets the default`() {
        assertEquals(-1, regionalHijriAdjustment("PK"))
        assertEquals(0, regionalHijriAdjustment("SA"))
        assertEquals(0, regionalHijriAdjustment(null))
    }
}
