package app.tasbih.counter.mobile.ring

import app.tasbih.counter.shared.RingCharge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RingProtocolTest {
    @Test
    fun `reads a live count message`() {
        assertEquals(RingMessage.Count(42), parseRingMessage("Q07,42,1793876805,0,0"))
    }

    @Test
    fun `reads a press captured from a real ring`() {
        assertEquals(RingMessage.Count(1), parseRingMessage("Q07,0001,853346,2,1"))
    }

    @Test
    fun `reads the count the ring gives when asked`() {
        assertEquals(RingMessage.Count(3), parseRingMessage("Q12,19,3,0"))
    }

    @Test
    fun `reads a battery message captured from a real ring`() {
        assertEquals(
            RingMessage.Battery(level = 3, charge = RingCharge.NONE),
            parseRingMessage("Q02,0,3,314,2024/08/09 14:14,4111,0.18.3"),
        )
    }

    @Test
    fun `reads each charge state`() {
        assertEquals(RingCharge.CHARGING, RingCharge.fromCode(1))
        assertEquals(RingCharge.FULL, RingCharge.fromCode(2))
        assertEquals(RingCharge.LOW, RingCharge.fromCode(3))
        assertEquals(RingCharge.NONE, RingCharge.fromCode(9))
    }

    @Test
    fun `rejects impossible counts`() {
        listOf("Q07,-1,1,0,0", "Q07,2000000000,1,0,0", "Q12,0,-5,0").forEach {
            assertEquals(it, RingMessage.Other, parseRingMessage(it))
        }
    }

    @Test
    fun `location is rounded before it leaves the phone`() {
        assertTrue(RingCommands.syncTime(true, 5.0, 24.86071, 67.00113).contains(",24.86,67.00"))
    }

    @Test
    fun `ignores anything else`() {
        listOf("Q01", "R01", "", "Q07", "Q07,abc", "junk").forEach {
            assertEquals(it, RingMessage.Other, parseRingMessage(it))
        }
    }

    @Test
    fun `commands have the expected shape`() {
        assertTrue(RingCommands.buzz(true).matches(Regex("P04,\\d{17},1")))
        assertTrue(RingCommands.syncTime(true, 5.0, 24.86, 67.0).matches(Regex("P01,\\d{17},0,1,5\\.0,24\\.86,67\\.00")))
    }

    @Test
    fun `prayer commands match the shape the ring accepted`() {
        assertTrue(RingCommands.prayerSetup(true, 5.0, 24.86, 67.0).matches(Regex("P09,\\d{17},1,21,1,24\\.86,67\\.00,5\\.0")))
        assertTrue(
            RingCommands.prayerReminders(listOf(true, false, true, true, true))
                .matches(Regex("P23,\\d{17},1#\\+00#\\+00:0#\\+00#\\+00:1#\\+00#\\+00:1#\\+00#\\+00:1#\\+00#\\+00"))
        )
    }
}
