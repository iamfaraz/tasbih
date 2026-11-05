package app.tasbih.counter.mobile

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tasbih.counter.shared.Prayer
import app.tasbih.counter.shared.PrayerDay
import app.tasbih.counter.shared.PrayerPeriod
import app.tasbih.counter.shared.PrayerStore
import app.tasbih.counter.shared.currentPrayerPeriod
import app.tasbih.counter.shared.today
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.util.Date
import java.util.Locale
import app.tasbih.counter.shared.R as SharedR

private const val UPCOMING_ALPHA = 0.45f

@Composable
fun PrayerScreen(store: PrayerStore, location: LocationUpdater) {
    val config by store.config.collectAsStateWithLifecycle()
    val prayed by store.prayed.collectAsStateWithLifecycle()
    val now by produceState(Date()) {
        while (true) {
            delay(1_000)
            value = Date()
        }
    }
    var showingLocation by remember { mutableStateOf(false) }
    val day = config.today()
    val period = currentPrayerPeriod(config, now, LocalDate.now())

    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(SharedR.string.prayer_times)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(PillShape)
                    .background(colors.card)
                    .clickable { showingLocation = true }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Icon(
                    painterResource(SharedR.drawable.ic_pin),
                    contentDescription = stringResource(SharedR.string.location),
                    tint = colors.ink,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text(config.placeName ?: stringResource(SharedR.string.location_current), fontWeight = FontWeight.Medium)
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        ) {
            if (day == null || period == null) {
                SoftCard(Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(
                            if (location.failed) SharedR.string.location_failed else SharedR.string.location_waiting
                        )
                    )
                    Spacer(Modifier.height(12.dp))
                    Pill(stringResource(SharedR.string.location)) { showingLocation = true }
                }
            } else {
                NextPrayerCard(period, now)
                PrayerList(store, prayed, day, period, now)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    SmallTimeCard(SharedR.drawable.ic_sun, SharedR.string.prayer_sunrise, day.sunrise, Modifier.weight(1f))
                    SmallTimeCard(SharedR.drawable.ic_crescent, SharedR.string.prayer_tahajjud, day.tahajjud, Modifier.weight(1f))
                }
                if (location.failed) Text(stringResource(SharedR.string.location_stale), color = colors.muted)
                Text(stringResource(SharedR.string.prayer_disclaimer), color = colors.muted, fontSize = 13.sp)
            }
        }
    }

    if (showingLocation) LocationDialog(store, location, onDismiss = { showingLocation = false })
}

@Composable
private fun NextPrayerCard(period: PrayerPeriod, now: Date) {
    val text = skyTextColor(period.current)
    val seconds = ((period.nextStart.time - now.time) / 1_000).coerceAtLeast(0)
    val countdown = String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
    val nextName = stringResource(period.next.nameRes)

    Box(Modifier.fillMaxWidth().height(214.dp).clip(CardShape)) {
        SkyScene(period.current, Modifier.fillMaxSize())
        Column(Modifier.padding(20.dp)) {
            Text(stringResource(period.current.nameRes), fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = text)
            Spacer(Modifier.height(18.dp))
            Text(stringResource(SharedR.string.next_prayer_in, nextName), color = text)
            Text(
                countdown,
                fontSize = 38.sp,
                fontWeight = FontWeight.SemiBold,
                color = text,
                style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
            )
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.5f)
                    .height(6.dp)
                    .clip(PillShape)
                    .background(text.copy(alpha = 0.2f)),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(period.progress(now))
                        .clip(PillShape)
                        .background(text),
                )
            }
        }
    }
}

@Composable
private fun PrayerList(store: PrayerStore, prayed: Set<String>, day: PrayerDay, period: PrayerPeriod, now: Date) {
    val context = LocalContext.current
    val timeFormat = remember { DateFormat.getTimeFormat(context) }
    val today = LocalDate.now()

    SoftCard(Modifier.fillMaxWidth()) {
        Prayer.entries.forEach { prayer ->
            val done = prayed.isNotEmpty() && store.hasPrayed(today, prayer)
            val active = prayer == period.current
            // A prayer can't be marked before its time. An early mark can still be taken back.
            val canToggle = done || !prayer.start(day).after(now)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (active) colors.sand.copy(alpha = 0.6f) else colors.card)
                    .clickable(enabled = canToggle) { store.setPrayed(today, prayer, !done) }
                    .alpha(if (canToggle) 1f else UPCOMING_ALPHA)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
                PrayedMark(done)
                Spacer(Modifier.size(14.dp))
                Text(
                    stringResource(prayer.nameRes),
                    fontSize = 18.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                )
                Text(timeFormat.format(prayer.start(day)), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun PrayedMark(done: Boolean) {
    val check = colors.onAccent
    Box(
        Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(if (done) colors.accent else colors.paper)
            .border(1.5.dp, colors.accent, CircleShape),
    ) {
        if (done) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 2.dp.toPx()
                val elbow = Offset(size.width * 0.44f, size.height * 0.68f)
                drawLine(check, Offset(size.width * 0.28f, size.height * 0.52f), elbow, stroke, StrokeCap.Round)
                drawLine(check, elbow, Offset(size.width * 0.74f, size.height * 0.34f), stroke, StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun SmallTimeCard(iconRes: Int, labelRes: Int, time: Date, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val timeFormat = remember { DateFormat.getTimeFormat(context) }
    SoftCard(modifier) {
        Icon(painterResource(iconRes), contentDescription = null, tint = colors.ink, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(12.dp))
        Text(stringResource(labelRes), color = colors.muted)
        Text(timeFormat.format(time), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}
