package app.tasbih.counter.wear

import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import app.tasbih.counter.shared.TasbihApplication
import app.tasbih.counter.shared.today
import java.util.Date
import app.tasbih.counter.shared.R as SharedR

/** Today's times, worked out from the location and school sent by the phone. */
class PrayerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = (application as TasbihApplication).prayerStore
        setContent {
            TasbihWearTheme {
                AppScaffold {
                    val config by store.config.collectAsStateWithLifecycle()
                    val entries = config.today()?.entries.orEmpty()
                    val now = Date()
                    val next = entries.firstOrNull { it.second.after(now) }
                    val timeFormat = DateFormat.getTimeFormat(LocalContext.current)
                    val listState = rememberTransformingLazyColumnState()
                    ScreenScaffold(scrollState = listState) { contentPadding ->
                        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
                            item {
                                ListHeader { Text(stringResource(SharedR.string.prayer_times)) }
                            }
                            if (entries.isEmpty()) {
                                item {
                                    Text(
                                        stringResource(SharedR.string.open_on_phone),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                            items(entries) { entry ->
                                val color = if (entry == next) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                                    Text(stringResource(entry.first), color = color, modifier = Modifier.weight(1f))
                                    Text(timeFormat.format(entry.second), color = color)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
