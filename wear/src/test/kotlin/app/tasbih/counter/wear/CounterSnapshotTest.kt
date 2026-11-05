package app.tasbih.counter.wear

import app.tasbih.counter.shared.Settings
import app.tasbih.counter.shared.Tasbih
import app.tasbih.counter.shared.TasbihState
import org.junit.Assert.assertEquals
import org.junit.Test

class CounterSnapshotTest {
    private val states = Tasbih.entries.associateWith { TasbihState() } +
        (Tasbih.ALLAHU_AKBAR to TasbihState().increment("ring", 4))

    @Test
    fun `shows the current dhikr with its target in the sequence`() {
        val snapshot = counterSnapshot(states, Settings(afterPrayerMode = true, selected = Tasbih.ALLAHU_AKBAR))

        assertEquals(4L, snapshot.count)
        assertEquals(34L, snapshot.roundSize)
        assertEquals(4f / 34, snapshot.progress, 0.0001f)
    }

    @Test
    fun `single dhikr use rounds of 33`() {
        val snapshot = counterSnapshot(states, Settings(afterPrayerMode = false, selected = Tasbih.ALLAHU_AKBAR))

        assertEquals(33L, snapshot.roundSize)
    }

    @Test
    fun `an empty counter shows no progress`() {
        assertEquals(0f, counterSnapshot(states, Settings()).progress, 0f)
    }
}
