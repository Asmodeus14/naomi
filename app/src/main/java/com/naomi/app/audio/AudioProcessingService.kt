package com.naomi.app.audio

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.naomi.app.NaomiApp
import com.naomi.app.R
import com.naomi.app.domain.model.FailureReason
import com.naomi.app.domain.model.ProcessingStage
import com.naomi.app.widget.NaomiWidgetProvider
import com.naomi.app.widget.WidgetState
import kotlinx.coroutines.*

class AudioProcessingService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isRunning = false

    companion object {
        const val ACTION_START_CAPTURE = "com.naomi.app.action.START_CAPTURE"
        const val ACTION_STOP_CAPTURE = "com.naomi.app.action.STOP_CAPTURE"
        const val ACTION_START_AMBIENT = "com.naomi.app.action.START_AMBIENT"
        const val ACTION_STOP_AMBIENT = "com.naomi.app.action.STOP_AMBIENT"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "naomi_capture_channel"
        private const val TAG = "AudioProcessingService"

        fun startCapture(context: Context) {
            val intent = Intent(context, AudioProcessingService::class.java).apply {
                action = ACTION_START_CAPTURE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopCapture(context: Context) {
            val intent = Intent(context, AudioProcessingService::class.java).apply {
                action = ACTION_STOP_CAPTURE
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private var timerJob: Job? = null

    private fun triggerHaptic(type: Int) {
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = when (type) {
                    1 -> android.os.VibrationEffect.createOneShot(25, android.os.VibrationEffect.DEFAULT_AMPLITUDE)
                    2 -> android.os.VibrationEffect.createOneShot(35, android.os.VibrationEffect.DEFAULT_AMPLITUDE)
                    else -> android.os.VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE)
                }
                vibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(30)
            }
        } catch (e: Exception) {
            // Haptics optional
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_CAPTURE -> {
                startForegroundNotification("Listening...", "Naomi is capturing your thought")
                triggerHaptic(1)
                timerJob?.cancel()
                timerJob = serviceScope.launch {
                    var seconds = 0
                    while (isActive) {
                        val formatted = String.format(java.util.Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60)
                        NaomiWidgetProvider.updateWidgetState(
                            this@AudioProcessingService,
                            WidgetState.LISTENING,
                            "Listening",
                            formatted
                        )
                        delay(1000)
                        seconds++
                    }
                }
                NaomiApp.instance.speechEngine.startListening()
            }
            ACTION_STOP_CAPTURE -> {
                triggerHaptic(2)
                timerJob?.cancel()
                NaomiWidgetProvider.updateWidgetState(
                    this,
                    WidgetState.PROCESSING,
                    "Remembering...",
                    "Organizing knowledge"
                )
                NaomiApp.instance.speechEngine.stopListening { transcript ->
                    serviceScope.launch {
                        if (!transcript.isBlank) {
                            // Collect the pipeline's terminal stage. Anything the
                            // use case doesn't already convert into a Failed stage
                            // is caught here, so the widget never sticks on
                            // "Remembering..." with nothing coming.
                            var outcome: ProcessingStage? = null
                            try {
                                NaomiApp.instance.processThoughtUseCase(transcript)
                                    .collect { stage ->
                                        if (stage is ProcessingStage.Done || stage is ProcessingStage.Failed) {
                                            outcome = stage
                                        }
                                    }
                            } catch (e: Exception) {
                                Log.e(TAG, "Processing failed", e)
                                outcome = ProcessingStage.Failed(FailureReason.COULD_NOT_SAVE, e)
                            }

                            when (val result = outcome) {
                                is ProcessingStage.Done -> {
                                    triggerHaptic(3)
                                    NaomiWidgetProvider.updateWidgetState(
                                        this@AudioProcessingService,
                                        WidgetState.SUCCESS,
                                        "Remembered",
                                        result.notes.firstOrNull()?.title
                                    )
                                }
                                is ProcessingStage.Failed -> NaomiWidgetProvider.updateWidgetState(
                                    this@AudioProcessingService,
                                    WidgetState.ERROR,
                                    result.reason.message,
                                    result.reason.recovery
                                )
                                else -> NaomiWidgetProvider.updateWidgetState(
                                    this@AudioProcessingService,
                                    WidgetState.ERROR,
                                    FailureReason.COULD_NOT_SAVE.message,
                                    FailureReason.COULD_NOT_SAVE.recovery
                                )
                            }
                        } else {
                            NaomiWidgetProvider.updateWidgetState(
                                this@AudioProcessingService,
                                WidgetState.ERROR,
                                FailureReason.NOTHING_HEARD.message,
                                FailureReason.NOTHING_HEARD.recovery
                            )
                        }
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
            ACTION_START_AMBIENT -> {
                startForegroundNotification("Ambient Mode Active", "Naomi is organizing ongoing thoughts")
                NaomiApp.instance.ambientSessionManager.startSession()
            }
            ACTION_STOP_AMBIENT -> {
                NaomiApp.instance.ambientSessionManager.stopSession()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundNotification(title: String, text: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            // A launcher icon here is drawn as a silhouette of its alpha channel,
            // which for a full-bleed icon is a grey square.
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Naomi Audio Processing",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows recording and ambient status"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
