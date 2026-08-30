package com.naomi.app.presentation.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.naomi.app.NaomiApp
import com.naomi.app.ai.intelligence.SharedTextParser
import com.naomi.app.ai.speech.SpeechState
import com.naomi.app.ai.speech.Transcript
import com.naomi.app.calendar.CalendarHandoff
import com.naomi.app.data.database.entities.MemoryEntryEntity
import com.naomi.app.data.database.entities.NoteEntity
import com.naomi.app.data.database.entities.TaskEntity
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

    /**
     * Fires when a memory just produced a dated reminder.
     *
     * POST_NOTIFICATIONS was declared but never requested, so on Android 13+ it
     * sat denied and every reminder was dropped silently — the feature shipped
     * looking present and doing nothing. The ask happens here rather than at
     * launch because this is the one moment it can be justified: the user has
     * just asked out loud to be reminded of something.
     */
    private val _reminderScheduled = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val reminderScheduled: SharedFlow<Unit> = _reminderScheduled.asSharedFlow()

    /**
     * Fires when a reminder was set for a time the user actually spoke, and the
     * system will currently deliver it only approximately.
     *
     * The same reasoning as above, one step further: apps targeting SDK 34+ are
     * denied `SCHEDULE_EXACT_ALARM` by default, so "remind me at 6 PM" would
     * arrive any time before seven. Asked for at the first moment it means
     * something and never at launch. Declining leaves an inexact alarm, which is
     * the behaviour Naomi had for its entire life until now.
     */
    private val _exactAlarmNeeded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val exactAlarmNeeded: SharedFlow<Unit> = _exactAlarmNeeded.asSharedFlow()

    /**
     * Fires with an occasion Naomi understood well enough to offer to the
     * calendar.
     *
     * Emitted to the screen rather than launched from here, because the handoff
     * starts another app's activity and Android 10+ blocks that from the
     * background. A view model has no way to know whether it is being observed.
     */
    private val _calendarHandoff = MutableSharedFlow<TaskEntity>(extraBufferCapacity = 1)
    val calendarHandoff: SharedFlow<TaskEntity> = _calendarHandoff.asSharedFlow()

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

    /**
     * Text arriving from another app's Share Sheet.
     *
     * Handled by exactly the same pipeline as speech — a shared paragraph is
     * still a thought the user wants kept, and giving it a separate path would
     * mean a second place for filing to go wrong. Only the recorded source
     * differs, so a link can be told apart from something said out loud.
     */
    fun processSharedText(subject: String, body: String) {
        val shared = SharedTextParser.parse(subject, body)
        if (shared.text.isBlank()) return
        processThought(
            transcript = Transcript.of(shared.text),
            source = MemoryEntryEntity.SOURCE_SHARED,
            sourceUrl = shared.url
        )
    }

    fun processThought(
        transcript: Transcript,
        source: String = MemoryEntryEntity.SOURCE_SPOKEN,
        sourceUrl: String? = null
    ) {
        processingJob?.cancel()
        processingJob = viewModelScope.launch {
            processThoughtUseCase(transcript, source, sourceUrl)
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
                    // A deadline heard in what was just said is worthless until
                    // it is on the system clock. Reconciling the whole set is
                    // cheap and cannot leave a task half-scheduled.
                    viewModelScope.launch {
                        try {
                            val result = app.syncRemindersUseCase()
                            if (result.scheduled > 0) _reminderScheduled.emit(Unit)
                            if (result.wantsExactPermission) _exactAlarmNeeded.emit(Unit)
                        } catch (e: Exception) {
                            Log.e(TAG, "Could not schedule reminders", e)
                        }
                        offerToCalendar(note)
                    }
                }
            }

            is ProcessingStage.Failed -> failWith(stage.reason)
        }
    }

    /**
     * Offers an occasion to the user's calendar, once.
     *
     * Only for a memory that produced an [TaskEntity.KIND_EVENT] row, which
     * [com.naomi.app.ai.intelligence.ActionClassifier] only creates when someone
     * named an occasion *and* a clock time and did not hedge it. Anything short
     * of that stays a memory and no calendar screen appears — the alternative is
     * an app that opens Google Calendar because you mentioned lunch.
     */
    private suspend fun offerToCalendar(note: NoteEntity) {
        try {
            val event = knowledgeRepository.getTasksForNote(note.id)
                .firstOrNull { it.kind == TaskEntity.KIND_EVENT && it.calendarAddedAt == null }
                ?: return
            if (!CalendarHandoff.isAvailable(app, event)) return

            // Marked before the screen opens, not after. Naomi has no calendar
            // permission and cannot see whether the event was saved, so the only
            // honest thing this records is that it offered — and offering twice
            // for one sentence would be worse than not offering again.
            knowledgeRepository.markCalendarOffered(event.id)
            _calendarHandoff.emit(event)
        } catch (e: Exception) {
            Log.w(TAG, "Could not offer the event to a calendar", e)
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
