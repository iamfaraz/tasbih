package app.tasbih.counter.wear

/**
 * Turns rotary input into counts. Only clockwise rotation counts, and turning
 * back never subtracts.
 *
 * @param pixelsPerCount scroll distance per count on smooth inputs.
 */
class RotaryCounter(private val pixelsPerCount: Float) {
    private var accumulated = 0f

    /**
     * Returns how many counts this event adds. Positive [scrollPixels] means clockwise.
     *
     * @param detented true for inputs that click, like the Galaxy Watch Classic bezel,
     *   where each event is one click and so one count. Smooth inputs such as a
     *   Pixel Watch crown send many small events, which are added up instead.
     */
    fun onScroll(scrollPixels: Float, detented: Boolean): Int {
        if (scrollPixels <= 0f) {
            accumulated = 0f
            return 0
        }
        if (detented) return 1
        accumulated += scrollPixels
        val counts = (accumulated / pixelsPerCount).toInt()
        accumulated -= counts * pixelsPerCount
        return counts
    }
}
