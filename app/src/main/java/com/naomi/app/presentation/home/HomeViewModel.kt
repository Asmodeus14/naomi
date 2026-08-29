package com.naomi.app.presentation.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.naomi.app.NaomiApp
import com.naomi.app.ai.speech.SpeechState
import com.naomi.app.data.database.entities.NoteEntity
import com.naomi.app.data.database.entities.TopicEntity
import com.naomi.app.domain.model.FailureReason
import com.naomi.app.domain.model.ProcessingStage
import com.naomi.app.widget.NaomiWidgetProvider
import com.naomi.app.widget.WidgetState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as NaomiApp
    private val knowledgeRepository = app.knowledgeRepository
    private val speechEngine = app.speechEngine
    private val processThoughtUseCase = app.processThoughtUseCase

    val recentNotes: StateFlow<List<NoteEntity>> = knowledgeRepository
        .getRecentNotesFlow(15)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rootTopics: StateFlow<List<TopicEntity>> = knowledgeRepository
        .getRootTopicsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _captureState = MutableStateFlow<CaptureUiState>(CaptureUiState.Idle)
    val captureState: StateFlow<CaptureUiState> = _captureState.asStateFlow()

    val audioRms: StateFlow<Float> = speechEngine.audioRms

    private var timerJob: Job? = null
    private var processingJob: Job? = null
    private var elapsedSeconds = 0L

    init {
        // Keep the live transcript flowing into the listening state.
        viewModelScope.launch {
            speechEngine.partialTranscript.collect { partial ->
                val current = _captureState.value
                if (current is CaptureUiState.Listening) {
                    _captureState.value = current.copy(partialTranscript = partial)
                }
            }
        }

        viewModelScope.launch {
            speechEngine.state.collect { state ->
                when (state) {
                    is SpeechState.Processing -> {
                        // The recogniser is still resolving audio into words.
                        if (_captureState.value !is CaptureUiState.Processing) {
                            _captureState.value =
                                CaptureUiState.Processing(CaptureUiState.Processing.Step.TRANSCRIBING)
                        }
                    }
                    // A speech failure used to be swallowed silently, leaving the
                    // user staring at a sheet that simply vanished after they had
                    // spoken. Say what happened.
                    is SpeechState.Error -> failWith(FailureReason.NOTHING_HEARD)
                    else -> Unit
                }
            }
        }
    }

    fun startCapture() {
        if (_captureState.value.isActive) return

        elapsedSeconds = 0L
        _captureState.value = CaptureUiState.Listening(0L, "")
        NaomiWidgetProvider.updateWidgetState(app, WidgetState.LISTENING)
        speechEngine.startListening()

        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                elapsedSeconds += 1
                val current = _captureState.value
                if (current !is CaptureUiState.Listening) break
                _captureState.value = current.copy(elapsedSeconds = elapsedSeconds)
            }
        }
    }

    fun stopCapture() {
        if (_captureState.value !is CaptureUiState.Listening) return
        timerJob?.cancel()
        _captureState.value = CaptureUiState.Processing(CaptureUiState.Processing.Step.TRANSCRIBING)
        NaomiWidgetProvider.updateWidgetState(app, WidgetState.PROCESSING)

        speechEngine.stopListening { transcript -> processThought(transcript) }
    }

    fun cancelCapture() {
        timerJob?.cancel()
        processingJob?.cancel()
        speechEngine.cancel()
        _captureState.value = CaptureUiState.Idle
        NaomiWidgetProvider.updateWidgetState(app, WidgetState.IDLE)
    }

    /** Dismisses a terminal state (saved or failed) back to idle. */
    fun acknowledge() {
        if (_captureState.value.isActive) return
        _captureState.value = CaptureUiState.Idle
        NaomiWidgetProvider.updateWidgetState(app, WidgetState.IDLE)
    }

    fun processThought(transcript: String) {
        processingJob?.cancel()
        processingJob = viewModelScope.launch {
            processThoughtUseCase(transcript)
                // Any exception that escapes the use case still has to leave the
                // UI somewhere the user can act from, never pinned on a spinner.
                .catch { e ->
                    Log.e(TAG, "Processing pipeline failed", e)
                    failWith(FailureReason.COULD_NOT_SAVE)
                }
                .collect { stage -> onStage(stage) }
        }
    }

    private fun onStage(stage: ProcessingStage) {
        when (stage) {
            is ProcessingStage.Understanding ->
                _captureState.value =
                    CaptureUiState.Processing(CaptureUiState.Processing.Step.UNDERSTANDING)

            is ProcessingStage.Organizing ->
                _captureState.value =
                    CaptureUiState.Processing(CaptureUiState.Processing.Step.ORGANIZING)

            is ProcessingStage.Done -> {
                val note = stage.notes.firstOrNull()
                if (note == null) {
                    failWith(FailureReason.NOTHING_HEARD)
                } else {
                    _captureState.value = CaptureUiState.Saved(note)
                    NaomiWidgetProvider.updateWidgetState(
                        app, WidgetState.SUCCESS, "Remembered", note.title
                    )
                }
            }

            is ProcessingStage.Failed -> failWith(stage.reason)
        }
    }

    private fun failWith(reason: FailureReason) {
        timerJob?.cancel()
        _captureState.value = CaptureUiState.Failed(reason)
        NaomiWidgetProvider.updateWidgetState(app, WidgetState.ERROR, reason.message)
    }

    private companion object {
        const val TAG = "HomeViewModel"
    }
}
