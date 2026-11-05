package app.tasbih.counter.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.tasbih.counter.shared.R
import app.tasbih.counter.shared.RingCharge
import app.tasbih.counter.shared.RingSummary

@Composable
fun ringSummaryText(summary: RingSummary): String {
    val level = summary.batteryLevel
    return when {
        !summary.paired -> stringResource(R.string.ring_not_paired)
        !summary.connected -> stringResource(R.string.ring_searching)
        summary.charge == RingCharge.FULL -> stringResource(R.string.ring_full)
        level == null -> stringResource(R.string.ring_connected)
        summary.charge == RingCharge.CHARGING -> stringResource(R.string.ring_charging, level)
        summary.charge == RingCharge.LOW -> stringResource(R.string.ring_low, level)
        else -> stringResource(R.string.ring_connected_battery, level)
    }
}
