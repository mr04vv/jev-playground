package dev.mr04vv.motionremo

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dev.mr04vv.motionremo.core.MotionSample
import dev.mr04vv.motionremo.core.Quaternion

/**
 * Records orientation (game rotation vector, no magnetometer) and linear acceleration while active.
 * A sample is taken on every rotation event, paired with the latest acceleration reading.
 */
class MotionRecorder(private val sensors: SensorManager) : SensorEventListener {
    private val samples = mutableListOf<MotionSample>()
    private var latestAccel = DoubleArray(3)
    private var startNanos = 0L

    val isAvailable: Boolean
        get() = sensors.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) != null &&
            sensors.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) != null

    fun start() {
        samples.clear()
        latestAccel = DoubleArray(3)
        startNanos = 0L
        sensors.registerListener(this, sensors.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR), SensorManager.SENSOR_DELAY_GAME)
        sensors.registerListener(this, sensors.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION), SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop(): List<MotionSample> {
        sensors.unregisterListener(this)
        return samples.toList()
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_LINEAR_ACCELERATION -> latestAccel = DoubleArray(3) { event.values[it].toDouble() }
            Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                if (startNanos == 0L) startNanos = event.timestamp
                val v = event.values
                // values[3] (the scalar part) is optional on some devices; derive it from the unit length.
                val w = if (v.size > 3) v[3].toDouble() else Math.sqrt(maxOf(0.0, 1.0 - v[0] * v[0] - v[1] * v[1] - v[2] * v[2].toDouble()))
                samples += MotionSample(
                    timeMs = (event.timestamp - startNanos) / NANOS_PER_MILLI,
                    rotation = Quaternion(v[0].toDouble(), v[1].toDouble(), v[2].toDouble(), w),
                    accel = latestAccel,
                )
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
