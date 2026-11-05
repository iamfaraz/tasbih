package app.tasbih.counter.mobile.ring

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.text.format.DateFormat
import androidx.core.content.ContextCompat
import app.tasbih.counter.mobile.MainActivity
import app.tasbih.counter.mobile.PrayerCountdown
import app.tasbih.counter.shared.Prayer
import app.tasbih.counter.shared.PrayerConfig
import app.tasbih.counter.shared.TasbihApplication
import app.tasbih.counter.shared.milestoneReached
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.TimeZone
import app.tasbih.counter.shared.R as SharedR

/**
 * Keeps the ring connected while the app is closed, and feeds its presses into the
 * shared counter, which then syncs to the watch over the Data Layer. It only runs
 * while a ring is paired, because the watch sync needs no service of its own.
 */
class RingService : Service(), RingGattClient.Listener {
    private val handler = Handler(Looper.getMainLooper())
    private val store by lazy { RingStore.get(this) }
    private val app by lazy { application as TasbihApplication }
    private var client: RingGattClient? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var ringCount: Long? = null

    /** While testing the protocol, the ring's totals are tracked but never added to a tasbih. */
    private var testMode = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val address = store.status.value.address
        if (address == null || !canConnect(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        running = true
        showNotification()
        if (client == null) {
            val device = getSystemService(BluetoothManager::class.java).adapter.getRemoteDevice(address)
            client = RingGattClient(this, this).also { it.connect(device) }
            buzzAtMilestones()
            poll()
            followDhikrChoice()
            keepPrayerSettingsCurrent()
        }
        if (intent?.hasExtra(EXTRA_TEST_MODE) == true) testMode = intent.getBooleanExtra(EXTRA_TEST_MODE, false)
        val gapMs = intent?.getIntExtra(EXTRA_GAP, 800)?.toLong() ?: 800L
        intent?.getStringExtra(EXTRA_RAW)?.let { raw ->
            Log.i(TAG, "TX $raw")
            client?.send(raw, gapMs)
        }
        intent?.getStringExtra(EXTRA_HEX)?.let { hex ->
            Log.i(TAG, "TX ${hex.length / 2} bytes")
            client?.sendBytes(hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        client?.disconnect()
        client = null
        store.update(RingConnection.DISCONNECTED)
        running = false
        // Leaves the shared notification in place, for the countdown to keep or clear.
        stopForeground(STOP_FOREGROUND_DETACH)
        PrayerCountdown.update(this)
        super.onDestroy()
    }

    override fun onConnecting() {
        store.update(RingConnection.CONNECTING)
        showNotification()
    }

    override fun onDisconnected() {
        store.update(RingConnection.CONNECTING)
        showNotification()
    }

    override fun onReady() {
        store.update(RingConnection.CONNECTED)
        showNotification()
        val config = app.prayerStore.config.value
        client?.run {
            send(
                RingCommands.syncTime(
                    DateFormat.is24HourFormat(this@RingService),
                    timeZoneHours(),
                    config.latitude ?: 0.0,
                    config.longitude ?: 0.0,
                )
            )
            send(RingCommands.battery())
            send(RingCommands.status()) // Matches the counters to the ring straight away.
        }
        sendPrayerSettings(config)
    }

    override fun onMessage(text: String) {
        Log.i(TAG, "RX $text")
        when (val message = parseRingMessage(text)) {
            is RingMessage.Count -> {
                ringCount = message.count.toLong()
                if (!testMode) app.repository.syncToRing(message.count.toLong())
            }
            is RingMessage.Battery -> store.updateBattery(message.level, message.charge)
            RingMessage.Other -> Unit
        }
    }

    /** Whatever is chosen on the phone or watch takes the ring's number straight away. */
    private fun followDhikrChoice() {
        scope.launch {
            app.settings.values
                .distinctUntilChangedBy { it.afterPrayerMode to (it.selected.takeUnless { _ -> it.afterPrayerMode }) }
                .drop(1)
                .collect { ringCount?.let { count -> if (!testMode) app.repository.syncToRing(count) } }
        }
    }

    /** The ring only reports its battery when asked, and this also catches any missed count. */
    private fun poll() {
        scope.launch {
            while (true) {
                delay(POLL_MS)
                if (store.status.value.connection == RingConnection.CONNECTED) {
                    client?.send(RingCommands.battery())
                    client?.send(RingCommands.status())
                }
            }
        }
    }

    /** Watches totals rather than presses, so counts from the phone and watch buzz the ring too. */
    private fun buzzAtMilestones() {
        var previous = app.repository.states.value
        scope.launch {
            app.repository.states.collect { states ->
                val reached = milestoneReached(previous, states, app.settings.values.value)
                previous = states
                if (reached) {
                    client?.send(RingCommands.buzz(true))
                    client?.send(RingCommands.buzz(false))
                }
            }
        }
    }

    private fun keepPrayerSettingsCurrent() {
        scope.launch {
            app.prayerStore.config
                .distinctUntilChangedBy { listOf(it.latitude, it.longitude, it.school, it.alerts) }
                .drop(1) // onReady sends the current settings.
                .collect(::sendPrayerSettings)
        }
    }

    private fun sendPrayerSettings(config: PrayerConfig) {
        val latitude = config.latitude ?: return
        val longitude = config.longitude ?: return
        // Only when something changed, to keep traffic to the ring down.
        val alerts = Prayer.entries.map { config.alerts.getValue(it).notify }
        // Rounded to about 1 km, as each location fix differs slightly and that shouldn't count as a change.
        val place = String.format(Locale.ROOT, "%.2f,%.2f", latitude, longitude)
        if (!store.shouldSendPrayerSettings("$place,${config.school},$alerts")) return
        client?.send(RingCommands.prayerSetup(config.school.doubleShadow, timeZoneHours(), latitude, longitude))
        client?.send(RingCommands.prayerReminders(alerts))
    }

    private fun timeZoneHours() = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 3_600_000.0

    /** Uses the prayer countdown when it's showing, so Tasbih only keeps one notification. */
    private fun showNotification() {
        val notification = PrayerCountdown.currentNotification(this) ?: notification(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                PrayerCountdown.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            startForeground(PrayerCountdown.NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val TAG = "TasbihRing"
        private const val EXTRA_RAW = "raw"
        private const val EXTRA_HEX = "hex"
        private const val EXTRA_GAP = "gap"
        private const val EXTRA_TEST_MODE = "test_mode"
        private const val POLL_MS = 5 * 60_000L
        private const val CHANNEL = "ring_connection"

        @Volatile
        var running = false
            private set

        /** The plain status shown when the countdown isn't there to carry it. */
        fun notification(context: Context): Notification {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(SharedR.string.ring_channel), NotificationManager.IMPORTANCE_LOW)
            )
            val connected = RingStore.get(context).status.value.connection == RingConnection.CONNECTED
            return Notification.Builder(context, CHANNEL)
                .setSmallIcon(SharedR.drawable.ic_launcher_foreground)
                .setContentTitle(context.getString(SharedR.string.ring))
                .setContentText(context.getString(if (connected) SharedR.string.ring_connected else SharedR.string.ring_searching))
                .setOngoing(true)
                .setContentIntent(
                    PendingIntent.getActivity(
                        context,
                        PrayerCountdown.NOTIFICATION_ID,
                        Intent(context, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                )
                .build()
        }

        private fun canConnect(context: Context) =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED

        /** Starts the service if a ring is paired. Safe to call at any time. */
        fun start(context: Context) {
            if (RingStore.get(context).status.value.paired && canConnect(context)) {
                ContextCompat.startForegroundService(context, Intent(context, RingService::class.java))
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RingService::class.java))
        }
    }
}
