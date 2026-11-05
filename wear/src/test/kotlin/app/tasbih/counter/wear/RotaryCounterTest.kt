package app.tasbih.counter.wear

import org.junit.Assert.assertEquals
import org.junit.Test

class RotaryCounterTest {
    private val counter = RotaryCounter(pixelsPerCount = 100f)

    private fun bezel(pixels: Float) = counter.onScroll(pixels, detented = true)
    private fun crown(pixels: Float) = counter.onScroll(pixels, detented = false)

    @Test
    fun `each clockwise bezel click counts once whatever its size`() {
        assertEquals(1, bezel(1f))
        assertEquals(1, bezel(500f))
    }

    @Test
    fun `counter-clockwise rotation never counts`() {
        assertEquals(0, bezel(-1f))
        assertEquals(0, crown(-500f))
    }

    @Test
    fun `smooth input adds up to whole counts and keeps the remainder`() {
        assertEquals(0, crown(60f))
        assertEquals(1, crown(60f))
        assertEquals(0, crown(60f))
        assertEquals(1, crown(80f))
        assertEquals(3, crown(300f))
    }

    @Test
    fun `turning back discards partial smooth rotation`() {
        crown(90f)
        crown(-1f)

        assertEquals(0, crown(90f))
    }
}
