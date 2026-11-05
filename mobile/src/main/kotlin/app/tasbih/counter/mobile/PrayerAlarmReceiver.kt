package app.tasbih.counter.mobile

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import app.tasbih.counter.mobile.ring.RingService
import app.tasbih.counter.shared.Prayer
import app.tasbih.counter.shared.PrayerEvent
import app.tasbih.counter.shared.TasbihApplication
import app.tasbih.counter.shared.nextPrayerEvent
import app.tasbih.counter.shared.toEnumOr
import java.time.LocalDate
import java.util.Date

/**
 * Only the next alert is ever scheduled. When it fires it schedules the one after,
 * which keeps every change of settings, location or clock down to one code path.
 */
object PrayerScheduler {
    fun schedule(context: Context) {
        val store = (context.applicationContext as TasbihApplication).prayerStore
        val event = nextPrayerEvent(store.config.value, Date(), LocalDate.now(), store::hasPrayed)
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = alarmIntent(context, event)
        when {
            event == null -> alarms.cancel(pending)
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms() ->
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, event.time.time, pending)
            else -> alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, event.time.time, pending)
        }
    }

    private fun alarmIntent(context: Context, event: PrayerEvent?): PendingIntent {
        val intent = Intent(context, PrayerAlarmReceiver::class.java).setAction(PrayerAlarmReceiver.ACTION_ALERT)
        if (event != null) {
            intent.putExtra(PrayerAlarmReceiver.EXTRA_PRAYER, event.prayer.name)
                .putExtra(PrayerAlarmReceiver.EXTRA_DAY, event.day.toString())
                .putExtra(PrayerAlarmReceiver.EXTRA_REMINDER, event.isReminder)
                .putExtra(PrayerAlarmReceiver.EXTRA_TIME, event.time.time)
                .putExtra(PrayerAlarmReceiver.EXTRA_ENDS_AT, event.endsAt.time)
        }
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

/** Fires alerts, handles reminder answers, and reschedules after reboots and clock changes. */
class PrayerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = (context.applicationContext as TasbihApplication).prayerStore
        val prayer = intent.getStringExtra(EXTRA_PRAYER).toEnumOr(Prayer.FAJR)
        val day = intent.getStringExtra(EXTRA_DAY)?.let(LocalDate::parse)
        when (intent.action) {
            ACTION_ALERT -> if (day != null) {
                val event = PrayerEvent(
                    time = Date(intent.getLongExtra(EXTRA_TIME, 0)),
                    prayer = prayer,
                    day = day,
                    isReminder = intent.getBooleanExtra(EXTRA_REMINDER, false),
                    endsAt = Date(intent.getLongExtra(EXTRA_ENDS_AT, 0)),
                )
                val config = store.config.value
                PrayerNotifications.show(context, event, config.soundUri, config.watchAlerts)
            }
            ACTION_PRAYED -> {
                if (day != null) store.setPrayed(day, prayer, true)
                PrayerNotifications.cancel(context, intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0))
                PrayerNotifications.clearFor(context, prayer)
            }
            ACTION_NOT_YET -> PrayerNotifications.cancel(context, intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0))
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> RingService.start(context)
        }
        PrayerScheduler.schedule(context)
        PrayerCountdown.update(context)
    }

    companion object {
        const val ACTION_COUNTDOWN = "app.tasbih.counter.action.COUNTDOWN"
        const val ACTION_ALERT = "app.tasbih.counter.action.PRAYER_ALERT"
        const val ACTION_PRAYED = "app.tasbih.counter.action.PRAYED"
        const val ACTION_NOT_YET = "app.tasbih.counter.action.NOT_YET"
        const val EXTRA_PRAYER = "prayer"
        const val EXTRA_DAY = "day"
        const val EXTRA_REMINDER = "reminder"
        const val EXTRA_TIME = "time"
        const val EXTRA_ENDS_AT = "ends_at"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }
}
