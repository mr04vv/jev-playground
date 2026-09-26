package dev.mr04vv.motionremo

import android.content.Context
import dev.mr04vv.motionremo.core.RegisteredGesture
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** Gestures as JSON in app-private storage. */
class GestureStore(context: Context) {
    private val file = File(context.filesDir, "gestures.json")
    private val serializer = ListSerializer(RegisteredGesture.serializer())

    fun load(): List<RegisteredGesture> = if (file.exists()) Json.decodeFromString(serializer, file.readText()) else emptyList()

    fun save(gestures: List<RegisteredGesture>) = file.writeText(Json.encodeToString(serializer, gestures))
}

/** API keys in app-private SharedPreferences (prototype; not encrypted at rest). */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var jevKey: String
        get() = prefs.getString(KEY_JEV, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_JEV, value).apply()

    var remoToken: String
        get() = prefs.getString(KEY_REMO, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_REMO, value).apply()

    private companion object {
        const val KEY_JEV = "jev_api_key"
        const val KEY_REMO = "remo_token"
    }
}
