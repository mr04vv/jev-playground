package dev.mr04vv.motionremo.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val features = MotionFeatures(listOf(listOf(0.0, 0.0, 0.0)), listOf(listOf(0.0, 0.0, 0.0)), 1.0, 500)
private val light = RemoAction.Light(applianceId = "a1", applianceName = "照明", button = "on")

class JevTest {
    @Test
    fun requestListsGestureIdsPlusUnknownAsChoices() {
        val gestures = listOf(RegisteredGesture("g1", "円", light, listOf(features)))
        val json = Json.parseToJsonElement(buildJevRequest(features, gestures)).jsonObject
        assertEquals("jev-latest", json["model"]!!.jsonPrimitive.content)
        val examples = json["state"]!!.jsonObject["examples"]!!.jsonArray
        assertEquals("g1", examples[0].jsonObject["id"]!!.jsonPrimitive.content)
        val question = json["questions"]!!.jsonObject[JEV_QUESTION_KEY]!!.jsonObject
        assertEquals("choice", question["type"]!!.jsonPrimitive.content)
        assertEquals(setOf("g1", JEV_UNKNOWN), question["criteria"]!!.jsonObject.keys)
    }

    @Test
    fun decisionRequiresAKnownChoiceAboveTheThreshold() {
        val hit = parseJevResponse("""{"answers":{"gesture":{"type":"choice","choice":"g1","confidence":0.8}}}""")
        assertEquals("g1", hit.decision(threshold = 0.6))
        assertNull(hit.decision(threshold = 0.9))
        val unknown = parseJevResponse("""{"answers":{"gesture":{"type":"choice","choice":"unknown","confidence":0.99}}}""")
        assertNull(unknown.decision(threshold = 0.6))
    }

    @Test
    fun gestureRoundTripsThroughJson() {
        val gesture = RegisteredGesture("g1", "円", RemoAction.Aircon("a2", "エアコン", on = false), listOf(features))
        val text = Json.encodeToString(RegisteredGesture.serializer(), gesture)
        assertEquals(gesture, Json.decodeFromString(RegisteredGesture.serializer(), text))
    }
}
