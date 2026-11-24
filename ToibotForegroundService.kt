package com.example.toibot

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import kotlin.concurrent.thread
import kotlin.math.abs

class ToibotForegroundService : Service(), RecognitionListener {

    private var sttModel: Model? = null
    private var speechService: SpeechService? = null
    private var offlineTts: OfflineTts? = null
    private lateinit var commandEngine: ToibotCommandEngine

    private var windowManager: WindowManager? = null
    private var overlayContainer: LinearLayout? = null
    private var orbView: ToibotOrbView? = null
    private var subtitleText: TextView? = null

    @Volatile
    private var isSpeakingNow = false

    override fun onCreate() {
        super.onCreate()
        commandEngine = ToibotCommandEngine(this)
        startForegroundNotification()
        setupFloatingMiniOrb()
        initModels()
    }

    private fun startForegroundNotification() {
        val channelId = "toibot_always_on"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "T.O.I.B.O.T Always-On Listener",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("T.O.I.B.O.T faol")
            .setContentText("Doimiy jim eshitish rejimi ishlamoqda")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()

        startForeground(101, notification)
    }

    private fun setupFloatingMiniOrb() {
        if (!Settings.canDrawOverlays(this)) return

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#CC050B0E"))
            setPadding(24, 14, 28, 14)
        }

        orbView = ToibotOrbView(this).apply {
            layoutParams = LinearLayout.LayoutParams(120, 120)
        }

        subtitleText = TextView(this).apply {
            text = "T.O.I.B.O.T tayyor"
            setTextColor(Color.parseColor("#E0F2FE"))
            textSize = 14f
            maxLines = 2
            setPadding(20, 0, 0, 0)
        }

        overlayContainer?.addView(orbView)
        overlayContainer?.addView(subtitleText)

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 48
        }

        windowManager?.addView(overlayContainer, params)
    }

    private fun initModels() {
        thread {
            StorageService.unpack(this, "tts_model", "tts_model",
                { unpackedTts ->
                    try {
                        val ttsDir = unpackedTts.absolutePath
                        val vitsConfig = OfflineTtsVitsModelConfig(
                            model = "$ttsDir/model.onnx",
                            tokens = "$ttsDir/tokens.txt",
                            dataDir = "$ttsDir/espeak-ng-data"
                        )
                        val modelConfig = OfflineTtsModelConfig(
                            vits = vitsConfig,
                            numThreads = 2,
                            debug = false
                        )
                        offlineTts = OfflineTts(config = OfflineTtsConfig(model = modelConfig))
                    } catch (_: Exception) {
                    }
                },
                { }
            )

            StorageService.unpack(this, "stt_model", "stt_model",
                { unpackedStt ->
                    sttModel = unpackedStt
                    startContinuousListening()
                },
                { e ->
                    updateOverlay(ToibotOrbView.OrbState.IDLE, "STT model topilmadi: ${e.message}", 0f)
                }
            )
        }
    }

    private fun startContinuousListening() {
        val model = sttModel ?: return
        speechService?.stop()
        val recognizer = Recognizer(model, 16000.0f)
        speechService = SpeechService(recognizer, 16000.0f)
        speechService?.startListening(this)
        updateOverlay(ToibotOrbView.OrbState.LISTENING, "Eshitmoqdaman...", 0.1f)
    }

    override fun onPartialResult(hypothesis: String?) {
        if (isSpeakingNow) return
        val partial = JSONObject(hypothesis ?: "{}").optString("partial", "")
        if (partial.isNotBlank()) {
            updateOverlay(ToibotOrbView.OrbState.LISTENING, partial, 0.55f)
        }
    }

    override fun onResult(hypothesis: String?) {
        if (isSpeakingNow) return
        val text = JSONObject(hypothesis ?: "{}").optString("text", "")
        if (text.isNotBlank()) {
            updateOverlay(ToibotOrbView.OrbState.LISTENING, text, 0.7f)
            thread {
                commandEngine.processCommand(
                    rawSpeech = text,
                    onSpeak = { reply -> speakIndependently(reply, confirming = false) },
                    onConfirmationRequested = { prompt -> speakIndependently(prompt, confirming = true) }
                )
            }
        }
    }

    override fun onFinalResult(hypothesis: String?) {}
    override fun onError(exception: Exception?) {}
    override fun onTimeout() {}

    private fun speakIndependently(text: String, confirming: Boolean) {
        isSpeakingNow = true
        speechService?.setPause(true)
        val targetState = if (confirming) ToibotOrbView.OrbState.CONFIRMING else ToibotOrbView.OrbState.SPEAKING
        updateOverlay(targetState, text, 0.8f)

        val ttsEngine = offlineTts
        if (ttsEngine != null) {
            val audio = ttsEngine.generate(text = text, sid = 0, speed = 0.92f)
            playPcmWithOrbSync(audio.samples, audio.sampleRate, targetState, text)
        } else {
            Thread.sleep(1500)
        }

        isSpeakingNow = false
        speechService?.setPause(false)
        updateOverlay(
            if (confirming) ToibotOrbView.OrbState.CONFIRMING else ToibotOrbView.OrbState.LISTENING,
            if (confirming) "Tasdiqlash kutilmoqda: Ha / Yo'q" else "Eshitmoqdaman...",
            0.1f
        )
    }

    private fun playPcmWithOrbSync(
        samples: FloatArray,
        sampleRate: Int,
        state: ToibotOrbView.OrbState,
        caption: String
    ) {
        if (samples.isEmpty()) return
        val pcm16 = ShortArray(samples.size) { i ->
            (samples[i].coerceIn(-1.0f, 1.0f) * Short.MAX_VALUE).toInt().toShort()
        }
        val minBufSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBufSize, pcm16.size * 2))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack.write(pcm16, 0, pcm16.size)
        audioTrack.play()

        val durationMs = (samples.size * 1000L) / sampleRate
        val stepMs = 60L
        var elapsed = 0L
        while (elapsed < durationMs) {
            val idx = ((elapsed * samples.size) / durationMs).toInt().coerceIn(0, samples.size - 1)
            val amp = abs(samples[idx]) * 2.5f
            updateOverlay(state, caption, amp)
            Thread.sleep(stepMs)
            elapsed += stepMs
        }
        audioTrack.stop()
        audioTrack.release()
    }

    private fun updateOverlay(state: ToibotOrbView.OrbState, caption: String, amp: Float) {
        overlayContainer?.post {
            orbView?.state = state
            orbView?.audioAmplitude = amp
            subtitleText?.text = caption
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        speechService?.stop()
        offlineTts?.release()
        if (overlayContainer != null) {
            windowManager?.removeView(overlayContainer)
        }
    }
}
