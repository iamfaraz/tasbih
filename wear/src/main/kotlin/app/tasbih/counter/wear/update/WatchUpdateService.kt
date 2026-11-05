package app.tasbih.counter.wear.update

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import app.tasbih.counter.shared.R
import app.tasbih.counter.shared.update.ApkInstaller
import app.tasbih.counter.shared.update.MAX_APK_BYTES
import app.tasbih.counter.shared.update.UPDATE_CHANNEL_PATH
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import java.io.File

/** Receives an update from the phone, then asks the person whether to install it. */
class WatchUpdateService : WearableListenerService() {
    override fun onChannelOpened(channel: ChannelClient.Channel) {
        if (channel.path != UPDATE_CHANNEL_PATH) return
        val apk = updateFile(this)
        apk.delete()
        apk.createNewFile()
        Wearable.getChannelClient(this).receiveFile(channel, Uri.fromFile(apk), false)
    }

    /** Called once the whole file has arrived, or the transfer has failed. */
    override fun onInputClosed(channel: ChannelClient.Channel, closeReason: Int, errorCode: Int) {
        if (channel.path != UPDATE_CHANNEL_PATH) return
        val apk = updateFile(this)
        val complete = closeReason == ChannelClient.ChannelCallback.CLOSE_REASON_NORMAL
        val genuine = complete && apk.length() in 1..MAX_APK_BYTES && ApkInstaller.isGenuine(this, apk)
        Log.i(TAG, "Update finished: reason=$closeReason bytes=${apk.length()} genuine=$genuine")
        if (genuine) showNotification(this) else apk.delete()
        // Closing here is what tells the phone everything arrived.
        Wearable.getChannelClient(this).close(channel)
    }

    private fun showNotification(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.update_channel), NotificationManager.IMPORTANCE_HIGH)
        )
        val open = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, WatchInstallActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager.notify(
            NOTIFICATION_ID,
            Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(context.getString(R.string.update_ready))
                .setContentText(context.getString(R.string.update_tap_install))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }

    companion object {
        private const val TAG = "TasbihUpdate"
        private const val CHANNEL = "watch_updates"
        private const val NOTIFICATION_ID = 410

        fun updateFile(context: Context) = File(File(context.cacheDir, "update").apply { mkdirs() }, "tasbih-update.apk")
    }
}
