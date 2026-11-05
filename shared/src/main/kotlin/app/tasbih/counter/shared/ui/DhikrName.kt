package app.tasbih.counter.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.tasbih.counter.shared.Settings
import app.tasbih.counter.shared.Tasbih

/** The name to show for [tasbih], which for a custom dhikr is whatever was typed. */
@Composable
fun dhikrName(tasbih: Tasbih, settings: Settings): String =
    settings.customNames[tasbih] ?: stringResource(tasbih.nameRes)
