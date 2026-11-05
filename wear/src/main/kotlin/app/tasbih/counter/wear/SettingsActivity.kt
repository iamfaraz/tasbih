package app.tasbih.counter.wear

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import app.tasbih.counter.shared.TasbihApplication
import app.tasbih.counter.shared.VIBRATION_INTERVALS
import app.tasbih.counter.shared.VIBRATION_OFF
import app.tasbih.counter.shared.ui.ringSummaryText
import app.tasbih.counter.shared.R as SharedR

/** Its own activity so the system's swipe-to-dismiss returns to the counter. */
class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as TasbihApplication
        val settings = app.settings
        setContent {
            TasbihWearTheme {
                AppScaffold {
                    val values by settings.values.collectAsStateWithLifecycle()
                    val ring by app.ringSummary.collectAsStateWithLifecycle()
                    val listState = rememberTransformingLazyColumnState()
                    ScreenScaffold(scrollState = listState) { contentPadding ->
                        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
                            if (ring.paired) {
                                item {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                    ) {
                                        Text(stringResource(SharedR.string.ring), color = Sand)
                                        Text(ringSummaryText(ring), textAlign = TextAlign.Center)
                                    }
                                }
                            }
                            item {
                                Button(
                                    onClick = { startActivity(Intent(this@SettingsActivity, PrayerActivity::class.java)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text(stringResource(SharedR.string.prayer_times)) },
                                )
                            }
                            item {
                                SwitchButton(
                                    checked = values.afterPrayerMode,
                                    onCheckedChange = { on -> settings.update { it.copy(afterPrayerMode = on) } },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text(stringResource(SharedR.string.after_prayer_mode)) },
                                )
                            }
                            item {
                                SwitchButton(
                                    checked = values.keepScreenOn,
                                    onCheckedChange = { on -> settings.update { it.copy(keepScreenOn = on) } },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text(stringResource(SharedR.string.keep_screen_on)) },
                                )
                            }
                            item {
                                ListHeader { Text(stringResource(SharedR.string.vibrate_every)) }
                            }
                            items(VIBRATION_INTERVALS) { interval ->
                                RadioButton(
                                    selected = interval == values.vibrationInterval,
                                    onSelect = { settings.update { it.copy(vibrationInterval = interval) } },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = {
                                        Text(
                                            if (interval == VIBRATION_OFF) stringResource(SharedR.string.vibrate_off)
                                            else stringResource(SharedR.string.vibrate_interval, interval)
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
