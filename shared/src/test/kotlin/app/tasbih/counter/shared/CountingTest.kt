package app.tasbih.counter.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CountingTest {
    @Test
    fun `fires on each multiple and nowhere else`() {
        val fired = (0L until 100L).filter { reachesMultiple(it, 1, 33) }.map { it + 1 }

        assertEquals(listOf(33L, 66L, 99L), fired)
    }

    @Test
    fun `a jump past a multiple still fires`() {
        assertTrue(reachesMultiple(before = 32, added = 3, interval = 33))
    }

    @Test
    fun `leaving a multiple does not fire again`() {
        assertFalse(reachesMultiple(before = 33, added = 1, interval = 33))
    }

    @Test
    fun `off never fires`() {
        assertFalse(reachesMultiple(before = 32, added = 1, interval = VIBRATION_OFF))
    }

    @Test
    fun `after-prayer sequence is 33 33 34 and wraps round`() {
        val sequence = generateSequence(Tasbih.SUBHAN_ALLAH) { it.nextAfterPrayer }.take(4).toList()

        assertEquals(
            listOf(Tasbih.SUBHAN_ALLAH, Tasbih.ALHAMDULILLAH, Tasbih.ALLAHU_AKBAR, Tasbih.SUBHAN_ALLAH),
            sequence,
        )
        assertEquals(listOf(33L, 33L, 34L), sequence.take(3).map { it.afterPrayerTarget })
    }

    @Test
    fun `milestones follow the sequence, then the vibration setting`() {
        val sequence = Settings(vibrationInterval = 100, afterPrayerMode = true)

        assertEquals(34L, sequence.milestoneInterval(Tasbih.ALLAHU_AKBAR))
        assertEquals(100L, sequence.milestoneInterval(Tasbih.SALAWAT))
        assertEquals(100L, sequence.copy(afterPrayerMode = false).milestoneInterval(Tasbih.ALLAHU_AKBAR))
    }

    @Test
    fun `custom dhikr fill the free slots`() {
        val two = Settings().addCustom("Ya Latif").addCustom("  Ya   Rahman ")

        assertEquals(listOf("Ya Latif", "Ya Rahman"), two.customNames.values.toList())
        assertEquals(two, two.addCustom("   "))
        assertEquals(Tasbih.CUSTOM_1, two.removeCustom(Tasbih.CUSTOM_2).addCustom("x").customNames.keys.first())
    }

    @Test
    fun `slots run out without an error`() {
        val full = (1..10).fold(Settings()) { settings, n -> settings.addCustom("dhikr $n") }

        assertEquals(8, full.customNames.size)
    }

    @Test
    fun `removing the current custom dhikr goes back to the sequence`() {
        val settings = Settings().addCustom("Ya Latif").useSingle(Tasbih.CUSTOM_1).removeCustom(Tasbih.CUSTOM_1)

        assertTrue(settings.afterPrayerMode)
        assertEquals(Tasbih.SUBHAN_ALLAH, settings.selected)
    }

    @Test
    fun `custom names survive saving and syncing`() {
        val names = Settings().addCustom("يا لطيف").addCustom("Ya Rahman").customNames

        assertEquals(names, decodeCustomNames(encodeCustomNames(names)))
        assertEquals(emptyMap<Tasbih, String>(), decodeCustomNames(null))
    }

    @Test
    fun `the watch steps through every choice and back to the sequence`() {
        var settings = Settings().addCustom("Ya Latif").useSequence()
        val seen = mutableListOf<Tasbih>()
        do {
            settings = settings.nextChoice()
            if (!settings.afterPrayerMode) seen += settings.selected
        } while (!settings.afterPrayerMode)

        assertEquals(Tasbih.builtIn + Tasbih.CUSTOM_1, seen)
    }

    @Test
    fun `tasbih ids are unique`() {
        assertEquals(Tasbih.entries.size, Tasbih.entries.map { it.id }.toSet().size)
    }
}
