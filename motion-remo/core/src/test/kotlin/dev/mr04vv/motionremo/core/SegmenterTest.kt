package dev.mr04vv.motionremo.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val STEP = 20L
private val identity = Quaternion(0.0, 0.0, 0.0, 1.0)

private fun still(from: Long, durationMs: Long) = (0 until durationMs / STEP).map { i ->
    MotionSample(from + i * STEP, identity, doubleArrayOf(0.05, 0.0, 0.0))
}

private fun shake(from: Long, durationMs: Long) = (0 until durationMs / STEP).map { i ->
    MotionSample(from + i * STEP, identity, doubleArrayOf(10 * sin(2 * PI * i * STEP / 300.0) + 5, 0.0, 0.0))
}

private fun twist(from: Long, durationMs: Long) = (0 until durationMs / STEP).map { i ->
    val angle = PI / 2 * i * STEP / durationMs
    MotionSample(from + i * STEP, Quaternion(0.0, sin(angle / 2), 0.0, cos(angle / 2)), doubleArrayOf(0.0, 0.0, 0.0))
}

private fun MotionSegmenter.feed(samples: List<MotionSample>) = samples.mapNotNull { push(it) }

class SegmenterTest {
    @Test
    fun emitsOneSegmentForStillMoveStill() {
        val segmenter = MotionSegmenter()
        val segments = segmenter.feed(still(0, 600) + shake(600, 600) + still(1200, 800))
        assertEquals(1, segments.size)
        val duration = segments[0].last().timeMs - segments[0].first().timeMs
        assertTrue(duration in 550..700, "duration was $duration")
    }

    @Test
    fun detectsRotationOnlyMotions() {
        val segments = MotionSegmenter().feed(still(0, 600) + twist(600, 700) + still(1300, 800))
        assertNotNull(segments.singleOrNull())
    }

    @Test
    fun ignoresMotionThatDidNotStartFromStillness() {
        val segments = MotionSegmenter().feed(shake(0, 600) + still(600, 800))
        assertEquals(0, segments.size)
    }

    @Test
    fun dropsMotionThatNeverStops() {
        // Like walking: continuous movement longer than a gesture, then stillness.
        val segments = MotionSegmenter().feed(still(0, 600) + shake(600, 6000) + still(6600, 800))
        assertEquals(0, segments.size)
    }

    @Test
    fun rearmsAfterStillnessForTheNextGesture() {
        val segments = MotionSegmenter().feed(
            still(0, 600) + shake(600, 500) + still(1100, 800) + twist(1900, 600) + still(2500, 800),
        )
        assertEquals(2, segments.size)
    }
}
