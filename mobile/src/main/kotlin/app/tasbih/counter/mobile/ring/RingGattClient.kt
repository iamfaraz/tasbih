package app.tasbih.counter.mobile.ring

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.UUID

/**
 * Talks to the ring over BLE: subscribe to D002 for replies, write commands to D004.
 * The ring has no pairing or encryption, so a plain connection is enough. Callbacks
 * arrive on the main thread.
 */
@SuppressLint("MissingPermission")
class RingGattClient(private val context: Context, private val listener: Listener) : BluetoothGattCallback() {
    interface Listener {
        fun onConnecting()
        fun onReady()
        fun onDisconnected()
        fun onMessage(text: String)
    }

    private val handler = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<Pair<ByteArray, Long>>()
    private var device: BluetoothDevice? = null
    private var gatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var draining = false
    private var wanted = false
    private var attempts = 0
    private var refreshedCache = false

    fun connect(target: BluetoothDevice) {
        device = target
        wanted = true
        attempts = 0
        open()
    }

    fun disconnect() {
        wanted = false
        handler.removeCallbacksAndMessages(null)
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        writeCharacteristic = null
        queue.clear()
        draining = false
    }

    fun send(command: String, gapMs: Long = COMMAND_GAP_MS) = sendBytes(command.toByteArray(Charsets.UTF_8), gapMs)

    fun sendBytes(bytes: ByteArray, gapMs: Long = COMMAND_GAP_MS) {
        queue.addLast(bytes to gapMs)
        if (!draining) drain()
    }

    /** After a few quick tries, leave it to the system to reconnect when the ring is back in range. */
    private fun open() {
        val target = device ?: return
        listener.onConnecting()
        gatt = target.connectGatt(context, attempts >= QUICK_ATTEMPTS, this, BluetoothDevice.TRANSPORT_LE)
    }

    private fun drain() {
        val next = queue.removeFirstOrNull()
        val characteristic = writeCharacteristic
        val connection = gatt
        if (next == null || characteristic == null || connection == null) {
            draining = false
            return
        }
        val (bytes, gapMs) = next
        draining = true
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        @Suppress("DEPRECATION")
        characteristic.value = bytes
        @Suppress("DEPRECATION")
        connection.writeCharacteristic(characteristic)
        handler.postDelayed(::drain, gapMs)
    }

    override fun onConnectionStateChange(connection: BluetoothGatt, status: Int, newState: Int) {
        handler.post {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connection.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                onLinkLost(connection)
            }
        }
    }

    private fun onLinkLost(connection: BluetoothGatt) {
        writeCharacteristic = null
        draining = false
        queue.clear()
        refreshCache(connection)
        connection.close()
        if (gatt === connection) gatt = null
        listener.onDisconnected()
        if (wanted) {
            attempts++
            handler.postDelayed(::open, RETRY_DELAY_MS)
        }
    }

    override fun onServicesDiscovered(connection: BluetoothGatt, status: Int) {
        handler.post { bind(connection, status) }
    }

    private fun bind(connection: BluetoothGatt, status: Int) {
        if (status != BluetoothGatt.GATT_SUCCESS) {
            connection.disconnect()
            return
        }
        val characteristics = connection.services.flatMap { it.characteristics }
        val notify = characteristics.firstOrNull { it.uuid == NOTIFY }
        val write = characteristics.firstOrNull { it.uuid == WRITE }
        if (notify == null || write == null) {
            // Android can cache an old service list for a device, so retry once with a fresh one.
            if (!refreshedCache) {
                refreshedCache = true
                refreshCache(connection)
                handler.postDelayed({ connection.discoverServices() }, CACHE_REFRESH_DELAY_MS)
            } else {
                connection.disconnect()
            }
            return
        }
        writeCharacteristic = write
        connection.requestMtu(MTU)
        connection.setCharacteristicNotification(notify, true)
        val descriptor = notify.getDescriptor(CCCD)
        if (descriptor == null) {
            onSubscribed()
            return
        }
        @Suppress("DEPRECATION")
        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        @Suppress("DEPRECATION")
        connection.writeDescriptor(descriptor)
    }

    override fun onDescriptorWrite(connection: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
        handler.post(::onSubscribed)
    }

    private fun onSubscribed() {
        attempts = 0
        listener.onReady()
    }

    @Deprecated("Deprecated in Java")
    override fun onCharacteristicChanged(connection: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        @Suppress("DEPRECATION")
        characteristic.value?.let { deliver(characteristic, it) }
    }

    override fun onCharacteristicChanged(
        connection: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
    ) = deliver(characteristic, value)

    private fun deliver(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        if (characteristic.uuid != NOTIFY) return
        val text = String(value, Charsets.UTF_8)
        handler.post { listener.onMessage(text) }
    }

    /** A hidden API, but the ring's own app calls it too, and stale caches otherwise stop the ring binding. */
    private fun refreshCache(connection: BluetoothGatt) {
        try {
            connection.javaClass.getMethod("refresh").invoke(connection)
        } catch (e: ReflectiveOperationException) {
            // Only an optimisation, so carry on without it.
        }
    }

    companion object {
        const val SERVICE_PREFIX = "0000d0ff"
        private val NOTIFY = UUID.fromString("0000d002-0000-1000-8000-00805f9b34fb")
        private val WRITE = UUID.fromString("0000d004-0000-1000-8000-00805f9b34fb")
        private val CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val COMMAND_GAP_MS = 800L
        private const val RETRY_DELAY_MS = 3_000L
        private const val CACHE_REFRESH_DELAY_MS = 1_500L
        private const val QUICK_ATTEMPTS = 3
        private const val MTU = 247
    }
}
