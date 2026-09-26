package dev.mr04vv.motionremo.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private fun aboutZ(angle: Double) = Quaternion(0.0, 0.0, sin(angle / 2), cos(angle / 2))

private fun assertVec(expected: List<Double>, actual: DoubleArray, tolerance: Double = 1e-9) {
    expected.zip(actual.toList()).forEach { (e, a) -> assertEquals(e, a, tolerance) }
}

class MotionTest {
    @Test
    fun relativeRotationIsZeroWhenOrientationIsUnchanged() {
        val q = aboutZ(0.7)
        assertVec(listOf(0.0, 0.0, 0.0), relativeRotation(q, q))
    }

    @Test
    fun relativeRotationIsAxisTimesAngle() {
        assertVec(listOf(0.0, 0.0, PI / 2), relativeRotation(aboutZ(0.3), aboutZ(0.3 + PI / 2)))
    }

    @Test
    fun resampleInterpolatesByTime() {
        val series = listOf(0L to doubleArrayOf(0.0), 100L to doubleArrayOf(10.0), 400L to doubleArrayOf(40.0))
        val out = resampleByTime(series, 5)
        assertEquals(listOf(0.0, 10.0, 20.0, 30.0, 40.0), out.map { it[0] })
    }

    private fun turn(durationMs: Long, degrees: Double, accelScale: Double) = (0..40).map { i ->
        val t = durationMs * i / 40
        val angle = Math.toRadians(degrees) * i / 40
        MotionSample(t, aboutZ(angle), doubleArrayOf(accelScale * sin(PI * i / 40), 0.0, 0.0))
    }

    @Test
    fun featuresAreRelativeResampledAndNormalized() {
        val features = assertNotNull(MotionFeatures.from(turn(600, 90.0, accelScale = 8.0)))
        assertEquals(MotionFeatures.POINT_COUNT, features.rotation.size)
        assertEquals(listOf(0.0, 0.0, 0.0), features.rotation.first())
        assertEquals(listOf(0.0, 0.0, 1.57), features.rotation.last())
        assertEquals(1.0, features.accel.maxOf { it[0] })
        assertEquals(8.0, features.peakAccel)
        assertEquals(600, features.durationMs)
    }

    @Test
    fun featuresRejectMotionsThatAreTooShortOrTooLong() {
        assertNull(MotionFeatures.from(turn(50, 90.0, 8.0)))
        assertNull(MotionFeatures.from(turn(10_000, 90.0, 8.0)))
        assertNull(MotionFeatures.from(turn(600, 90.0, 8.0).take(3)))
    }
}
