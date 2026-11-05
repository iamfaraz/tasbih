package app.tasbih.counter.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tasbih.counter.mobile.ring.RingService
import app.tasbih.counter.mobile.update.AppUpdater
import app.tasbih.counter.shared.TasbihApplication
import app.tasbih.counter.shared.toEnumOr
import app.tasbih.counter.shared.R as SharedR

enum class Tab(val labelRes: Int, val iconRes: Int) {
    TASBIH(SharedR.string.tab_tasbih, SharedR.drawable.ic_beads),
    PRAYER(SharedR.string.tab_prayer, SharedR.drawable.ic_crescent),
    SETTINGS(SharedR.string.settings, SharedR.drawable.ic_settings),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as TasbihApplication
        val startTab = intent.getStringExtra(EXTRA_TAB).toEnumOr(Tab.TASBIH)
        setContent {
            TasbihTheme {
                TasbihApp(app, startTab)
            }
        }
    }

    companion object {
        const val EXTRA_TAB = "tab"
    }
}

@Composable
private fun TasbihApp(app: TasbihApplication, startTab: Tab) {
    var tab by rememberSaveable { mutableStateOf(startTab) }
    val store = app.prayerStore
    val location = rememberLocationUpdater(store)
    val config by store.config.collectAsStateWithLifecycle()
    val prayed by store.prayed.collectAsStateWithLifecycle()
    val values by app.settings.values.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current

    LaunchedEffect(Unit) {
        location.refresh()
        RingService.start(context)
        AppUpdater.checkIfDue(context)
    }
    LaunchedEffect(config, prayed) {
        PrayerScheduler.schedule(context)
        PrayerCountdown.update(context)
    }
    DisposableEffect(values.keepScreenOn) {
        view.keepScreenOn = values.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    Column(Modifier.fillMaxSize().background(colors.paper)) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                Tab.TASBIH -> TasbihScreen(app.repository, app.settings, store)
                Tab.PRAYER -> PrayerScreen(store, location)
                Tab.SETTINGS -> SettingsScreen(app.settings, store, location)
            }
        }
        BottomBar(tab, onSelect = { tab = it })
    }
}

@Composable
private fun BottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    Column(Modifier.navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
        Row(Modifier.fillMaxWidth()) {
            Tab.entries.forEach { tab ->
                val tint = if (tab == selected) colors.accent else colors.muted
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(tab) }
                        .padding(vertical = 10.dp),
                ) {
                    Icon(painterResource(tab.iconRes), contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                    Text(
                        stringResource(tab.labelRes),
                        color = tint,
                        fontSize = 13.sp,
                        fontWeight = if (tab == selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
