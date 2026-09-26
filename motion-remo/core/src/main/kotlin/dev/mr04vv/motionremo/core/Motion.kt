package dev.mr04vv.motionremo.core

import kotlinx.serialization.Serializable
import kotlin.math.atan2
import kotlin.math.round
import kotlin.math.sqrt

/** Unit quaternion in Android's rotation-vector order: x, y, z, then the scalar w. */
data class Quaternion(val x: Double, val y: Double, val z: Double, val w: Double) {
    fun conjugate() = Quaternion(-x, -y, -z, w)

    operator fun times(o: Quaternion) = Quaternion(
        w * o.x + x * o.w + y * o.z - z * o.y,
        w * o.y - x * o.z + y * o.w + z * o.x,
        w * o.z + x * o.y - y * o.x + z * o.w,
        w * o.w - x * o.x - y * o.y - z * o.z,
    )
}

/** Rotation from [from] to [to], expressed as axis * angle (radians) in the starting frame. */
fun relativeRotation(from: Quaternion, to: Quaternion): DoubleArray {
    var q = from.conjugate() * to
    if (q.w < 0) q = Quaternion(-q.x, -q.y, -q.z, -q.w)
    val norm = sqrt(q.x * q.x + q.y * q.y + q.z * q.z)
    if (norm == 0.0) return doubleArrayOf(0.0, 0.0, 0.0)
    val angle = 2 * atan2(norm, q.w)
    return doubleArrayOf(q.x / norm * angle, q.y / norm * angle, q.z / norm * angle)
}

/** Linearly interpolate a timestamped series into [count] evenly spaced samples. */
fun resampleByTime(series: List<Pair<Long, DoubleArray>>, count: Int): List<DoubleArray> {
    val start = series.first().first
    val end = series.last().first
    var j = 0
    return (0 until count).map { i ->
        val t = start + (end - start) * i.toDouble() / (count - 1)
        while (j < series.size - 2 && series[j + 1].first < t) j++
        val (t0, v0) = series[j]
        val (t1, v1) = series[minOf(j + 1, series.size - 1)]
        val f = if (t1 == t0) 0.0 else ((t - t0) / (t1 - t0)).coerceIn(0.0, 1.0)
        DoubleArray(v0.size) { k -> v0[k] + (v1[k] - v0[k]) * f }
    }
}

data class MotionSample(val timeMs: Long, val rotation: Quaternion, val accel: DoubleArray)

// Adding 0.0 turns -0.0 into 0.0 so equal motions serialize identically.
private fun round(value: Double, decimals: Int): Double {
    val scale = Math.pow(10.0, decimals.toDouble())
    return round(value * scale) / scale + 0.0
}

/** Motion features sent to Jev: rotation relative to the starting pose, and acceleration shape. */
@Serializable
data class MotionFeatures(
    /** Relative rotation vectors (radians), [x, y, z] per sample. */
    val rotation: List<List<Double>>,
    /** Linear acceleration divided by [peakAccel], [x, y, z] per sample. */
    val accel: List<List<Double>>,
    /** Largest linear acceleration magnitude in m/s², so strength still counts. */
    val peakAccel: Double,
    val durationMs: Int,
) {
    companion object {
        const val POINT_COUNT = 32
        const val MIN_SAMPLES = 10
        const val MIN_DURATION_MS = 200
        const val MAX_DURATION_MS = 4000

        fun from(samples: List<MotionSample>): MotionFeatures? {
            if (samples.size < MIN_SAMPLES) return null
            val duration = (samples.last().timeMs - samples.first().timeMs).toInt()
            if (duration !in MIN_DURATION_MS..MAX_DURATION_MS) return null

            val start = samples.first().rotation
            val rotation = resampleByTime(samples.map { it.timeMs to relativeRotation(start, it.rotation) }, POINT_COUNT)
            val accel = resampleByTime(samples.map { it.timeMs to it.accel }, POINT_COUNT)
            val peak = samples.maxOf { s -> sqrt(s.accel.sumOf { it * it }) }
            val divisor = if (peak > 0) peak else 1.0
            return MotionFeatures(
                rotation = rotation.map { v -> v.map { round(it, 2) } },
                accel = accel.map { v -> v.map { round(it / divisor, 2) } },
                peakAccel = round(peak, 1),
                durationMs = duration,
            )
        }
    }
}
