package dev.mr04vv.motionremo.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

const val JEV_MODEL = "jev-latest"
const val JEV_QUESTION_KEY = "gesture"
const val JEV_UNKNOWN = "unknown"

private const val INSTRUCTIONS =
    "Which registered gesture is the input phone motion? Compare the input with each gesture's samples " +
        "by the shape and order of rotation and acceleration over time. Ignore small differences in speed and size."
private const val UNKNOWN_DESCRIPTION = "The input does not clearly match any registered gesture."

private val json = Json { ignoreUnknownKeys = true }

/** Whistle-style request: the input and registered samples in state, one choice over gesture ids plus unknown. */
fun buildJevRequest(input: MotionFeatures, gestures: List<RegisteredGesture>): String = buildJsonObject {
    put("model", JEV_MODEL)
    putJsonObject("state") {
        put("input", json.encodeToJsonElement(MotionFeatures.serializer(), input))
        putJsonArray("examples") {
            gestures.forEach { g ->
                addJsonObject {
                    put("id", g.id)
                    put("name", g.name)
                    putJsonArray("samples") { g.samples.forEach { add(json.encodeToJsonElement(MotionFeatures.serializer(), it)) } }
                }
            }
        }
    }
    putJsonObject("questions") {
        putJsonObject(JEV_QUESTION_KEY) {
            put("type", "choice")
            put("instructions", INSTRUCTIONS)
            putJsonObject("criteria") {
                gestures.forEach { put(it.id, it.name) }
                put(JEV_UNKNOWN, UNKNOWN_DESCRIPTION)
            }
        }
    }
}.toString()

@Serializable
data class JevAnswer(val choice: String, val confidence: Double)

@Serializable
data class JevResponse(val answers: Map<String, JevAnswer>) {
    val answer: JevAnswer? get() = answers[JEV_QUESTION_KEY]

    /** The gesture id to act on, or null when Jev chose unknown or is not confident enough. */
    fun decision(threshold: Double): String? =
        answer?.takeIf { it.choice != JEV_UNKNOWN && it.confidence >= threshold }?.choice
}

fun parseJevResponse(text: String): JevResponse = json.decodeFromString(JevResponse.serializer(), text)
