package app.tasbih.counter.mobile

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import app.tasbih.counter.shared.ui.SansFamily

data class TasbihColors(
    val paper: Color,
    val card: Color,
    val sand: Color,
    val ink: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val gold: Color,
    val line: Color,
    val beadEmpty: Color,
)

private val Light = TasbihColors(
    paper = Color(0xFFFFFDF8),
    card = Color(0xFFFBF2E3),
    sand = Color(0xFFF0DCAE),
    ink = Color(0xFF12302E),
    muted = Color(0xFF5E716D),
    accent = Color(0xFF17625E),
    onAccent = Color(0xFFFFFDF8),
    gold = Color(0xFFC28F3E),
    line = Color(0xFFF3EADB),
    beadEmpty = Color(0xFFEBDDBF),
)

private val Dark = TasbihColors(
    paper = Color(0xFF0F1C1B),
    card = Color(0xFF182A28),
    sand = Color(0xFF2B4340),
    ink = Color(0xFFF6EEDF),
    muted = Color(0xFFA7B5B0),
    accent = Color(0xFF7CC4BC),
    onAccent = Color(0xFF0F1C1B),
    gold = Color(0xFFE3B765),
    line = Color(0xFF20312F),
    beadEmpty = Color(0xFF2B403C),
)

val LocalColors = staticCompositionLocalOf { Light }

val colors: TasbihColors
    @Composable get() = LocalColors.current

private val typography = Typography().run {
    copy(
        headlineSmall = headlineSmall.copy(fontFamily = SansFamily),
        titleMedium = titleMedium.copy(fontFamily = SansFamily),
        bodyLarge = bodyLarge.copy(fontFamily = SansFamily),
        bodyMedium = bodyMedium.copy(fontFamily = SansFamily),
        labelLarge = labelLarge.copy(fontFamily = SansFamily),
    )
}

@Composable
fun TasbihTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val palette = if (dark) Dark else Light
    val scheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = palette.accent,
        onPrimary = palette.onAccent,
        background = palette.paper,
        surface = palette.paper,
        onSurface = palette.ink,
        onSurfaceVariant = palette.muted,
        surfaceContainerHigh = palette.card,
        surfaceContainerHighest = palette.sand,
        outline = palette.muted,
    )
    CompositionLocalProvider(LocalColors provides palette) {
        MaterialTheme(colorScheme = scheme, typography = typography) {
            ProvideTextStyle(TextStyle(fontFamily = SansFamily, color = palette.ink), content)
        }
    }
}
