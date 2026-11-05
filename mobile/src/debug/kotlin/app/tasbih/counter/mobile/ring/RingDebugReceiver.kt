package app.tasbih.counter.mobile.ring

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.tasbih.counter.mobile.PrayerCountdown
import app.tasbih.counter.shared.TasbihApplication

/**
 * Lets adb talk to the ring while exploring its protocol:
 * `adb shell am broadcast -n app.tasbih.counter/.mobile.ring.RingDebugReceiver --es raw "P07,..."`
 * Add `--ez test_mode true` first so replies aren't counted as presses.
 * Use `--ez stop true` before installing, as an unclean restart seems to shut the ring down.
 */
class RingDebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getBooleanExtra("stop", false)) {
            RingService.stop(context) // Closes the link cleanly before installing a new build.
            return
        }
        if (intent.hasExtra("countdown")) {
            val on = intent.getBooleanExtra("countdown", false)
            (context.applicationContext as TasbihApplication).prayerStore.update { it.copy(countdownEnabled = on) }
            PrayerCountdown.update(context)
            return
        }
        context.startService(Intent(context, RingService::class.java).putExtras(intent))
    }
}
