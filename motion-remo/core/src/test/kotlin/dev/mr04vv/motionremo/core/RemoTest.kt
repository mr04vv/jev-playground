package dev.mr04vv.motionremo.core

import kotlin.test.Test
import kotlin.test.assertEquals

class RemoTest {
    @Test
    fun lightPressesTheNamedButton() {
        val request = RemoAction.Light("a1", "照明", "off").toRequest()
        assertEquals("/1/appliances/a1/light", request.path)
        assertEquals(mapOf("button" to "off"), request.form)
    }

    @Test
    fun airconOffSendsPowerOffAndOnSendsEmptyButton() {
        assertEquals(mapOf("button" to "power-off"), RemoAction.Aircon("a2", "エアコン", on = false).toRequest().form)
        val on = RemoAction.Aircon("a2", "エアコン", on = true).toRequest()
        assertEquals("/1/appliances/a2/aircon_settings", on.path)
        assertEquals(mapOf("button" to ""), on.form)
    }

    @Test
    fun formEncodingEscapesValues() {
        assertEquals("button=power-off&x=a+b%26c", formEncode(mapOf("button" to "power-off", "x" to "a b&c")))
    }
}
