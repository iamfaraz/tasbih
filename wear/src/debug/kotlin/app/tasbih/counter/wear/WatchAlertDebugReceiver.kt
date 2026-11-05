package app.tasbih.counter.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.tasbih.counter.shared.Prayer
import kotlin.concurrent.thread

/** Shows a sample alert: `adb shell am broadcast -n app.tasbih.counter/.wear.WatchAlertDebugReceiver` */
class WatchAlertDebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        thread {
            WatchPrayerAlerts.show(context, Prayer.ISHA)
            result.finish()
        }
    }
}
