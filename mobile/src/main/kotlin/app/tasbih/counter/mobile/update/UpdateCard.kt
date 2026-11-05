package app.tasbih.counter.mobile.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tasbih.counter.mobile.Pill
import app.tasbih.counter.mobile.SoftCard
import app.tasbih.counter.mobile.colors
import app.tasbih.counter.shared.R

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UpdateCard() {
    val context = LocalContext.current
    val state by AppUpdater.state.collectAsStateWithLifecycle()
    var auto by remember { mutableStateOf(AppUpdater.autoCheckEnabled(context)) }
    val current = remember { AppUpdater.currentVersion(context) }

    SoftCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.update_title), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.update_version, current), color = colors.muted)
        Spacer(Modifier.height(10.dp))
        when (val now = state) {
            UpdateState.Idle -> Unit
            UpdateState.Checking -> Text(stringResource(R.string.update_checking))
            UpdateState.UpToDate -> Text(stringResource(R.string.update_up_to_date))
            is UpdateState.Available -> Text(
                stringResource(R.string.update_available, now.release.version),
                fontWeight = FontWeight.Medium,
            )
            is UpdateState.Working -> Text(
                if (now.percent != null) "${stringResource(now.messageRes)} ${now.percent}%" else stringResource(now.messageRes)
            )
            is UpdateState.Finished -> Text(stringResource(now.messageRes))
            is UpdateState.Failed -> Text(stringResource(now.messageRes))
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val available = state as? UpdateState.Available
            if (available != null) {
                Pill(stringResource(R.string.update_phone), selected = true) {
                    AppUpdater.updatePhone(context, available.release)
                }
                Pill(stringResource(R.string.update_watch), selected = true) {
                    AppUpdater.updateWatch(context, available.release)
                }
            } else if (state !is UpdateState.Working && state != UpdateState.Checking) {
                Pill(stringResource(R.string.update_check)) { AppUpdater.check(context) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.update_auto), modifier = Modifier.weight(1f))
            Switch(checked = auto, onCheckedChange = {
                auto = it
                AppUpdater.setAutoCheck(context, it)
            })
        }
    }
}
