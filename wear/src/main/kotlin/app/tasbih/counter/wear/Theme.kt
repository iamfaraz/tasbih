package app.tasbih.counter.wear

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Typography
import app.tasbih.counter.shared.ui.SansFamily

val Sand = Color(0xFFF0DCAE)
val Gold = Color(0xFFE3B765)
val Teal = Color(0xFF7CC4BC)
val BeadEmpty = Color(0xFF2B403C)
private val Ink = Color(0xFF12302E)
private val Cream = Color(0xFFF6EEDF)
private val CardDark = Color(0xFF182A28)

private val scheme = ColorScheme(
    primary = Sand,
    onPrimary = Ink,
    primaryContainer = Teal,
    onPrimaryContainer = Ink,
    surfaceContainerLow = CardDark,
    surfaceContainer = CardDark,
    surfaceContainerHigh = BeadEmpty,
    onSurface = Cream,
    onSurfaceVariant = Color(0xFFA7B5B0),
    background = Color.Black,
    onBackground = Cream,
)

/** The phone's palette on the watch's true black, which is kindest to its OLED screen. */
@Composable
fun TasbihWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = Typography(SansFamily), content = content)
}
