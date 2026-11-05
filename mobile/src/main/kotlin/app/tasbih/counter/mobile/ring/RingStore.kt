package app.tasbih.counter.mobile.ring

import android.content.Context
import app.tasbih.counter.shared.RingCharge
import app.tasbih.counter.shared.RingSummary
import app.tasbih.counter.shared.TasbihApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RingConnection { DISCONNECTED, CONNECTING, CONNECTED }

data class RingStatus(
    val address: String? = null,
    val name: String? = null,
    val connection: RingConnection = RingConnection.DISCONNECTED,
    val batteryLevel: Int? = null,
    val charge: RingCharge = RingCharge.NONE,
) {
    val paired: Boolean
        get() = address != null

    fun toSummary() = RingSummary(paired, connection == RingConnection.CONNECTED, batteryLevel, charge)
}

/** Which ring is paired and how it's doing. */
class RingStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("ring", Context.MODE_PRIVATE)
    private val summary = (context.applicationContext as TasbihApplication).ringSummary

    private val _status = MutableStateFlow(
        RingStatus(address = prefs.getString(KEY_ADDRESS, null), name = prefs.getString(KEY_NAME, null))
    )
    val status: StateFlow<RingStatus> = _status.asStateFlow()

    init {
        summary.value = _status.value.toSummary()
    }

    private fun publish(status: RingStatus) {
        _status.value = status
        summary.value = status.toSummary() // Reaches the watch through WearSync.
    }

    fun pair(address: String, name: String?) {
        prefs.edit().putString(KEY_ADDRESS, address).putString(KEY_NAME, name).remove(KEY_PRAYER_SENT).apply()
        publish(RingStatus(address, name))
    }

    fun unpair() {
        prefs.edit().clear().apply()
        publish(RingStatus())
    }

    fun update(connection: RingConnection) {
        publish(_status.value.copy(connection = connection))
    }

    fun updateBattery(level: Int, charge: RingCharge) {
        publish(_status.value.copy(batteryLevel = level, charge = charge))
    }

    /** Returns whether [settings] differ from what the ring last received, and remembers them. */
    fun shouldSendPrayerSettings(settings: String): Boolean {
        if (prefs.getString(KEY_PRAYER_SENT, null) == settings) return false
        prefs.edit().putString(KEY_PRAYER_SENT, settings).apply()
        return true
    }

    companion object {
        private const val KEY_ADDRESS = "address"
        private const val KEY_NAME = "name"
        private const val KEY_PRAYER_SENT = "prayer_sent"

        @Volatile
        private var instance: RingStore? = null

        fun get(context: Context): RingStore = instance ?: synchronized(this) {
            instance ?: RingStore(context.applicationContext).also { instance = it }
        }
    }
}
