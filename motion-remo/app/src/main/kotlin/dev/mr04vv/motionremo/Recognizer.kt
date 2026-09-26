package dev.mr04vv.motionremo

import android.util.Log
import dev.mr04vv.motionremo.core.JevClient
import dev.mr04vv.motionremo.core.MotionFeatures
import dev.mr04vv.motionremo.core.RemoAction
import dev.mr04vv.motionremo.core.RemoClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val TAG = "MotionRemo"
const val CONFIDENCE_THRESHOLD = 0.6

fun describe(action: RemoAction): String = when (action) {
    is RemoAction.Light -> "${action.applianceName}: ${action.button}"
    is RemoAction.Aircon -> "${action.applianceName}: ${if (action.on) "電源ON" else "電源OFF"}"
    RemoAction.Ignore -> "何もしない"
}

/** Recognizes a motion with Jev and runs the matching Remo action; returns a status line for the UI. */
class Recognizer(private val store: GestureStore, private val settings: Settings) {
    suspend fun run(features: MotionFeatures): String = withContext(Dispatchers.IO) {
        try {
            val gestures = store.load()
            if (gestures.isEmpty()) return@withContext "ジェスチャーが未登録です"
            val started = System.currentTimeMillis()
            val response = JevClient(settings.jevKey).recognize(features, gestures)
            val judged = System.currentTimeMillis() - started
            val confidence = response.answer?.confidence?.let { "%.2f".format(it) } ?: "-"
            val gesture = response.decision(CONFIDENCE_THRESHOLD)?.let { id -> gestures.firstOrNull { it.id == id } }
                ?: return@withContext "該当なし（確信度 $confidence、判定 ${judged}ms）"
            RemoClient(settings.remoToken).send(gesture.action)
            val total = System.currentTimeMillis() - started
            "「${gesture.name}」→ ${describe(gesture.action)}（確信度 $confidence、判定 ${judged}ms、合計 ${total}ms）"
        } catch (e: Exception) {
            Log.e(TAG, "recognition or remo call failed", e)
            "失敗しました: ${e.message}"
        }
    }
}
