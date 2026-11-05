package app.tasbih.counter.shared.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * A string of [total] prayer beads, starting at the top and running clockwise.
 * The first [filled] are lit, and the latest one stands out.
 */
@Composable
fun BeadRing(
    filled: Int,
    total: Int,
    emptyColor: Color,
    filledColor: Color,
    currentColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val spacing = 2 * PI.toFloat() / total
        val outer = min(size.width, size.height) / 2
        val bead = outer * spacing * 0.33f
        val current = bead * 1.35f
        val ring = outer - current
        repeat(total) { index ->
            val angle = -PI.toFloat() / 2 + index * spacing
            val latest = index == filled - 1
            drawCircle(
                color = when {
                    latest -> currentColor
                    index < filled -> filledColor
                    else -> emptyColor
                },
                radius = if (latest) current else bead,
                center = Offset(center.x + ring * cos(angle), center.y + ring * sin(angle)),
            )
        }
    }
}
