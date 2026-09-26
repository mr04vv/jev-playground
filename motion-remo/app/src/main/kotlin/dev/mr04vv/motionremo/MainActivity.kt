package dev.mr04vv.motionremo

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.mr04vv.motionremo.core.MotionFeatures
import dev.mr04vv.motionremo.core.RegisteredGesture
import dev.mr04vv.motionremo.core.RemoAction
import dev.mr04vv.motionremo.core.RemoAppliance
import dev.mr04vv.motionremo.core.RemoClient
import dev.mr04vv.motionremo.core.trimToMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private const val SAMPLES_PER_GESTURE = 3
private const val NOTIFICATION_PERMISSION_REQUEST = 1

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val recorder = MotionRecorder(getSystemService(SensorManager::class.java))
        val store = GestureStore(this)
        val settings = Settings(this)
        // Results of widget and background gestures are shown as notifications.
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST)
        }
        setContent { MaterialTheme { App(recorder, store, settings) } }
    }
}

private enum class Screen { Main, Settings, Register }

/** A light button or aircon power choice offered for registration. */
private data class ActionOption(val label: String, val action: RemoAction)

private val ignoreOption = ActionOption("何もしない（持ち上げる・置くなどの誤作動よけ）", RemoAction.Ignore)

private fun optionsFor(appliances: List<RemoAppliance>): List<ActionOption> = listOf(ignoreOption) + appliances.flatMap { a ->
    val name = a.nickname.ifEmpty { a.id }
    when (a.type) {
        RemoClient.TYPE_LIGHT -> a.light?.buttons.orEmpty().map { b ->
            ActionOption("$name: ${b.label.ifEmpty { b.name }}", RemoAction.Light(a.id, name, b.name))
        }
        RemoClient.TYPE_AIRCON -> listOf(
            ActionOption("$name: 電源ON", RemoAction.Aircon(a.id, name, on = true)),
            ActionOption("$name: 電源OFF", RemoAction.Aircon(a.id, name, on = false)),
        )
        else -> emptyList()
    }
}

@Composable
private fun App(recorder: MotionRecorder, store: GestureStore, settings: Settings) {
    val scope = rememberCoroutineScope()
    val gestures = remember {
        mutableStateListOf<RegisteredGesture>().apply {
            runCatching { addAll(store.load()) }.onFailure { Log.e(TAG, "failed to load gestures", it) }
        }
    }
    var screen by remember { mutableStateOf(if (settings.jevKey.isEmpty() || settings.remoToken.isEmpty()) Screen.Settings else Screen.Main) }
    var status by remember { mutableStateOf(if (recorder.isAvailable) "ボタンを押しながらスマホを動かしてください" else "この端末には必要なセンサーがありません") }

    fun persist() = runCatching { store.save(gestures) }.onFailure {
        Log.e(TAG, "failed to save gestures", it)
        status = "保存に失敗しました: ${it.message}"
    }

    val recognizer = remember { Recognizer(store, settings) }

    fun recognize(features: MotionFeatures) {
        status = "判定中…"
        scope.launch { status = recognizer.run(features) }
    }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Screen.entries.forEach { s ->
                val label = when (s) {
                    Screen.Main -> "操作"
                    Screen.Settings -> "設定"
                    Screen.Register -> "登録"
                }
                if (s == screen) Button(onClick = {}) { Text(label) } else OutlinedButton(onClick = { screen = s }) { Text(label) }
            }
        }
        when (screen) {
            Screen.Main -> MainScreen(recorder, gestures, status, onDelete = { g ->
                gestures.remove(g)
                persist()
            }) { samples ->
                val features = MotionFeatures.from(trimToMotion(samples))
                if (features == null) status = "動きが短すぎるか長すぎます（0.2〜4 秒）" else recognize(features)
            }
            Screen.Settings -> SettingsScreen(settings) { status = "設定を保存しました" }
            Screen.Register -> RegisterScreen(recorder, settings) { gesture ->
                gestures += gesture
                persist()
                status = "「${gesture.name}」を登録しました"
                screen = Screen.Main
            }
        }
    }
}

/** Big round button that records motion only while it is held down. */
@Composable
private fun HoldToRecord(recorder: MotionRecorder, label: String, onRecorded: (List<dev.mr04vv.motionremo.core.MotionSample>) -> Unit) {
    var holding by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(200.dp)
                .background(if (holding) Color(0xFFE65100) else Color(0xFF1565C0), CircleShape)
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        holding = true
                        recorder.start()
                        tryAwaitRelease()
                        holding = false
                        onRecorded(recorder.stop())
                    })
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(if (holding) "記録中…" else label, color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun MainScreen(
    recorder: MotionRecorder,
    gestures: List<RegisteredGesture>,
    status: String,
    onDelete: (RegisteredGesture) -> Unit,
    onRecorded: (List<dev.mr04vv.motionremo.core.MotionSample>) -> Unit,
) {
    Text(status)
    HoldToRecord(recorder, "押しながら動かす", onRecorded)
    Text("登録済み", style = MaterialTheme.typography.titleMedium)
    if (gestures.isEmpty()) Text("まだありません。「登録」から追加してください")
    Column(Modifier.verticalScroll(rememberScrollState())) {
        gestures.forEach { g ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${g.name} → ${describe(g.action)}", Modifier.weight(1f))
                TextButton(onClick = { onDelete(g) }) { Text("削除") }
            }
        }
    }
}

@Composable
private fun SettingsScreen(settings: Settings, onSaved: () -> Unit) {
    val context = LocalContext.current
    var background by remember { mutableStateOf(settings.backgroundListening) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Log.w(TAG, "notification permission denied; the listening notification will be hidden")
        settings.backgroundListening = true
        background = true
        GestureService.start(context)
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("バックグラウンド待機（画面オンの間、動かすだけで操作）", Modifier.weight(1f))
        Switch(checked = background, onCheckedChange = { on ->
            if (on) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                settings.backgroundListening = false
                background = false
                GestureService.stop(context)
            }
        })
    }
    var jevKey by remember { mutableStateOf(settings.jevKey) }
    var remoToken by remember { mutableStateOf(settings.remoToken) }
    OutlinedTextField(jevKey, { jevKey = it }, Modifier.fillMaxWidth(), label = { Text("TypeSafe API キー") },
        singleLine = true, visualTransformation = PasswordVisualTransformation())
    OutlinedTextField(remoToken, { remoToken = it }, Modifier.fillMaxWidth(), label = { Text("Nature Remo アクセストークン") },
        singleLine = true, visualTransformation = PasswordVisualTransformation())
    Button(onClick = {
        settings.jevKey = jevKey.trim()
        settings.remoToken = remoToken.trim()
        onSaved()
    }) { Text("保存") }
    Text("Remo のトークンは home.nature.global で発行できます")
}

@Composable
private fun ColumnScope.RegisterScreen(recorder: MotionRecorder, settings: Settings, onRegistered: (RegisteredGesture) -> Unit) {
    val scope = rememberCoroutineScope()
    var options by remember { mutableStateOf(listOf(ignoreOption)) }
    var selected by remember { mutableStateOf<ActionOption?>(null) }
    var name by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("家電を読み込んでください") }
    val samples = remember { mutableStateListOf<MotionFeatures>() }

    Text(message)
    OutlinedButton(onClick = {
        message = "読み込み中…"
        scope.launch {
            try {
                val appliances = withContext(Dispatchers.IO) { RemoClient(settings.remoToken).appliances() }
                options = optionsFor(appliances)
                message = if (options.size == 1) "照明・エアコンが見つかりません" else "操作を選んでください"
            } catch (e: Exception) {
                Log.e(TAG, "failed to load appliances", e)
                message = "読み込みに失敗しました: ${e.message}"
            }
        }
    }) { Text("家電を読み込む") }

    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
        options.forEach { option ->
            val isSelected = option == selected
            TextButton(onClick = { selected = option; samples.clear() }) {
                Text((if (isSelected) "● " else "○ ") + option.label)
            }
        }
    }

    val choice = selected ?: return
    OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("ジェスチャー名（例: 右にひねる）") }, singleLine = true)
    Spacer(Modifier.size(4.dp))
    Text("同じ動きを $SAMPLES_PER_GESTURE 回記録します（${samples.size}/$SAMPLES_PER_GESTURE）")
    HoldToRecord(recorder, "押しながら動かす") { recorded ->
        val features = MotionFeatures.from(trimToMotion(recorded))
        if (features == null) {
            message = "動きが短すぎるか長すぎます（0.2〜4 秒）"
            return@HoldToRecord
        }
        samples += features
        message = "${samples.size}/$SAMPLES_PER_GESTURE 回記録しました"
        if (samples.size == SAMPLES_PER_GESTURE) {
            onRegistered(RegisteredGesture(UUID.randomUUID().toString(), name.trim().ifEmpty { choice.label }, choice.action, samples.toList()))
        }
    }
}
