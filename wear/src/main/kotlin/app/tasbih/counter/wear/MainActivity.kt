package app.tasbih.counter.wear

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import app.tasbih.counter.shared.ROUND_SIZE
import app.tasbih.counter.shared.TasbihApplication
import app.tasbih.counter.shared.TasbihRepository
import app.tasbih.counter.shared.TasbihSettings
import app.tasbih.counter.shared.ui.ArabicFamily
import app.tasbih.counter.shared.ui.BeadRing
import app.tasbih.counter.shared.ui.dhikrName
import app.tasbih.counter.shared.vibrateMilestone
import app.tasbih.counter.shared.R as SharedR

/** Declared by some watches whose rotary input clicks. */
private const val FEATURE_DETENTED_ROTARY = "android.hardware.rotaryencoder.lowres"

/**
 * The Galaxy Watch6 Classic doesn't declare [FEATURE_DETENTED_ROTARY], so also
 * go by the input device, which it names "detent_bezel".
 */
private fun isDetented(context: Context, inputDeviceId: Int): Boolean =
    context.packageManager.hasSystemFeature(FEATURE_DETENTED_ROTARY) ||
        InputDevice.getDevice(inputDeviceId)?.name?.contains("detent", ignoreCase = true) == true

/** How far a smooth crown turns for one count. */
private val CROWN_DISTANCE_PER_COUNT = 40.dp

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    /** Asked here because the setting is switched on from the phone. */
    override fun onResume() {
        super.onResume()
        val config = (application as TasbihApplication).prayerStore.config.value
        val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (config.watchAlerts && !granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        WatchPrayerAlerts.schedule(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as TasbihApplication
        setContent {
            TasbihWearTheme {
                CounterScreen(app.repository, app.settings)
            }
        }
    }
}

@Composable
private fun CounterScreen(repository: TasbihRepository, settings: TasbihSettings) {
    val states by repository.states.collectAsStateWithLifecycle()
    val values by settings.values.collectAsStateWithLifecycle()
    val selected = values.selected
    val state = states.getValue(selected)
    val view = LocalView.current
    val context = LocalContext.current
    val crownPixelsPerCount = with(LocalDensity.current) { CROWN_DISTANCE_PER_COUNT.toPx() }
    val rotary = remember { RotaryCounter(crownPixelsPerCount) }
    val focusRequester = remember { FocusRequester() }
    var confirmingReset by remember { mutableStateOf(false) }
    val description = stringResource(SharedR.string.count_description, state.total)

    // A bezel click is already felt, so only taps get a tick.
    fun count(by: Int, tickEachCount: Boolean) {
        if (by == 0) return
        when {
            repository.count(by.toLong()) -> context.vibrateMilestone()
            tickEachCount -> view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    DisposableEffect(values.keepScreenOn) {
        view.keepScreenOn = values.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    // Rotary events only reach the focused node, and focus can be lost across
    // screen-off, so take it back every time the screen resumes.
    LifecycleResumeEffect(Unit) {
        focusRequester.requestFocus()
        onPauseOrDispose {}
    }

    // The reset dialog takes input while open but closing it doesn't trigger a resume.
    LaunchedEffect(confirmingReset) {
        if (!confirmingReset) focusRequester.requestFocus()
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .semantics { contentDescription = description }
            .onRotaryScrollEvent { event ->
                val detented = isDetented(context, event.inputDeviceId)
                count(rotary.onScroll(event.verticalScrollPixels, detented), tickEachCount = false)
                true
            }
            .focusRequester(focusRequester)
            .focusable()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onLongClick = {
                    repository.undo(selected)
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                },
                onClick = { count(1, tickEachCount = true) },
            ),
    ) {
        val roundSize = selected.afterPrayerTarget?.takeIf { values.afterPrayerMode } ?: ROUND_SIZE
        BeadRing(
            filled = state.beadsFilled(roundSize),
            total = roundSize.toInt(),
            emptyColor = BeadEmpty,
            filledColor = Teal,
            currentColor = Gold,
            modifier = Modifier.fillMaxSize().padding(3.dp),
        )
        CompactButton(
            onClick = { context.startActivity(Intent(context, SettingsActivity::class.java)) },
            icon = {
                Icon(painterResource(SharedR.drawable.ic_settings), stringResource(SharedR.string.settings))
            },
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                dhikrName(selected, values),
                fontFamily = ArabicFamily,
                fontSize = 19.sp,
                color = Sand,
                maxLines = 1,
                modifier = Modifier
                    .clickable { settings.update { it.nextChoice() } }
                    .padding(horizontal = 12.dp, vertical = 2.dp),
            )
            Text(text = state.total.toString(), fontSize = 40.sp, fontWeight = FontWeight.SemiBold)
        }
        CompactButton(
            onClick = { confirmingReset = true },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
        ) {
            Text(stringResource(SharedR.string.reset))
        }
    }

    AlertDialog(
        visible = confirmingReset,
        onDismissRequest = { confirmingReset = false },
        title = { Text(stringResource(SharedR.string.reset_question)) },
        confirmButton = {
            AlertDialogDefaults.ConfirmButton(onClick = {
                repository.reset(selected)
                confirmingReset = false
            })
        },
        dismissButton = {
            AlertDialogDefaults.DismissButton(onClick = { confirmingReset = false })
        },
    )
}
