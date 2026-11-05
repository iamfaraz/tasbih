package app.tasbih.counter.wear

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.tasbih.counter.shared.Prayer
import app.tasbih.counter.shared.REMINDER_OFF
import app.tasbih.counter.shared.TasbihApplication
import app.tasbih.counter.shared.isWorn
import app.tasbih.counter.shared.nextPrayerEvent
import app.tasbih.counter.shared.toEnumOr
import app.tasbih.counter.shared.vibrateMilestone
import java.time.LocalDate
import java.util.Date
import kotlin.concurrent.thread
import app.tasbih.counter.shared.R as SharedR

/**
 * Alerts at prayer times, worked out on the watch from what the phone sent, so they
 * still arrive with the phone out of range. Only the next one is ever scheduled.
 */
object WatchPrayerAlerts {
    private const val CHANNEL = "watch_prayer_time"
    private const val NOTIFICATION_ID = 400
    const val ACTION_ALERT = "app.tasbih.counter.action.WATCH_PRAYER_ALERT"
    const val EXTRA_PRAYER = "prayer"

    fun schedule(context: Context) {
        val config = (context.applicationContext as TasbihApplication).prayerStore.config.value
        val alarms = context.getSystemService(AlarmManager::class.java)
        val startsOnly = config.copy(alerts = config.alerts.mapValues { it.value.copy(reminderMinutes = REMINDER_OFF) })
        val event = if (config.watchAlerts) {
            nextPrayerEvent(startsOnly, Date(), LocalDate.now()) { _, _ -> false }
        } else {
            null
        }
        val pending = alarmIntent(context, event?.prayer)
        when {
            event == null -> alarms.cancel(pending)
            alarms.canScheduleExactAlarms() ->
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, event.time.time, pending)
            else -> alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, event.time.time, pending)
        }
    }

    /** Only alerts on the wrist, so a watch on charge stays quiet. Blocks briefly, so stay off the main thread. */
    fun show(context: Context, prayer: Prayer) {
        val manager = context.getSystemService(NotificationManager::class.java)
        // Without notification permission the alert can't appear, so a buzz would be unexplained.
        if (!manager.areNotificationsEnabled() || !isWorn(context)) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                context.getString(SharedR.string.channel_prayer_time),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                enableVibration(false)
                setSound(null, null)
            }
        )
        val name = context.getString(prayer.nameRes)
        manager.notify(
            NOTIFICATION_ID,
            Notification.Builder(context, CHANNEL)
                .setSmallIcon(SharedR.drawable.ic_launcher_foreground)
                .setContentTitle(name)
                .setContentText(context.getString(SharedR.string.prayer_time_now, name))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .build(),
        )
        context.vibrateMilestone()
    }

    private fun alarmIntent(context: Context, prayer: Prayer?): PendingIntent {
        val intent = Intent(context, WatchPrayerReceiver::class.java).setAction(ACTION_ALERT)
        prayer?.let { intent.putExtra(EXTRA_PRAYER, it.name) }
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

/** Fires alerts, and reschedules after new settings, reboots and clock changes. */
class WatchPrayerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        thread {
            try {
                if (intent.action == WatchPrayerAlerts.ACTION_ALERT) {
                    val prayer = intent.getStringExtra(WatchPrayerAlerts.EXTRA_PRAYER).toEnumOr(Prayer.FAJR)
                    WatchPrayerAlerts.show(context, prayer)
                }
                WatchPrayerAlerts.schedule(context)
            } finally {
                result.finish()
            }
        }
    }
}
