package com.naomi.app.domain.model

import com.naomi.app.data.database.entities.NoteEntity

/**
 * Where a thought currently is in the pipeline.
 *
 * Each value is emitted at the moment the corresponding work begins, so the UI
 * reports what is genuinely happening. Transcription is not represented here
 * because it finishes in the speech engine before this pipeline is handed the
 * text — the capture screen owns that part of the story.
 */
sealed interface ProcessingStage {

    /** Reading the transcript: keyphrases, tasks, ideas, named things. */
    data object Understanding : ProcessingStage

    /** Deciding where the memory belongs and writing it down. */
    data object Organizing : ProcessingStage

    /** Finished. [notes] is one entry per distinct thought in the recording. */
    data class Done(val notes: List<NoteEntity>) : ProcessingStage

    /**
     * Something went wrong. [reason] is phrased for a person, not a logfile —
     * the underlying [cause] is kept for logging but never shown.
     */
    data class Failed(
        val reason: FailureReason,
        val cause: Throwable? = null
    ) : ProcessingStage
}

/**
 * The failures a user can actually be told something useful about.
 */
enum class FailureReason(val message: String, val recovery: String) {
    NOTHING_HEARD(
        message = "Didn't catch that.",
        recovery = "Try again, a little closer to the mic."
    ),
    COULD_NOT_UNDERSTAND(
        message = "Couldn't make sense of that.",
        recovery = "Try saying it a different way."
    ),
    COULD_NOT_SAVE(
        message = "Couldn't remember this.",
        recovery = "Your words are safe — try again."
    );
}
