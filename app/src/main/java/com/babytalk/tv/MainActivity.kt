package com.babytalk.tv

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.babytalk.tv.audio.AudioRecordService
import com.babytalk.tv.data.PhraseRepository
import com.babytalk.tv.orchestrator.ConversationOrchestrator
import com.babytalk.tv.orchestrator.ConversationState
import com.babytalk.tv.power.PowerManagerHelper
import com.babytalk.tv.tts.TtsManager
import kotlinx.coroutines.delay
import kotlin.random.Random

class MainActivity : ComponentActivity() {

    private lateinit var phraseRepository: PhraseRepository
    private lateinit var ttsManager: TtsManager
    private lateinit var orchestrator: ConversationOrchestrator

    private var audioService: AudioRecordService? = null
    private var isServiceBound = false

    private val timerOptions = intArrayOf(5, 10, 15, 30, 60, 120)
    private var selectedTimerIndex = mutableIntStateOf(1) // 10分がデフォルト
    private var showTimerUi = mutableStateOf(true)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as AudioRecordService.LocalBinder
            audioService = binder.getService()
            isServiceBound = true

            // 音声検知イベントをオーケストレーターへ渡す
            audioService?.onVoiceEventListener = { event ->
                orchestrator.onVoiceDetected(event)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            audioService = null
            isServiceBound = false
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            bindAudioService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 全面黒・画面輝度最小化 (0.05f) ＆ 常時点灯設定
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val lp = window.attributes
        lp.screenBrightness = 0.05f
        window.attributes = lp

        // リポジトリ・TTS・オーケストレーター初期化
        phraseRepository = PhraseRepository(this)
        ttsManager = TtsManager(
            context = this,
            onUtteranceStart = {},
            onUtteranceComplete = { orchestrator.onUtteranceCompleted() }
        )

        orchestrator = ConversationOrchestrator(
            phraseRepository = phraseRepository,
            ttsManager = ttsManager,
            onMuteAudioInput = { muted -> audioService?.setMuted(muted) },
            onSessionFinished = {
                PowerManagerHelper.turnOffTvOrDim(this)
            }
        )

        setContent {
            val state by orchestrator.state.collectAsState()
            val currentTimerMinutes = timerOptions[selectedTimerIndex.intValue]
            val isTimerVisible by showTimerUi

            BabyTalkTvScreen(
                state = state,
                timerMinutes = currentTimerMinutes,
                isTimerVisible = isTimerVisible
            )
        }

        checkPermissionAndStart()
        orchestrator.startSession(timerOptions[selectedTimerIndex.intValue])
    }

    private fun checkPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            bindAudioService()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun bindAudioService() {
        val intent = Intent(this, AudioRecordService::class.java)
        startService(intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // リモコンキー操作（上下キーでタイマー切り替え、長押し検知）
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                showTimerUi.value = true
                if (selectedTimerIndex.intValue < timerOptions.size - 1) {
                    selectedTimerIndex.intValue++
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                showTimerUi.value = true
                if (selectedTimerIndex.intValue > 0) {
                    selectedTimerIndex.intValue--
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                showTimerUi.value = false
                orchestrator.startSession(timerOptions[selectedTimerIndex.intValue])
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
        orchestrator.stopSession()
        ttsManager.shutdown()

        // 画面輝度と常時点灯フラグをOSデフォルト値に明示的に復帰
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val lp = window.attributes
        lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = lp

        super.onDestroy()
    }
}

@Composable
fun BabyTalkTvScreen(
    state: ConversationState,
    timerMinutes: Int,
    isTimerVisible: Boolean
) {
    // ピクセルシフト用オフセット (60秒ごとに微動)
    var pixelShiftOffset by remember { mutableStateOf(IntOffset(0, 0)) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            pixelShiftOffset = IntOffset(
                Random.nextInt(-16, 16),
                Random.nextInt(-16, 16)
            )
        }
    }

    // タイマーUI自動フェードアウト (5秒後)
    var timerAlphaVisible by remember { mutableStateOf(true) }
    LaunchedEffect(isTimerVisible, timerMinutes) {
        if (isTimerVisible) {
            timerAlphaVisible = true
            delay(5000L)
            timerAlphaVisible = false
        }
    }

    // 呼吸パルスアニメーション
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    // インジケーターの色とアルファの決定
    val (indicatorColor, indicatorAlpha) = when (state) {
        ConversationState.IDLE -> Color(0xFF555555) to pulseAlpha
        ConversationState.LISTENING -> Color(0xFF4A90E2) to 0.85f
        ConversationState.SPEAKING, ConversationState.CLOSING -> Color(0xFF7ED321) to 0.85f
        ConversationState.STANDBY -> Color.Transparent to 0.0f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 控えめなタイマー設定UI (起動時またはキー操作時のみ表示)
        AnimatedVisibility(
            visible = timerAlphaVisible,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(800)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Text(
                text = "タイマー: ${timerMinutes}分",
                color = Color(0xFF666666),
                fontSize = 24.sp
            )
        }

        // 極小動作インジケーター (画面右下端、ピクセルシフト付き)
        if (state != ConversationState.STANDBY) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(32.dp)
                    .offset { pixelShiftOffset }
                    .size(14.dp)
                    .clip(CircleShape)
                    .alpha(indicatorAlpha)
                    .background(indicatorColor)
            )
        }
    }
}
