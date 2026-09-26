package dev.mr04vv.motionremo.core

import java.net.URLEncoder

/** A Nature Remo Cloud API call: POST to [path] with a form-encoded body. */
data class RemoRequest(val path: String, val form: Map<String, String>)

// Per the API spec, "power-off" turns an air conditioner off and an empty button turns it on.
private const val AIRCON_POWER_OFF = "power-off"
private const val AIRCON_POWER_ON = ""

/** The API call for this action, or null for [RemoAction.Ignore]. */
fun RemoAction.toRequest(): RemoRequest? = when (this) {
    is RemoAction.Light -> RemoRequest("/1/appliances/$applianceId/light", mapOf("button" to button))
    is RemoAction.Aircon -> RemoRequest(
        "/1/appliances/$applianceId/aircon_settings",
        mapOf("button" to if (on) AIRCON_POWER_ON else AIRCON_POWER_OFF),
    )
    RemoAction.Ignore -> null
}

fun formEncode(form: Map<String, String>): String =
    form.entries.joinToString("&") { (k, v) -> "${URLEncoder.encode(k, Charsets.UTF_8)}=${URLEncoder.encode(v, Charsets.UTF_8)}" }
