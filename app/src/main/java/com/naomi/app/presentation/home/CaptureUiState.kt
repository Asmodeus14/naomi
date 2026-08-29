package com.naomi.app.presentation.home

import com.naomi.app.data.database.entities.NoteEntity
import com.naomi.app.domain.model.FailureReason

/**
 * The capture flow as a single value.
 *
 * Modelling this as one state rather than a handful of independent booleans
 * removes the combinations that shouldn't exist — listening and processing at
 * the same time, or a failure that leaves a spinner running behind it.
 */
sealed interface CaptureUiState {

    data object Idle : CaptureUiState

    data class Listening(
        val elapsedSeconds: Long,
        val partialTranscript: String
    ) : CaptureUiState

    /**
     * @param step which pipeline stage is running right now
     */
    data class Processing(val step: Step) : CaptureUiState {
        enum class Step { TRANSCRIBING, UNDERSTANDING, ORGANIZING }
    }

    data class Saved(val note: NoteEntity) : CaptureUiState

    data class Failed(val reason: FailureReason) : CaptureUiState

    val isActive: Boolean
        get() = this is Listening || this is Processing
}
