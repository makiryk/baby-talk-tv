package com.babytalk.tv.audio

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.babytalk.tv.BabyTalkApplication
import com.babytalk.tv.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * マイク常時音声キャプチャ用フォアグラウンドサービス。
 * - H6対応: Android 14+ FGS権限、マイク非搭載/権限拒否ハンドリング
 */
class AudioRecordService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var recordingJob: Job? = null

    private var audioRecord: AudioRecord? = null
    var vadDetector: VadDetector? = null

    // リスナー
    var onVoiceEventListener: ((VoiceEvent) -> Unit)? = null
    var onErrorListener: ((String) -> Unit)? = null

    inner class LocalBinder : Binder() {
        fun getService(): AudioRecordService = this@AudioRecordService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundServiceNotification()
        startRecording()
        return START_STICKY
    }

    private fun startForegroundServiceNotification() {
        val notification: Notification = NotificationCompat.Builder(this, BabyTalkApplication.CHANNEL_ID_SERVICE)
            .setContentTitle(getString(R.string.mic_notification_title))
            .setContentText(getString(R.string.mic_notification_text))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1001, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(1001, notification)
        }
    }

    fun startRecording() {
        if (recordingJob?.isActive == true) return

        // 録音権限チェック
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            onErrorListener?.invoke("RECORD_AUDIO permission not granted")
            return
        }

        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat).coerceAtLeast(2048)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                onErrorListener?.invoke("AudioRecord initialization failed (Device may lack mic)")
                return
            }

            audioRecord?.startRecording()

            vadDetector = VadDetector(sampleRate = sampleRate) { event ->
                onVoiceEventListener?.invoke(event)
            }

            recordingJob = serviceScope.launch {
                val buffer = ShortArray(1024)
                while (isActive) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (read > 0) {
                        vadDetector?.processFrame(buffer, read)
                    }
                }
            }
        } catch (e: SecurityException) {
            onErrorListener?.invoke("SecurityException: ${e.message}")
        } catch (e: Exception) {
            onErrorListener?.invoke("Recording start error: ${e.message}")
        }
    }

    fun stopRecording() {
        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            // ignore
        } finally {
            audioRecord = null
        }
    }

    fun setMuted(muted: Boolean) {
        vadDetector?.isMuted = muted
    }

    override fun onDestroy() {
        stopRecording()
        super.onDestroy()
    }
}
