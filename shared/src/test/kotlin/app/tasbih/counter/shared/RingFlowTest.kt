package app.tasbih.counter.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** How counts move through the sequence, on the watch and from the ring. */
class RingFlowTest {
    private val ring = "phone"
    private var states = Tasbih.entries.associateWith { TasbihState() }
    private var settings = Settings(afterPrayerMode = true)

    private fun press(times: Int = 1): Boolean {
        var reached = false
        repeat(times) {
            val step = countOne(states, settings, ring)
            states = step.states
            settings = step.settings
            reached = step.reached
        }
        return reached
    }

    private fun assertAt(tasbih: Tasbih, total: Long) {
        assertEquals(tasbih, settings.selected)
        assertEquals(total, states.getValue(tasbih).total)
    }

    @Test
    fun `ring 33 is the last subhan allah`() {
        press(32)
        assertAt(Tasbih.SUBHAN_ALLAH, 32)

        assertTrue(press())
        assertEquals(33, states.getValue(Tasbih.SUBHAN_ALLAH).total)
        assertEquals(Tasbih.ALHAMDULILLAH, settings.selected)
    }

    @Test
    fun `ring 34 is alhamdulillah 1`() {
        press(34)
        assertAt(Tasbih.ALHAMDULILLAH, 1)
    }

    @Test
    fun `ring 67 is allahu akbar 1`() {
        press(67)
        assertAt(Tasbih.ALLAHU_AKBAR, 1)
    }

    @Test
    fun `ring 100 finishes and 101 starts subhan allah at 1`() {
        press(99)
        assertAt(Tasbih.ALLAHU_AKBAR, 33)

        assertTrue(press())
        assertAt(Tasbih.SUBHAN_ALLAH, 0)
        assertEquals(0, states.getValue(Tasbih.ALLAHU_AKBAR).total)

        assertFalse(press())
        assertAt(Tasbih.SUBHAN_ALLAH, 1)
    }

    @Test
    fun `milestones fall on 33, 66 and 100 only`() {
        val hits = (1..100).filter { press() }

        assertEquals(listOf(33, 66, 100), hits)
    }

    @Test
    fun `a synced device sees the same milestones`() {
        val hits = (1..100).filter {
            val before = states
            press()
            milestoneReached(before, states, settings)
        }

        assertEquals(listOf(33, 66, 100), hits)
    }

    @Test
    fun `a reset is not a milestone`() {
        press(40)

        assertFalse(milestoneReached(states, syncToRing(states, settings, 0, ring).states, settings))
    }

    @Test
    fun `a batch that crosses a milestone still counts`() {
        val before = states
        press(40)

        assertTrue(milestoneReached(before, states, settings))
    }

    @Test
    fun `all time totals survive a finished sequence`() {
        press(100)

        assertEquals(33, states.getValue(Tasbih.SUBHAN_ALLAH).allTime)
        assertEquals(33, states.getValue(Tasbih.ALHAMDULILLAH).allTime)
        assertEquals(34, states.getValue(Tasbih.ALLAHU_AKBAR).allTime)
    }

    private fun ring(count: Long) {
        val step = syncToRing(states, settings, count, ring)
        states = step.states
        settings = step.settings
    }

    private fun assertTotals(subhan: Long, alhamdu: Long, akbar: Long) {
        assertEquals(subhan, states.getValue(Tasbih.SUBHAN_ALLAH).total)
        assertEquals(alhamdu, states.getValue(Tasbih.ALHAMDULILLAH).total)
        assertEquals(akbar, states.getValue(Tasbih.ALLAHU_AKBAR).total)
    }

    @Test
    fun `ring 10 is subhan allah 10`() {
        ring(10)

        assertTotals(10, 0, 0)
        assertEquals(Tasbih.SUBHAN_ALLAH, settings.selected)
    }

    @Test
    fun `ring 70 fills the first two and allahu akbar reaches 4`() {
        ring(70)

        assertTotals(33, 33, 4)
        assertEquals(Tasbih.ALLAHU_AKBAR, settings.selected)
    }

    @Test
    fun `ring 34 and 67 start the next dhikr`() {
        ring(34)
        assertTotals(33, 1, 0)
        assertEquals(Tasbih.ALHAMDULILLAH, settings.selected)

        ring(67)
        assertTotals(33, 33, 1)
        assertEquals(Tasbih.ALLAHU_AKBAR, settings.selected)
    }

    @Test
    fun `ring takes priority over counts made on the watch`() {
        ring(10)
        press(5)
        assertTotals(15, 0, 0)

        ring(11)

        assertTotals(11, 0, 0)
    }

    @Test
    fun `ring reset clears the sequence and keeps all time`() {
        ring(40)
        ring(0)

        assertTotals(0, 0, 0)
        assertEquals(Tasbih.SUBHAN_ALLAH, settings.selected)
        assertEquals(33, states.getValue(Tasbih.SUBHAN_ALLAH).allTime)
        assertEquals(7, states.getValue(Tasbih.ALHAMDULILLAH).allTime)
    }

    @Test
    fun `ring 100 finishes the sequence and 101 starts again`() {
        ring(100)
        assertTotals(0, 0, 0)

        ring(101)
        assertTotals(1, 0, 0)
    }

    @Test
    fun `ring presses reach the same milestones`() {
        val hits = (1L..100L).filter {
            val before = states
            ring(it)
            milestoneReached(before, states, settings)
        }

        assertEquals(listOf(33L, 66L, 100L), hits)
    }

    @Test
    fun `hearing ring 100 twice counts the round once`() {
        ring(99)
        ring(100)
        val before = states
        ring(100)

        assertEquals(before, states)
        assertEquals(34, states.getValue(Tasbih.ALLAHU_AKBAR).allTime)
    }

    @Test
    fun `the same ring number twice changes nothing`() {
        ring(70)
        val before = states
        ring(70)

        assertEquals(before, states)
    }

    @Test
    fun `dhikr outside the sequence are left alone`() {
        states = states + (Tasbih.SALAWAT to TasbihState().increment(ring, 5))
        ring(70)

        assertEquals(5, states.getValue(Tasbih.SALAWAT).total)
    }

    @Test
    fun `without the sequence the ring sets the current dhikr`() {
        settings = Settings(afterPrayerMode = false, selected = Tasbih.SALAWAT)
        ring(70)

        assertEquals(70, states.getValue(Tasbih.SALAWAT).total)
        assertTotals(0, 0, 0)
    }
}
