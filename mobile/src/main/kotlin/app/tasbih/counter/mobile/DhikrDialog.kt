package app.tasbih.counter.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tasbih.counter.shared.Tasbih
import app.tasbih.counter.shared.TasbihSettings
import app.tasbih.counter.shared.ui.ArabicFamily
import app.tasbih.counter.shared.R as SharedR

/** The list of dhikr. Whatever is chosen here takes the ring's number. */
@Composable
fun DhikrDialog(settings: TasbihSettings, onDismiss: () -> Unit) {
    val values by settings.values.collectAsStateWithLifecycle()
    var newName by remember { mutableStateOf("") }
    val slotsLeft = Tasbih.entries.count { it.isCustom } > values.customNames.size

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.card,
        shape = CardShape,
        title = { Text(stringResource(SharedR.string.choose_dhikr), fontSize = 22.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
            ) {
                ChoiceRow(selected = values.afterPrayerMode, onClick = {
                    settings.update { it.useSequence() }
                    onDismiss()
                }) {
                    Text(stringResource(SharedR.string.sequence_title), fontWeight = FontWeight.SemiBold)
                    Text(stringResource(SharedR.string.sequence_note), color = colors.muted, fontSize = 13.sp)
                }
                Tasbih.builtIn.forEach { tasbih ->
                    ChoiceRow(selected = !values.afterPrayerMode && values.selected == tasbih, onClick = {
                        settings.update { it.useSingle(tasbih) }
                        onDismiss()
                    }) {
                        Text(stringResource(tasbih.nameRes), fontFamily = ArabicFamily, fontSize = 20.sp)
                        Text(stringResource(tasbih.transliterationRes), color = colors.muted, fontSize = 13.sp)
                    }
                }
                Text(
                    stringResource(SharedR.string.your_dhikr),
                    color = colors.muted,
                    modifier = Modifier.padding(top = 8.dp),
                )
                values.customNames.entries.sortedBy { it.key.ordinal }.forEach { (tasbih, name) ->
                    ChoiceRow(selected = !values.afterPrayerMode && values.selected == tasbih, onClick = {
                        settings.update { it.useSingle(tasbih) }
                        onDismiss()
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(name, fontFamily = ArabicFamily, fontSize = 20.sp, modifier = Modifier.weight(1f))
                            Pill(stringResource(SharedR.string.remove)) {
                                settings.update { it.removeCustom(tasbih) }
                            }
                        }
                    }
                }
                if (slotsLeft) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text(stringResource(SharedR.string.new_dhikr)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Pill(stringResource(SharedR.string.add_dhikr)) {
                        settings.update { it.addCustom(newName) }
                        newName = ""
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(SharedR.string.cancel)) }
        },
    )
}

@Composable
private fun ChoiceRow(selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) colors.sand.copy(alpha = 0.6f) else colors.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) { content() }
}
