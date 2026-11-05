package app.tasbih.counter.mobile

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import app.tasbih.counter.shared.Prayer

/** How the desert looks during each prayer. */
private data class Sky(val sky: Color, val light: Color, val dunes: List<Color>, val text: Color)

private val DarkText = Color(0xFF12302E)
private val LightText = Color(0xFFFBF2E3)

private fun skyFor(prayer: Prayer) = when (prayer) {
    Prayer.FAJR -> Sky(
        Color(0xFFC6D2E8), Color(0xFFFBE3C8),
        listOf(Color(0xFF9DAFCD), Color(0xFF6E86AC), Color(0xFF3E5A7C)), DarkText,
    )
    Prayer.DHUHR -> Sky(
        Color(0xFFF3E3B8), Color(0xFFFFD37E),
        listOf(Color(0xFFE6C684), Color(0xFFCFA25A), Color(0xFF17625E)), DarkText,
    )
    Prayer.ASR -> Sky(
        Color(0xFFF1D79E), Color(0xFFF4AC55),
        listOf(Color(0xFFDDAA64), Color(0xFFBC8142), Color(0xFF6E4C2C)), DarkText,
    )
    Prayer.MAGHRIB -> Sky(
        Color(0xFFF5B394), Color(0xFFF27452),
        listOf(Color(0xFFBA5642), Color(0xFF803239), Color(0xFF412031)), DarkText,
    )
    Prayer.ISHA -> Sky(
        Color(0xFF163A44), Color(0xFFF2E6C4),
        listOf(Color(0xFF1F4E57), Color(0xFF143641), Color(0xFF0C2430)), LightText,
    )
}

fun skyTextColor(prayer: Prayer): Color = skyFor(prayer).text

/** A flat desert scene, weighted to the right so text can sit on the left. */
@Composable
fun SkyScene(prayer: Prayer, modifier: Modifier = Modifier) {
    val look = skyFor(prayer)
    Canvas(modifier) {
        drawRect(look.sky)
        val orb = Offset(size.width * 0.79f, size.height * 0.42f)
        val radius = size.height * 0.15f
        if (prayer == Prayer.ISHA) {
            STARS.forEach { (x, y) ->
                drawCircle(look.light, size.height * 0.008f, Offset(size.width * x, size.height * y))
            }
            drawCircle(look.light, radius, orb)
            drawCircle(look.sky, radius * 0.86f, orb + Offset(radius * 0.42f, -radius * 0.14f))
        } else {
            drawCircle(look.light.copy(alpha = 0.3f), radius * 1.75f, orb)
            drawCircle(look.light, radius, orb)
        }
        dune(look.dunes[0], left = 0.86f, peak = 0.58f, right = 0.66f)
        dune(look.dunes[1], left = 0.98f, peak = 0.72f, right = 0.74f)
        minaret(look.dunes[2])
        dune(look.dunes[2], left = 1.02f, peak = 0.92f, right = 0.82f)
    }
}

private fun DrawScope.dune(color: Color, left: Float, peak: Float, right: Float) {
    val path = Path().apply {
        moveTo(0f, size.height * left)
        cubicTo(
            size.width * 0.35f, size.height * left,
            size.width * 0.55f, size.height * peak,
            size.width * 0.72f, size.height * peak,
        )
        cubicTo(
            size.width * 0.84f, size.height * peak,
            size.width * 0.9f, size.height * right,
            size.width, size.height * right,
        )
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.minaret(color: Color) {
    val x = size.width * 0.9f
    val width = size.height * 0.05f
    val top = size.height * 0.5f
    drawRect(color, Offset(x - width / 2, top), Size(width, size.height - top))
    drawRect(color, Offset(x - width, top + size.height * 0.08f), Size(width * 2, size.height * 0.022f))
    drawCircle(color, width * 0.62f, Offset(x, top))
}

private val STARS = listOf(
    0.5f to 0.14f, 0.6f to 0.3f, 0.66f to 0.1f, 0.92f to 0.16f, 0.95f to 0.42f, 0.55f to 0.5f,
)
