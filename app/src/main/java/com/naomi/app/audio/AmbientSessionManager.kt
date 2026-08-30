package com.naomi.app.audio

import com.naomi.app.ai.speech.SpeechToTextEngine
import com.naomi.app.domain.usecases.AmbientChunkResult
import com.naomi.app.domain.usecases.ProcessAmbientChunkUseCase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AmbientSessionState(
    val isActive: Boolean = false,
    val durationSeconds: Long = 0L,
    val currentTopic: String? = null,
    val processedSegmentsCount: Int = 0,
    val recentNotes: List<String> = emptyList()
)

class AmbientSessionManager(
    private val speechEngine: SpeechToTextEngine,
    private val processAmbientChunkUseCase: ProcessAmbientChunkUseCase
) {
    private var sessionScope: CoroutineScope? = null
    private var timerJob: Job? = null
    private var chunkJob: Job? = null

    private val _sessionState = MutableStateFlow(AmbientSessionState())
    val sessionState: StateFlow<AmbientSessionState> = _sessionState.asStateFlow()

    fun startSession() {
        if (_sessionState.value.isActive) return

        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        sessionScope = scope
        processAmbientChunkUseCase.reset()

        _sessionState.value = AmbientSessionState(
            isActive = true,
            durationSeconds = 0L,
            currentTopic = null,
            processedSegmentsCount = 0,
            recentNotes = emptyList()
        )

        // Timer job
        timerJob = scope.launch {
            while (isActive) {
                delay(1000)
                _sessionState.value = _sessionState.value.copy(
                    durationSeconds = _sessionState.value.durationSeconds + 1
                )
            }
        }

        // Start speech listening loop with automatic chunk processing
        startListeningLoop(scope)
    }

    private fun startListeningLoop(scope: CoroutineScope) {
        speechEngine.startListening()

        chunkJob = scope.launch {
            while (isActive && _sessionState.value.isActive) {
                // Chunk every 30 seconds of ambient audio
                delay(30000)
                if (_sessionState.value.isActive) {
                    speechEngine.stopListening { transcript ->
                        if (!transcript.isBlank) {
                            scope.launch {
                                val result = processAmbientChunkUseCase(transcript)
                                if (result != null) {
                                    val currentList = _sessionState.value.recentNotes.toMutableList()
                                    currentList.add(0, result.note.title)
                                    _sessionState.value = _sessionState.value.copy(
                                        currentTopic = result.currentTopicName,
                                        processedSegmentsCount = _sessionState.value.processedSegmentsCount + 1,
                                        recentNotes = currentList.take(5)
                                    )
                                }
                            }
                        }
                        if (_sessionState.value.isActive) {
                            speechEngine.startListening()
                        }
                    }
                }
            }
        }
    }

    fun stopSession() {
        speechEngine.stopListening { transcript ->
            sessionScope?.launch {
                if (!transcript.isBlank) {
                    processAmbientChunkUseCase(transcript)
                }
            }
        }
        timerJob?.cancel()
        chunkJob?.cancel()
        sessionScope?.cancel()
        sessionScope = null
        _sessionState.value = _sessionState.value.copy(isActive = false)
    }
}
