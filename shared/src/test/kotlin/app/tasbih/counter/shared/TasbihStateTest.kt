package app.tasbih.counter.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TasbihStateTest {
    private val phone = "phone"
    private val watch = "watch"

    private fun TasbihState.count(device: String, times: Long) = increment(device, times)

    @Test
    fun `counts made offline on both devices add up`() {
        val shared = TasbihState().count(phone, 10)
        val onPhone = shared.count(phone, 5)
        val onWatch = shared.count(watch, 7)

        assertEquals(22, onPhone.merge(onWatch).total)
        assertEquals(22, onWatch.merge(onPhone).total)
    }

    @Test
    fun `merging the same state again changes nothing`() {
        val a = TasbihState().count(phone, 3)
        val b = TasbihState().count(watch, 4)
        val merged = a.merge(b)

        assertEquals(merged, merged.merge(a))
        assertEquals(merged, merged.merge(b))
        assertEquals(merged, merged.merge(merged))
    }

    @Test
    fun `merge order does not matter`() {
        val a = TasbihState().count(phone, 3)
        val b = a.count(watch, 4).reset()
        val c = a.count("watch2", 9)

        assertEquals(a.merge(b).merge(c), c.merge(b.merge(a)))
    }

    @Test
    fun `reset reaches the other device`() {
        val shared = TasbihState().count(phone, 10).count(watch, 10)
        val resetOnPhone = shared.reset()

        assertEquals(0, resetOnPhone.total)
        assertEquals(0, shared.merge(resetOnPhone).total)
    }

    @Test
    fun `stale state cannot undo a reset`() {
        val stale = TasbihState().count(watch, 50)
        val reset = stale.reset()

        assertEquals(0, reset.merge(stale).total)
    }

    @Test
    fun `counts made after a reset the device has not seen survive it`() {
        val shared = TasbihState().count(watch, 10)
        val resetOnPhone = shared.reset()
        val countedOnWatch = shared.count(watch, 4)

        assertEquals(4, resetOnPhone.merge(countedOnWatch).total)
    }

    @Test
    fun `counting continues from zero after a reset`() {
        assertEquals(2, TasbihState().count(phone, 10).reset().count(phone, 2).total)
    }

    @Test
    fun `resets on both devices at once leave zero`() {
        val shared = TasbihState().count(phone, 6).count(watch, 6)

        assertEquals(0, shared.reset().merge(shared.reset()).total)
    }

    @Test
    fun `beads fill and then start over`() {
        fun beadsAt(total: Long) = TasbihState().count(phone, total).beadsFilled()

        assertEquals(0, TasbihState().beadsFilled())
        assertEquals(1, beadsAt(1))
        assertEquals(33, beadsAt(33))
        assertEquals(1, beadsAt(34))
    }

    @Test
    fun `beads follow a custom round size`() {
        assertEquals(34, TasbihState().count(phone, 34).beadsFilled(34))
    }

    @Test
    fun `undo takes one off and syncs`() {
        val shared = TasbihState().count(phone, 5)
        val undone = shared.undo(phone)

        assertEquals(4, undone.total)
        assertEquals(4, shared.merge(undone).total)
    }

    @Test
    fun `undo can take back a count made on the other device`() {
        assertEquals(2, TasbihState().count(watch, 3).undo(phone).total)
    }

    @Test
    fun `undo at zero does nothing`() {
        val reset = TasbihState().count(phone, 2).reset()

        assertEquals(reset, reset.undo(phone))
        assertEquals(TasbihState(), TasbihState().undo(phone))
    }

    @Test
    fun `an undone state still encodes and decodes`() {
        val undone = TasbihState().count(watch, 3).undo(phone)

        assertEquals(undone, TasbihState.decode(undone.encode()))
    }

    @Test
    fun `all time total survives a reset`() {
        val state = TasbihState().count(phone, 10).reset().count(watch, 2)

        assertEquals(2, state.total)
        assertEquals(12, state.allTime)
    }

    @Test
    fun `encoding round trips`() {
        val state = TasbihState().count(phone, 12).count(watch, 3).reset().count(watch, 2)

        assertEquals(state, TasbihState.decode(state.encode()))
        assertEquals(TasbihState(), TasbihState.decode(TasbihState().encode()))
    }

    @Test
    fun `malformed input is rejected`() {
        listOf("junk", "a:1", "a:x:0", ":1:0", "a:1:2", "a:-1:-1", "a:1:0;").forEach {
            assertNull(it, TasbihState.decode(it))
        }
    }
}
