package dev.mr04vv.motionremo.core

import kotlin.math.sqrt

/** Decides whether the phone is moving, from linear acceleration or rotation speed since the previous sample. */
class MotionDetector(
    private val accelThreshold: Double = 1.5,
    private val angularSpeedThreshold: Double = 1.0,
) {
    fun isMoving(previous: MotionSample?, sample: MotionSample): Boolean {
        if (sqrt(sample.accel.sumOf { it * it }) > accelThreshold) return true
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

/** Samples kept before the first moving one, so the start of a gesture is not clipped. */
const val PRE_ROLL_MS = 60L

/**
 * Cuts a hold-to-record capture down to the same span [MotionSegmenter] would emit:
 * from [PRE_ROLL_MS] before the first moving sample through the last moving one.
 * Registration and both recognition paths then compare like with like.
 */
fun trimToMotion(samples: List<MotionSample>, detector: MotionDetector = MotionDetector()): List<MotionSample> {
    val moving = samples.indices.map { i -> detector.isMoving(samples.getOrNull(i - 1), samples[i]) }
    val first = moving.indexOf(true)
    if (first < 0) return emptyList()
    val last = moving.lastIndexOf(true)
    // Mirrors the segmenter, whose pre-roll window is measured from the sample before the first moving one.
    val anchor = samples[maxOf(first - 1, 0)].timeMs
    val start = samples.indexOfFirst { it.timeMs >= anchor - PRE_ROLL_MS }.coerceAtMost(first)
    return samples.subList(start, last + 1)
}

/**
 * Cuts a continuous sensor stream into gestures: stillness, then motion, then stillness again.
 * Motion that does not start from stillness, or lasts longer than a gesture (e.g. walking), is dropped.
 */
class MotionSegmenter(
    private val detector: MotionDetector = MotionDetector(),
    private val stillBeforeMs: Long = 300,
    private val stillAfterMs: Long = 400,
    private val maxGestureMs: Long = MotionFeatures.MAX_DURATION_MS.toLong(),
) {
    private var previous: MotionSample? = null
    private var stillSince: Long? = null
    private var armed = false
    private val preRoll = ArrayDeque<MotionSample>()
    private val recording = mutableListOf<MotionSample>()
    private var recordingStart: Long? = null
    private var lastMovingAt = 0L

    /** True while a gesture has started and not yet ended. */
    val isRecording: Boolean get() = recordingStart != null

    /** Feed one sample; returns the samples of a finished gesture, or null. */
    fun push(sample: MotionSample): List<MotionSample>? {
        val moving = detector.isMoving(previous, sample)
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
        while (sample.timeMs - preRoll.first().timeMs > PRE_ROLL_MS) preRoll.removeFirst()
        return null
    }

    private fun reset(stillSince: Long?) {
        recording.clear()
        recordingStart = null
        preRoll.clear()
        armed = false
        this.stillSince = stillSince
    }
}
