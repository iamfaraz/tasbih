package app.tasbih.counter.mobile

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import androidx.annotation.RequiresApi
import app.tasbih.counter.mobile.ring.RingConnection
import app.tasbih.counter.mobile.ring.RingService
import app.tasbih.counter.mobile.ring.RingStore
import app.tasbih.counter.shared.PrayerWindow
import app.tasbih.counter.shared.TasbihApplication
import app.tasbih.counter.shared.currentPrayerWindow
import app.tasbih.counter.shared.formatTimeLeft
import java.time.LocalDate
import java.util.Date
import app.tasbih.counter.shared.R as SharedR

/** The optional ongoing notification showing how long the current prayer's time has left. */
object PrayerCountdown {
    private const val CHANNEL = "prayer_countdown"
    private const val TICK_REQUEST = 1
    private const val BOUNDARY_REQUEST = 2
    private const val MARK_PRAYED_REQUEST = 3
    private const val PROGRESS_MAX = 1000
    private const val MINUTE_MS = 60_000L
    private const val EXTRA_REQUEST_PROMOTED = "android.requestPromotedOngoing"
    private const val ROUTE_COLOR = 0xFF2E8F88.toInt()

    /**
     * The one ongoing notification Tasbih keeps. Android keeps a Live Update apart from
     * other notifications, so instead of grouping, the ring service shares this one.
     */
    const val NOTIFICATION_ID = 200

    /** Refreshes the notification and sets up the next refresh, or clears it all if switched off. */
    fun update(context: Context) {
        val store = (context.applicationContext as TasbihApplication).prayerStore
        val config = store.config.value
        val window = if (config.countdownEnabled) currentPrayerWindow(config, Date(), LocalDate.now()) else null
        val manager = context.getSystemService(NotificationManager::class.java)
        val alarms = context.getSystemService(AlarmManager::class.java)
        val countdown = currentNotification(context)
        when {
            countdown != null -> manager.notify(NOTIFICATION_ID, countdown)
            RingService.running -> manager.notify(NOTIFICATION_ID, RingService.notification(context))
            else -> manager.cancel(NOTIFICATION_ID)
        }
        PrayerNotifications.tidyGroup(context)

        if (countdown != null) {
            // Not a wake-up alarm, so a sleeping phone isn't woken just to redraw the minutes.
            val nextMinute = (System.currentTimeMillis() / MINUTE_MS + 1) * MINUTE_MS
            alarms.set(AlarmManager.RTC, nextMinute, refreshIntent(context, TICK_REQUEST))
        } else {
            alarms.cancel(refreshIntent(context, TICK_REQUEST))
        }
        // A prayed window still needs this, as the next prayer brings the notification back.
        if (window != null) scheduleBoundary(context, alarms, window) else alarms.cancel(refreshIntent(context, BOUNDARY_REQUEST))
    }

    /** The countdown as it should look now, or null if nothing should be showing. */
    fun currentNotification(context: Context): Notification? {
        val store = (context.applicationContext as TasbihApplication).prayerStore
        val config = store.config.value
        val now = Date()
        val window = (if (config.countdownEnabled) currentPrayerWindow(config, now, LocalDate.now()) else null)
            ?.takeUnless { it.isPrayed(store::hasPrayed) }
            ?: return null
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                context.getString(SharedR.string.channel_countdown),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
        return build(context, window, now)
    }

    /** Sits in the notification's header, so the ring needs no notification of its own. */
    private fun ringLine(context: Context): String? {
        val status = RingStore.get(context).status.value
        return when {
            !status.paired -> null
            status.connection == RingConnection.CONNECTED -> context.getString(SharedR.string.ring_line_connected)
            else -> context.getString(SharedR.string.ring_line_searching)
        }
    }

    /** The move to the next prayer should be on time, even while the phone sleeps. */
    private fun scheduleBoundary(context: Context, alarms: AlarmManager, window: PrayerWindow) {
        val pending = refreshIntent(context, BOUNDARY_REQUEST)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, window.end.time, pending)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, window.end.time, pending)
        }
    }

    /** Only a prayer whose time has started can be marked, so the gap after sunrise has no button. */
    private fun Notification.Builder.addMarkPrayed(context: Context, window: PrayerWindow): Notification.Builder {
        if (!window.active) return this
        val intent = Intent(context, PrayerAlarmReceiver::class.java)
            .setAction(PrayerAlarmReceiver.ACTION_PRAYED)
            .putExtra(PrayerAlarmReceiver.EXTRA_PRAYER, window.prayer.name)
            .putExtra(PrayerAlarmReceiver.EXTRA_DAY, window.day.toString())
            .putExtra(PrayerAlarmReceiver.EXTRA_NOTIFICATION_ID, NOTIFICATION_ID)
        val pending = PendingIntent.getBroadcast(
            context,
            MARK_PRAYED_REQUEST,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return addAction(
            Notification.Action.Builder(null, context.getString(SharedR.string.mark_prayed), pending).build()
        )
    }

    private fun build(context: Context, window: PrayerWindow, now: Date): Notification =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            buildLiveUpdate(context, window, now)
        } else {
            buildClassic(context, window, now)
        }

    /** A Live Update, which draws the prayer's time as a route with a marker for now. */
    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun buildLiveUpdate(context: Context, window: PrayerWindow, now: Date): Notification {
        val time = DateFormat.getTimeFormat(context)
        val name = context.getString(window.prayer.nameRes)
        val start = time.format(window.start)
        val end = time.format(window.end)
        val left = formatTimeLeft(window.end.time - now.time)
        val length = ((window.end.time - window.start.time) / MINUTE_MS).toInt().coerceAtLeast(1)
        val elapsed = ((now.time - window.start.time) / MINUTE_MS).toInt().coerceIn(0, length)
        val route = Notification.ProgressStyle()
            .setStyledByProgress(true)
            .addProgressSegment(Notification.ProgressStyle.Segment(length).setColor(ROUTE_COLOR))
            .setProgress(elapsed)
            .setProgressStartIcon(Icon.createWithResource(context, SharedR.drawable.ic_live_start))
            .setProgressTrackerIcon(Icon.createWithResource(context, SharedR.drawable.ic_live_bead))
            .setProgressEndIcon(Icon.createWithResource(context, SharedR.drawable.ic_live_end))
        return Notification.Builder(context, CHANNEL)
            .setSmallIcon(SharedR.drawable.ic_launcher_foreground)
            .setContentTitle(
                if (window.active) {
                    context.getString(SharedR.string.live_left, left)
                } else {
                    context.getString(SharedR.string.live_next, name, left)
                }
            )
            .setContentText(
                if (window.active) {
                    context.getString(SharedR.string.live_route, name, start, end)
                } else {
                    context.getString(SharedR.string.live_route_next, start, name, end)
                }
            )
            .setStyle(route)
            .setShortCriticalText(left)
            .setFlag(Notification.FLAG_PROMOTED_ONGOING, true)
            // Android 16.1 and later read this request instead of the flag.
            .addExtras(Bundle().apply { putBoolean(EXTRA_REQUEST_PROMOTED, true) })
            .setColor(ROUTE_COLOR)
            .setShowWhen(false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .setSubText(ringLine(context))
            .setContentIntent(PrayerNotifications.openPrayerTimes(context))
            .addMarkPrayed(context, window)
            .build()
    }

    private fun buildClassic(context: Context, window: PrayerWindow, now: Date): Notification {
        val time = DateFormat.getTimeFormat(context)
        val name = context.getString(window.prayer.nameRes)
        val start = time.format(window.start)
        val end = time.format(window.end)
        val left = formatTimeLeft(window.end.time - now.time)
        val title = context.getString(
            if (window.active) SharedR.string.countdown_title else SharedR.string.countdown_title_next,
            name,
        )
        val details = context.getString(
            if (window.active) SharedR.string.countdown_details else SharedR.string.countdown_details_next,
            start,
            left,
            end,
        )
        return Notification.Builder(context, CHANNEL)
            .setSmallIcon(SharedR.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(context.getString(SharedR.string.countdown_summary, left, end))
            .setStyle(Notification.BigTextStyle().bigText(details))
            .setProgress(PROGRESS_MAX, (window.progress(now) * PROGRESS_MAX).toInt(), false)
            .setWhen(window.end.time)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .setSubText(ringLine(context))
            .setContentIntent(PrayerNotifications.openPrayerTimes(context))
            .addMarkPrayed(context, window)
            .build()
    }

    private fun refreshIntent(context: Context, request: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        request,
        Intent(context, PrayerAlarmReceiver::class.java).setAction(PrayerAlarmReceiver.ACTION_COUNTDOWN),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
