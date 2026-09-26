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
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import dev.mr04vv.motionremo.core.MotionFeatures
import dev.mr04vv.motionremo.core.MotionSample
import dev.mr04vv.motionremo.core.MotionSegmenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Listens for gestures without the hold button while the screen is on.
 * Sensors stop when the screen turns off, so the device can sleep normally.
 */
class GestureService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var sampler: SensorSampler
    private lateinit var recognizer: Recognizer
    private var segmenter = MotionSegmenter()
    private var busy = false
    private var cooldownUntil = 0L

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> startListening()
                Intent.ACTION_SCREEN_OFF -> stopListening()
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
        startForeground(NOTIFICATION_ID, notification("待機中（画面オンの間、動かすだけで操作）"), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        })
        if (getSystemService(PowerManager::class.java).isInteractive) startListening()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        stopListening()
        unregisterReceiver(screenReceiver)
        scope.cancel()
        super.onDestroy()
    }

    private fun startListening() {
        segmenter = MotionSegmenter()
        sampler.start()
    }

    private fun stopListening() = sampler.stop()

    private fun onGesture(samples: List<MotionSample>) {
        // Skip while a previous gesture is still being handled, and briefly after an action ran.
        if (busy || SystemClock.elapsedRealtime() < cooldownUntil) return
        val features = MotionFeatures.from(samples) ?: return
        busy = true
        scope.launch {
            val status = recognizer.run(features)
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(status))
            cooldownUntil = SystemClock.elapsedRealtime() + COOLDOWN_MS
            busy = false
        }
    }

    private fun notification(text: String): Notification {
        val openApp = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_rotate)
            .setContentTitle("Motion Remo")
            .setContentText(text)
            .setContentIntent(openApp)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "gesture_listening"
        private const val NOTIFICATION_ID = 1
        private const val COOLDOWN_MS = 1500L

        fun start(context: Context) = context.startForegroundService(Intent(context, GestureService::class.java))

        fun stop(context: Context) = context.stopService(Intent(context, GestureService::class.java))
    }
}
