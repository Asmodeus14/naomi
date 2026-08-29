package com.naomi.app.ai.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class AndroidSpeechToTextEngine(
    private val context: Context
) : SpeechToTextEngine {

    private val _state = MutableStateFlow<SpeechState>(SpeechState.Idle)
    override val state: StateFlow<SpeechState> = _state.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    override val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _audioRms = MutableStateFlow(0f)
    override val audioRms: StateFlow<Float> = _audioRms.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var resultCallback: ((String) -> Unit)? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var isCapturing = false

    private val cumulativeTranscript = StringBuilder()
    private var currentPartial = ""

    override fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    override fun startListening() {
        mainHandler.post {
            isCapturing = true
            cumulativeTranscript.clear()
            currentPartial = ""
            _partialTranscript.value = ""
            _audioRms.value = 0.1f
            _state.value = SpeechState.Listening

            initRecognizerIfNeeded()
            startListeningIntent()
        }
    }

    private fun initRecognizerIfNeeded() {
        if (speechRecognizer == null) {
            try {
                val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        if (isCapturing) {
                            _state.value = SpeechState.Listening
                        }
                    }

                    override fun onBeginningOfSpeech() {
                        if (isCapturing) {
                            _state.value = SpeechState.Listening
                        }
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        if (isCapturing) {
                            val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.08f, 1.0f)
                            _audioRms.value = normalized
                        }
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        if (isCapturing) {
                            _audioRms.value = 0.05f
                        }
                    }

                    override fun onError(error: Int) {
                        if (!isCapturing) return

                        when (error) {
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                                isCapturing = false
                                _state.value = SpeechState.Error("Microphone permission denied")
                            }
                            SpeechRecognizer.ERROR_NO_MATCH,
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                            SpeechRecognizer.ERROR_CLIENT -> {
                                if (isCapturing) {
                                    mainHandler.postDelayed({
                                        startListeningIntent()
                                    }, 200)
                                }
                            }
                            else -> {
                                if (isCapturing) {
                                    mainHandler.postDelayed({
                                        startListeningIntent()
                                    }, 350)
                                } else {
                                    _state.value = SpeechState.Error("Recognition error ($error)")
                                }
                            }
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: currentPartial
                        if (text.isNotBlank()) {
                            if (cumulativeTranscript.isNotEmpty()) {
                                cumulativeTranscript.append(" ")
                            }
                            cumulativeTranscript.append(text.trim())
                        }
                        currentPartial = ""
                        _partialTranscript.value = cumulativeTranscript.toString().trim()

                        if (isCapturing) {
                            mainHandler.postDelayed({
                                startListeningIntent()
                            }, 100)
                        } else {
                            finishRecognition()
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        if (!isCapturing) return
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val partial = matches?.firstOrNull() ?: ""
                        if (partial.isNotBlank()) {
                            currentPartial = partial
                            val combined = if (cumulativeTranscript.isNotEmpty()) {
                                "$cumulativeTranscript $currentPartial"
                            } else {
                                currentPartial
                            }
                            _partialTranscript.value = combined.trim()
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                speechRecognizer = recognizer
            } catch (e: Exception) {
                _state.value = SpeechState.Error(e.localizedMessage ?: "Failed to initialize recognizer")
            }
        }
    }

    private fun startListeningIntent() {
        if (!isCapturing) return

        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 30000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
                putExtra("android.speech.extra.DICTATION_MODE", true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }

            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            if (isCapturing) {
                _state.value = SpeechState.Error(e.localizedMessage ?: "Failed to start microphone")
                isCapturing = false
            }
        }
    }

    override fun stopListening(onResult: (String) -> Unit) {
        isCapturing = false
        this.resultCallback = onResult

        mainHandler.post {
            _state.value = SpeechState.Processing
            _audioRms.value = 0f

            try {
                speechRecognizer?.stopListening()
            } catch (_: Exception) {}

            // Give 250ms for any final onResults, otherwise finish with cumulative transcript
            mainHandler.postDelayed({
                finishRecognition()
            }, 250)
        }
    }

    private fun finishRecognition() {
        val callback = resultCallback
        resultCallback = null

        val finalResult = buildString {
            if (cumulativeTranscript.isNotEmpty()) {
                append(cumulativeTranscript.toString().trim())
            }
            if (currentPartial.isNotBlank() && !cumulativeTranscript.contains(currentPartial)) {
                if (isNotEmpty()) append(" ")
                append(currentPartial.trim())
            }
        }.trim()

        _state.value = SpeechState.Idle
        _audioRms.value = 0f

        callback?.invoke(finalResult)

        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (_: Exception) {}
    }

    override fun cancel() {
        isCapturing = false
        resultCallback = null
        cumulativeTranscript.clear()
        currentPartial = ""

        mainHandler.post {
            try {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (_: Exception) {}

            _state.value = SpeechState.Idle
            _partialTranscript.value = ""
            _audioRms.value = 0f
        }
    }
}
