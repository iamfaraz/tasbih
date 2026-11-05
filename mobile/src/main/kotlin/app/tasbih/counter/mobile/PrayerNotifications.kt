package app.tasbih.counter.mobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.text.format.DateFormat
import app.tasbih.counter.shared.Prayer
import app.tasbih.counter.shared.PrayerEvent
import app.tasbih.counter.shared.R as SharedR

/** Posts prayer alerts. The system mirrors them to the watch, buttons included. */
object PrayerNotifications {
    private const val CHANNEL_PREFIX = "prayer_time_"
    private const val REMINDER_CHANNEL = "prayer_reminder"
    private const val REMINDER_ID_OFFSET = 100
    private const val GROUP_CHANNEL = "tasbih_group"
    private const val GROUP_SUMMARY_ID = 300

    /** Prayer alerts and reminders share this, so they take one status bar icon between them. */
    const val GROUP = "app.tasbih.counter.NOTIFICATIONS"

    /** Call before posting into the group. The summary is what collapses the icons. */
    fun ensureGroup(context: Context) {
        val manager = manager(context)
        manager.createNotificationChannel(
            NotificationChannel(
                GROUP_CHANNEL,
                context.getString(SharedR.string.channel_group),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
        val summary = Notification.Builder(context, GROUP_CHANNEL)
            .setSmallIcon(SharedR.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(SharedR.string.app_name))
            .setGroup(GROUP)
            .setGroupSummary(true)
            .setGroupAlertBehavior(Notification.GROUP_ALERT_CHILDREN)
            .setLocalOnly(true)
            .build()
        manager.notify(GROUP_SUMMARY_ID, summary)
    }

    /** Removes the group's summary once no alert or reminder is left under it. */
    fun tidyGroup(context: Context) {
        val manager = manager(context)
        val grouped = manager.activeNotifications.any {
            it.id != GROUP_SUMMARY_ID && it.id != PrayerCountdown.NOTIFICATION_ID
        }
        if (!grouped) manager.cancel(GROUP_SUMMARY_ID)
    }

    /** Clears a prayer's alert and reminder once it's been marked as prayed. */
    fun clearFor(context: Context, prayer: Prayer) {
        cancel(context, prayer.ordinal)
        cancel(context, prayer.ordinal + REMINDER_ID_OFFSET)
    }

    fun defaultSound(context: Context): Uri = Uri.parse(
        "${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/${R.raw.prayer_chime}"
    )

    /** @param watchHasOwnAlert stops this one being forwarded too, which would double up. */
    fun show(context: Context, event: PrayerEvent, soundUri: String?, watchHasOwnAlert: Boolean) {
        if (event.isReminder) {
            showReminder(context, event.prayer, event.endsAt.time, event.day.toString())
        } else {
            showPrayerTime(context, event.prayer, soundUri, watchHasOwnAlert)
        }
    }

    fun showPrayerTime(context: Context, prayer: Prayer, soundUri: String?, localOnly: Boolean = false) {
        val name = context.getString(prayer.nameRes)
        val notification = Notification.Builder(context, prayerTimeChannel(context, soundUri))
            .setSmallIcon(SharedR.drawable.ic_launcher_foreground)
            .setContentTitle(name)
            .setContentText(context.getString(SharedR.string.prayer_time_now, name))
            .setContentIntent(openPrayerTimes(context))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setGroup(GROUP)
            .setLocalOnly(localOnly)
            .setAutoCancel(true)
            .build()
        ensureGroup(context)
        manager(context).notify(prayer.ordinal, notification)
    }

    /** @param day the prayer's day to mark as prayed, or null for a test that records nothing. */
    fun showReminder(context: Context, prayer: Prayer, endsAt: Long, day: String?) {
        val name = context.getString(prayer.nameRes)
        val notificationId = prayer.ordinal + REMINDER_ID_OFFSET
        val time = DateFormat.getTimeFormat(context).format(endsAt)
        val notification = Notification.Builder(context, reminderChannel(context))
            .setSmallIcon(SharedR.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(SharedR.string.have_you_prayed, name))
            .setContentText(context.getString(SharedR.string.prayer_ends_at, name, time))
            .setContentIntent(openPrayerTimes(context))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setGroup(GROUP)
            .setAutoCancel(true)
            .addAction(answer(context, SharedR.string.yes_prayed, PrayerAlarmReceiver.ACTION_PRAYED, prayer, day, notificationId))
            .addAction(answer(context, SharedR.string.not_yet, PrayerAlarmReceiver.ACTION_NOT_YET, prayer, day, notificationId))
            .build()
        ensureGroup(context)
        manager(context).notify(notificationId, notification)
    }

    fun cancel(context: Context, id: Int) = manager(context).cancel(id)

    private fun answer(
        context: Context,
        labelRes: Int,
        action: String,
        prayer: Prayer,
        day: String?,
        notificationId: Int,
    ): Notification.Action {
        val intent = Intent(context, PrayerAlarmReceiver::class.java)
            .setAction(action)
            .putExtra(PrayerAlarmReceiver.EXTRA_PRAYER, prayer.name)
            .putExtra(PrayerAlarmReceiver.EXTRA_DAY, day)
            .putExtra(PrayerAlarmReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        val pending = PendingIntent.getBroadcast(
            context,
            action.hashCode() + notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Action.Builder(null, context.getString(labelRes), pending).build()
    }

    fun openPrayerTimes(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, Tab.PRAYER.name),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** A channel's sound is fixed once created, so each chosen sound gets its own channel. */
    private fun prayerTimeChannel(context: Context, soundUri: String?): String {
        val manager = manager(context)
        val id = CHANNEL_PREFIX + (soundUri?.hashCode() ?: 0)
        manager.notificationChannels
            .filter { it.id.startsWith(CHANNEL_PREFIX) && it.id != id }
            .forEach { manager.deleteNotificationChannel(it.id) }
        val channel = NotificationChannel(
            id,
            context.getString(SharedR.string.channel_prayer_time),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            setSound(
                soundUri?.let(Uri::parse) ?: defaultSound(context),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
        manager.createNotificationChannel(channel)
        return id
    }

    private fun reminderChannel(context: Context): String {
        manager(context).createNotificationChannel(
            NotificationChannel(
                REMINDER_CHANNEL,
                context.getString(SharedR.string.channel_reminder),
                NotificationManager.IMPORTANCE_HIGH,
            )
        )
        return REMINDER_CHANNEL
    }

    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)
}
