package dev.mr04vv.motionremo.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URI

class ApiException(val status: Int, message: String) : Exception(message)

private const val TIMEOUT_MS = 10_000
private val json = Json { ignoreUnknownKeys = true }

private fun request(url: String, method: String, token: String, contentType: String?, body: String?, requestIdHeader: String?): String {
    val connection = URI(url).toURL().openConnection() as HttpURLConnection
    try {
        connection.requestMethod = method
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Accept", "application/json")
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", contentType)
            connection.outputStream.use { it.write(body.toByteArray()) }
        }
        val status = connection.responseCode
        val text = (if (status in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (status !in 200..299) {
            val requestId = requestIdHeader?.let { connection.getHeaderField(it) } ?: "none"
            throw ApiException(status, "$method $url -> $status (request $requestId): $text")
        }
        return text
    } finally {
        connection.disconnect()
    }
}

class JevClient(private val apiKey: String) {
    fun recognize(input: MotionFeatures, gestures: List<RegisteredGesture>): JevResponse = parseJevResponse(
        request(ENDPOINT, "POST", apiKey, "application/json", buildJevRequest(input, gestures), "x-typesafe-request-id"),
    )

    companion object {
        const val ENDPOINT = "https://api.typesafe.ai/v1/systemone"
    }
}

@Serializable
data class RemoLightButton(val name: String, val label: String = "")

@Serializable
data class RemoLight(val buttons: List<RemoLightButton> = emptyList())

@Serializable
data class RemoAppliance(val id: String, val type: String, val nickname: String = "", val light: RemoLight? = null)

class RemoClient(private val token: String) {
    fun appliances(): List<RemoAppliance> =
        json.decodeFromString(ListSerializer(RemoAppliance.serializer()), request("$BASE_URL/1/appliances", "GET", token, null, null, null))

    fun send(action: RemoAction) {
        val call = action.toRequest() ?: return
        request("$BASE_URL${call.path}", "POST", token, "application/x-www-form-urlencoded", formEncode(call.form), null)
    }

    companion object {
        const val BASE_URL = "https://api.nature.global"
        const val TYPE_LIGHT = "LIGHT"
        const val TYPE_AIRCON = "AC"
    }
}
