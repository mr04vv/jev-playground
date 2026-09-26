package dev.mr04vv.motionremo.core

import kotlin.math.sqrt

/**
 * Cuts a continuous sensor stream into gestures: stillness, then motion, then stillness again.
 * Motion that does not start from stillness, or lasts longer than a gesture (e.g. walking), is dropped.
 */
class MotionSegmenter(
    private val accelThreshold: Double = 1.5,
    private val angularSpeedThreshold: Double = 1.0,
    private val stillBeforeMs: Long = 300,
    private val stillAfterMs: Long = 400,
    private val maxGestureMs: Long = MotionFeatures.MAX_DURATION_MS.toLong(),
    private val preRollMs: Long = 60,
) {
    private var previous: MotionSample? = null
    private var stillSince: Long? = null
    private var armed = false
    private val preRoll = ArrayDeque<MotionSample>()
    private val recording = mutableListOf<MotionSample>()
    private var recordingStart: Long? = null
    private var lastMovingAt = 0L

    /** Feed one sample; returns the samples of a finished gesture, or null. */
    fun push(sample: MotionSample): List<MotionSample>? {
        val moving = isMoving(sample)
        previous = sample
        val start = recordingStart ?: return idle(sample, moving)

        recording += sample
        if (moving) lastMovingAt = sample.timeMs
        if (sample.timeMs - start > maxGestureMs) {
            reset(stillSince = null)
            return null
        }
        if (!moving && sample.timeMs - lastMovingAt >= stillAfterMs) {
            val gesture = recording.filter { it.timeMs <= lastMovingAt }
            reset(stillSince = lastMovingAt)
            armed = sample.timeMs - lastMovingAt >= stillBeforeMs
            return gesture
        }
        return null
    }

    private fun idle(sample: MotionSample, moving: Boolean): List<MotionSample>? {
        if (moving) {
            if (armed) {
                recording += preRoll
                recording += sample
                recordingStart = sample.timeMs
                lastMovingAt = sample.timeMs
            }
            armed = false
            stillSince = null
        } else {
            val since = stillSince ?: sample.timeMs.also { stillSince = it }
            if (sample.timeMs - since >= stillBeforeMs) armed = true
        }
        preRoll.addLast(sample)
        while (sample.timeMs - preRoll.first().timeMs > preRollMs) preRoll.removeFirst()
        return null
    }

    private fun reset(stillSince: Long?) {
        recording.clear()
        recordingStart = null
        preRoll.clear()
        armed = false
        this.stillSince = stillSince
    }

    private fun isMoving(sample: MotionSample): Boolean {
        val accel = sqrt(sample.accel.sumOf { it * it })
        if (accel > accelThreshold) return true
        val prev = previous ?: return false
        val dt = (sample.timeMs - prev.timeMs) / MILLIS_PER_SECOND
        if (dt <= 0) return false
        val angle = sqrt(relativeRotation(prev.rotation, sample.rotation).sumOf { it * it })
        return angle / dt > angularSpeedThreshold
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1000.0
    }
}
