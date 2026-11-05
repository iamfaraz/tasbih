package app.tasbih.counter.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tasbih.counter.shared.PrayerStore
import app.tasbih.counter.shared.ROUND_SIZE
import app.tasbih.counter.shared.TasbihRepository
import app.tasbih.counter.shared.TasbihSettings
import app.tasbih.counter.shared.ui.ArabicFamily
import app.tasbih.counter.shared.ui.BeadRing
import app.tasbih.counter.shared.ui.dhikrName
import app.tasbih.counter.shared.ui.hijriDateNow
import app.tasbih.counter.shared.R as SharedR

@Composable
fun TasbihScreen(repository: TasbihRepository, settings: TasbihSettings, store: PrayerStore) {
    val states by repository.states.collectAsStateWithLifecycle()
    val values by settings.values.collectAsStateWithLifecycle()
    val config by store.config.collectAsStateWithLifecycle()
    val selected = values.selected
    val state = states.getValue(selected)
    var confirmingReset by remember { mutableStateOf(false) }
    var showingDate by remember { mutableStateOf(false) }
    var choosingDhikr by remember { mutableStateOf(false) }
    val roundSize = selected.afterPrayerTarget?.takeIf { values.afterPrayerMode } ?: ROUND_SIZE

    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(SharedR.string.tab_tasbih)) {
            Text(
                config.hijriDateNow(),
                color = colors.muted,
                modifier = Modifier
                    .clip(PillShape)
                    .clickable { showingDate = true }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            BeadRing(
                filled = state.beadsFilled(roundSize),
                total = roundSize.toInt(),
                emptyColor = colors.beadEmpty,
                filledColor = colors.accent,
                currentColor = colors.gold,
                modifier = Modifier.widthIn(max = 340.dp).aspectRatio(1f),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    dhikrName(selected, values),
                    fontFamily = ArabicFamily,
                    fontSize = 28.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 40.dp),
                )
                Text(state.total.toString(), fontSize = 72.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(SharedR.string.of_round, roundSize), color = colors.muted)
            }
        }

        if (!selected.isCustom) {
            Text(
                stringResource(selected.transliterationRes),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            )
            Text(
                stringResource(selected.meaningRes),
                color = colors.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(start = 20.dp, end = 20.dp, top = 14.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.card)
                .clickable { choosingDhikr = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                if (values.afterPrayerMode) stringResource(SharedR.string.sequence_title) else dhikrName(selected, values),
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                modifier = Modifier.weight(1f).padding(end = 12.dp),
            )
            Text(stringResource(SharedR.string.change_dhikr), color = colors.accent, fontWeight = FontWeight.SemiBold)
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Text(
                stringResource(SharedR.string.all_time, state.allTime),
                color = colors.muted,
                modifier = Modifier.weight(1f),
            )
            Pill(stringResource(SharedR.string.undo)) { repository.undo(selected) }
            Pill(stringResource(SharedR.string.reset)) { confirmingReset = true }
        }
    }

    if (showingDate) HijriDateDialog(store, onDismiss = { showingDate = false })
    if (choosingDhikr) DhikrDialog(settings, onDismiss = { choosingDhikr = false })

    if (confirmingReset) {
        AlertDialog(
            onDismissRequest = { confirmingReset = false },
            containerColor = colors.card,
            shape = CardShape,
            title = { Text(stringResource(SharedR.string.reset_question)) },
            text = { Text(dhikrName(selected, values), fontFamily = ArabicFamily, fontSize = 22.sp) },
            confirmButton = {
                TextButton(onClick = {
                    repository.reset(selected)
                    confirmingReset = false
                }) { Text(stringResource(SharedR.string.reset)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingReset = false }) {
                    Text(stringResource(SharedR.string.cancel))
                }
            },
        )
    }
}
