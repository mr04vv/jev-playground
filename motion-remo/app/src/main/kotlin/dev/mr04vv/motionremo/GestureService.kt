package dev.mr04vv.motionremo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.SensorManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import dev.mr04vv.motionremo.core.MotionFeatures
import dev.mr04vv.motionremo.core.MotionSample
import dev.mr04vv.motionremo.core.MotionSegmenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Recognizes gestures without the hold button, in two modes:
 * - continuous: while background listening is on and the screen is on;
 * - one-shot: for a few seconds after the home screen widget is tapped, then the service stops itself.
 */
class GestureService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var sampler: SensorSampler
    private lateinit var recognizer: Recognizer
    private var segmenter = MotionSegmenter()
    private var listening = false
    private var continuous = false
    private var oneShotActive = false
    private var busy = false
    private var cooldownUntil = 0L

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!continuous) return
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> startListening()
                Intent.ACTION_SCREEN_OFF -> if (!oneShotActive) stopListening()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        recognizer = Recognizer(GestureStore(this), Settings(this))
        sampler = SensorSampler(getSystemService(SensorManager::class.java)) { sample ->
            segmenter.push(sample)?.let(::onGesture)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "ジェスチャー待機", NotificationManager.IMPORTANCE_LOW),
        )
        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_LISTEN_ONCE) {
            startForeground(ONGOING_ID, ongoing("${ONE_SHOT_WINDOW_MS / 1000} 秒以内に動かしてください"), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            startOneShot()
        } else {
            continuous = true
            startForeground(ONGOING_ID, ongoing("待機中（画面オンの間、動かすだけで操作）"), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            if (getSystemService(PowerManager::class.java).isInteractive) startListening()
        }
        return if (continuous) START_STICKY else START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopListening()
        unregisterReceiver(screenReceiver)
        scope.cancel()
        super.onDestroy()
    }

    private fun startListening() {
        if (listening) return
        segmenter = MotionSegmenter()
        sampler.start()
        listening = true
    }

    private fun stopListening() {
        if (!listening) return
        sampler.stop()
        listening = false
    }

    private fun startOneShot() {
        oneShotActive = true
        startListening()
        getSystemService(Vibrator::class.java).vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed(::onOneShotWindowEnded, ONE_SHOT_WINDOW_MS)
    }

    // A gesture already under way may finish; otherwise give up.
    private fun onOneShotWindowEnded() {
        if (segmenter.isRecording || busy) {
            handler.postDelayed(::finishOneShot, MotionFeatures.MAX_DURATION_MS.toLong())
        } else {
            finishOneShot("動きを検出できませんでした")
        }
    }

    private fun finishOneShot(result: String? = null) {
        if (!oneShotActive) return
        oneShotActive = false
        handler.removeCallbacksAndMessages(null)
        result?.let(::postResult)
        if (continuous) {
            getSystemService(NotificationManager::class.java).notify(ONGOING_ID, ongoing("待機中（画面オンの間、動かすだけで操作）"))
            if (!getSystemService(PowerManager::class.java).isInteractive) stopListening()
        } else {
            stopListening()
            stopSelf()
        }
    }

    private fun onGesture(samples: List<MotionSample>) {
        // Skip while a previous gesture is still being handled, and briefly after an action ran.
        if (busy || SystemClock.elapsedRealtime() < cooldownUntil) return
        val features = MotionFeatures.from(samples) ?: return
        busy = true
        scope.launch {
            val status = recognizer.run(features)
            cooldownUntil = SystemClock.elapsedRealtime() + COOLDOWN_MS
            busy = false
            if (oneShotActive) {
                finishOneShot(status)
            } else {
                getSystemService(NotificationManager::class.java).notify(ONGOING_ID, ongoing(status))
            }
        }
    }

    private fun openAppIntent() =
        PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

    private fun ongoing(text: String): Notification = Notification.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_menu_rotate)
        .setContentTitle("Motion Remo")
        .setContentText(text)
        .setContentIntent(openAppIntent())
        .setOnlyAlertOnce(true)
        .setOngoing(true)
        .build()

    // Separate from the foreground notification so it stays after the one-shot service stops.
    private fun postResult(text: String) {
        getSystemService(NotificationManager::class.java).notify(
            RESULT_ID,
            Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_rotate)
                .setContentTitle("Motion Remo")
                .setContentText(text)
                .setContentIntent(openAppIntent())
                .setAutoCancel(true)
                .setTimeoutAfter(RESULT_TIMEOUT_MS)
                .build(),
        )
    }

    companion object {
        private const val CHANNEL_ID = "gesture_listening"
        private const val ONGOING_ID = 1
        private const val RESULT_ID = 2
        private const val COOLDOWN_MS = 1500L
        private const val ONE_SHOT_WINDOW_MS = 3000L
        private const val RESULT_TIMEOUT_MS = 10_000L
        const val ACTION_LISTEN_ONCE = "dev.mr04vv.motionremo.LISTEN_ONCE"

        fun start(context: Context) = context.startForegroundService(Intent(context, GestureService::class.java))

        fun stop(context: Context) = context.stopService(Intent(context, GestureService::class.java))

        fun listenOnceIntent(context: Context) =
            Intent(context, GestureService::class.java).setAction(ACTION_LISTEN_ONCE)
    }
}
