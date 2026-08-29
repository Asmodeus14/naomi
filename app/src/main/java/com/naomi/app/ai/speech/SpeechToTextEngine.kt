package com.naomi.app.ai.speech

import kotlinx.coroutines.flow.StateFlow

sealed class SpeechState {
    object Idle : SpeechState()
    object Listening : SpeechState()
    object Processing : SpeechState()
    data class Error(val message: String) : SpeechState()
}

interface SpeechToTextEngine {
    val state: StateFlow<SpeechState>
    val partialTranscript: StateFlow<String>
    val audioRms: StateFlow<Float> // 0f to 1f for waveform animation

    fun startListening()
    fun stopListening(onResult: (String) -> Unit)
    fun cancel()
    fun isAvailable(): Boolean
}
