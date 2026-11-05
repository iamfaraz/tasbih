package app.tasbih.counter.shared

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val TIMEOUT_MS = 800L

/**
 * Whether the watch is on the wrist. A watch on charge never is. Otherwise the sensor
 * reports its state as soon as it's listened to. With no sensor or no answer this says
 * yes, as a stray buzz costs less than a missed one. Blocks briefly, so call it off the
 * main thread.
 */
fun isWorn(context: Context): Boolean {
    if (context.getSystemService(BatteryManager::class.java).isCharging) return false
    val manager = context.getSystemService(SensorManager::class.java)
    val sensor = manager.getDefaultSensor(Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT, true) ?: return true
    val answered = CountDownLatch(1)
    var worn = true
    val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            worn = event.values[0] != 0f
            answered.countDown()
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
    }
    manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    answered.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)
    manager.unregisterListener(listener)
    return worn
}
