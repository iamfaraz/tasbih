package app.tasbih.counter.mobile.update

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import app.tasbih.counter.mobile.MainActivity
import app.tasbih.counter.mobile.Tab
import app.tasbih.counter.shared.R
import app.tasbih.counter.shared.update.ApkInstaller
import app.tasbih.counter.shared.update.MAX_APK_BYTES
import app.tasbih.counter.shared.update.UPDATE_CHANNEL_PATH
import app.tasbih.counter.shared.update.isNewerVersion
import app.tasbih.counter.shared.update.isTrustedDownload
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

data class AppRelease(val version: String, val phoneUrl: String?, val watchUrl: String?)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: AppRelease) : UpdateState
    data class Working(val messageRes: Int, val percent: Int?) : UpdateState
    data class Finished(val messageRes: Int) : UpdateState
    data class Failed(val messageRes: Int) : UpdateState
}

/** Checks this project's GitHub releases. Nothing installs without the person confirming. */
object AppUpdater {
    private const val LATEST_URL = "https://api.github.com/repos/iamfaraz/tasbih/releases/latest"
    private const val MAX_RESPONSE_BYTES = 1024 * 1024
    private const val TIMEOUT_MS = 20_000
    private const val DAY_MS = 24 * 60 * 60 * 1000L
    private const val PREFS = "updates"
    private const val KEY_AUTO = "auto_check"
    private const val KEY_LAST_CHECK = "last_check"
    private const val CHANNEL = "app_updates"
    private const val NOTIFICATION_ID = 600
    private const val TAG = "TasbihUpdate"
    private const val WATCH_TRANSFER_TIMEOUT_MS = 20 * 60_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun autoCheckEnabled(context: Context) = prefs(context).getBoolean(KEY_AUTO, true)

    fun setAutoCheck(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO, enabled).apply()
    }

    fun currentVersion(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()

    /** At most once a day, and only if the person hasn't turned it off. */
    fun checkIfDue(context: Context) {
        val last = prefs(context).getLong(KEY_LAST_CHECK, 0)
        if (autoCheckEnabled(context) && System.currentTimeMillis() - last >= DAY_MS) check(context, notify = true)
    }

    fun check(context: Context, notify: Boolean = false) {
        val app = context.applicationContext
        scope.launch {
            _state.value = UpdateState.Checking
            try {
                val release = fetchLatest()
                prefs(app).edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
                if (isNewerVersion(release.version, currentVersion(app))) {
                    _state.value = UpdateState.Available(release)
                    if (notify) showNotification(app, release)
                } else {
                    _state.value = UpdateState.UpToDate
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = UpdateState.Failed(R.string.update_check_failed)
            }
        }
    }

    fun updatePhone(context: Context, release: AppRelease) {
        val app = context.applicationContext
        val url = release.phoneUrl ?: return fail(R.string.update_no_apk)
        if (!ApkInstaller.canInstall(app)) {
            ApkInstaller.requestPermission(app)
            return
        }
        scope.launch {
            val apk = downloadVerified(app, url, "tasbih-phone.apk") ?: return@launch
            _state.value = UpdateState.Working(R.string.update_installing, null)
            try {
                ApkInstaller.install(app, apk)
                _state.value = UpdateState.Available(release)
            } catch (e: IOException) {
                fail(R.string.update_install_failed)
            }
        }
    }

    fun updateWatch(context: Context, release: AppRelease) {
        val app = context.applicationContext
        val url = release.watchUrl ?: return fail(R.string.update_no_apk)
        scope.launch {
            val apk = downloadVerified(app, url, "tasbih-watch.apk") ?: return@launch
            sendToWatch(app, apk)
        }
    }

    /**
     * Sends an APK over the watch connection. The watch checks it again and asks to install.
     * The watch closes the channel when it has everything, because closing it from this
     * side loses whatever is still queued.
     */
    suspend fun sendToWatch(context: Context, apk: File) {
        try {
            _state.value = UpdateState.Working(R.string.update_sending, null)
            val node = Wearable.getNodeClient(context).connectedNodes.await().firstOrNull()
                ?: return fail(R.string.update_no_watch)
            val client = Wearable.getChannelClient(context)
            val channel = client.openChannel(node.id, UPDATE_CHANNEL_PATH).await()
            val finished = CompletableDeferred<Boolean>()
            val callback = object : ChannelClient.ChannelCallback() {
                override fun onChannelClosed(channel: ChannelClient.Channel, closeReason: Int, errorCode: Int) {
                    finished.complete(closeReason == CLOSE_REASON_REMOTE_CLOSE)
                }
            }
            client.registerChannelCallback(channel, callback).await()
            try {
                client.sendFile(channel, Uri.fromFile(apk)).await()
                val delivered = withTimeoutOrNull(WATCH_TRANSFER_TIMEOUT_MS) { finished.await() } == true
                if (delivered) {
                    _state.value = UpdateState.Finished(R.string.update_sent)
                } else {
                    fail(R.string.update_send_failed)
                }
            } finally {
                client.unregisterChannelCallback(channel, callback)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't send the update to the watch", e)
            fail(R.string.update_send_failed)
        }
    }

    private fun fail(messageRes: Int) {
        _state.value = UpdateState.Failed(messageRes)
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Tasbih")
        }

    private fun fetchLatest(): AppRelease {
        val connection = open(LATEST_URL)
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("GitHub said ${connection.responseCode}")
            val body = connection.inputStream.use(::readLimited).toString(Charsets.UTF_8)
            val json = JSONObject(body)
            val assets = json.getJSONArray("assets")
            val urls = (0 until assets.length()).map { assets.getJSONObject(it) }
                .associate { it.getString("name") to it.getString("browser_download_url") }
            fun find(prefix: String) = urls.entries.firstOrNull { (name, url) ->
                name.startsWith(prefix) && name.endsWith(".apk") && isTrustedDownload(url)
            }?.value
            return AppRelease(json.getString("tag_name"), find("tasbih-phone-"), find("tasbih-watch-"))
        } finally {
            connection.disconnect()
        }
    }

    private fun readLimited(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return output.toByteArray()
            if (output.size() + read > MAX_RESPONSE_BYTES) throw IOException("Response too large")
            output.write(buffer, 0, read)
        }
    }

    /** Returns the file only if it's a genuine Tasbih build. Reports its own failures. */
    private fun downloadVerified(context: Context, url: String, name: String): File? {
        if (!isTrustedDownload(url)) {
            fail(R.string.update_not_genuine)
            return null
        }
        val folder = File(context.cacheDir, "update").apply { mkdirs() }
        val apk = File(folder, name)
        val connection = open(url)
        try {
            _state.value = UpdateState.Working(R.string.update_downloading, 0)
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("Download failed")
            if (connection.url.protocol != "https") throw IOException("Redirected away from HTTPS")
            val size = connection.contentLengthLong
            if (size > MAX_APK_BYTES) throw IOException("Too large")
            connection.inputStream.use { input ->
                apk.outputStream().use { output ->
                    copy(input, output, size) { percent ->
                        _state.value = UpdateState.Working(R.string.update_downloading, percent)
                    }
                }
            }
        } catch (e: IOException) {
            apk.delete()
            fail(R.string.update_download_failed)
            return null
        } finally {
            connection.disconnect()
        }
        // A phone and watch APK share a signature, so this is what proves the download is ours.
        if (!ApkInstaller.isGenuine(context, apk)) {
            apk.delete()
            fail(R.string.update_not_genuine)
            return null
        }
        return apk
    }

    private fun copy(input: InputStream, output: OutputStream, size: Long, onPercent: (Int) -> Unit) {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        var lastPercent = -1
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_APK_BYTES) throw IOException("Too large")
            output.write(buffer, 0, read)
            val percent = if (size > 0) (total * 100 / size).toInt() else 0
            if (percent != lastPercent) {
                lastPercent = percent
                onPercent(percent)
            }
        }
    }

    private fun showNotification(context: Context, release: AppRelease) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.update_channel), NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, Tab.SETTINGS.name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager.notify(
            NOTIFICATION_ID,
            Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(context.getString(R.string.update_available, release.version))
                .setContentText(context.getString(R.string.update_tap))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }
}
