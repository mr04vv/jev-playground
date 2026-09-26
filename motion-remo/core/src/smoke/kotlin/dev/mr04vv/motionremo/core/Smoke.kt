package dev.mr04vv.motionremo.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val SAMPLES_PER_GESTURE = 3
private const val TRIALS_PER_GESTURE = 4
private const val STEP_MS = 20L

private fun axisAngle(ax: Double, ay: Double, az: Double, angle: Double) =
    Quaternion(ax * sin(angle / 2), ay * sin(angle / 2), az * sin(angle / 2), cos(angle / 2))

/** A motion over [durationMs]: rotation angle and acceleration as functions of progress 0..1. */
private fun motion(durationMs: Long, rotation: (Double) -> Quaternion, accel: (Double) -> DoubleArray): List<MotionSample> {
    val speed = Random.nextDouble(0.8, 1.25)
    val total = (durationMs * speed).toLong()
    val strength = Random.nextDouble(0.7, 1.4)
    return (0..total / STEP_MS).map { i ->
        val p = i * STEP_MS.toDouble() / total
        val noise = { Random.nextDouble(-0.4, 0.4) }
        MotionSample(i * STEP_MS, rotation(p), accel(p).map { it * strength + noise() }.toDoubleArray())
    }
}

private val gestures: List<Pair<String, () -> List<MotionSample>>> = listOf(
    "右にひねる" to { motion(700, { p -> axisAngle(0.0, 1.0, 0.0, PI / 2 * p) }, { doubleArrayOf(0.0, 0.0, 0.0) }) },
    "左にひねる" to { motion(700, { p -> axisAngle(0.0, 1.0, 0.0, -PI / 2 * p) }, { doubleArrayOf(0.0, 0.0, 0.0) }) },
    "上に振る" to { motion(500, { p -> axisAngle(1.0, 0.0, 0.0, PI / 3 * p) }, { p -> doubleArrayOf(0.0, 12 * sin(2 * PI * p), 0.0) }) },
    "シェイク" to { motion(900, { axisAngle(0.0, 0.0, 1.0, 0.0) }, { p -> doubleArrayOf(15 * sin(6 * PI * p), 0.0, 0.0) }) },
    "円を描く" to { motion(1200, { axisAngle(0.0, 0.0, 1.0, 0.0) }, { p -> doubleArrayOf(8 * cos(2 * PI * p), 0.0, 8 * sin(2 * PI * p)) }) },
)

private fun features(make: () -> List<MotionSample>) = requireNotNull(MotionFeatures.from(make())) { "synthetic motion rejected" }

fun main() {
    val apiKey = System.getenv("TYPESAFE_API_KEY")?.takeIf { it.isNotBlank() } ?: error("TYPESAFE_API_KEY is not set")
    val registered = gestures.map { (name, make) ->
        RegisteredGesture(name, name, RemoAction.Light("none", "none", "on"), List(SAMPLES_PER_GESTURE) { features(make) })
    }
    val client = JevClient(apiKey)
    var correct = 0
    var total = 0
    for ((name, make) in gestures) {
        repeat(TRIALS_PER_GESTURE) {
            val started = System.currentTimeMillis()
            val answer = client.recognize(features(make), registered).answer
            val ms = System.currentTimeMillis() - started
            val hit = answer?.choice == name
            if (hit) correct++
            total++
            println("${if (hit) "ok  " else "MISS"} expected=$name got=${answer?.choice} confidence=${"%.2f".format(answer?.confidence ?: 0.0)} ${ms}ms")
        }
    }
    println("accuracy $correct/$total")
}
