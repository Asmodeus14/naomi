package com.naomi.app.ai.speech

import kotlinx.coroutines.flow.StateFlow

sealed class SpeechState {
    object Idle : SpeechState()
    object Listening : SpeechState()
    object Processing : SpeechState()
    data class Error(val message: String) : SpeechState()
}

/**
 * One recognition session's worth of speech, with the runner-up hypotheses the
 * recogniser also considered.
 *
 * [alternatives] is the reason this type exists. Android's recogniser is asked
 * for several hypotheses and, until now, every one but the first was discarded.
 * That is exactly where the fix for a misheard proper noun lives: when someone
 * says "Nyx" and the recogniser commits to "next", the correct word is very
 * often sitting in hypothesis two. Throwing it away meant reconstructing from
 * spelling alone what the recogniser had already worked out from sound.
 *
 * @param chosen       the hypothesis the recogniser ranked first
 * @param alternatives lower-ranked hypotheses of the same audio, best first
 */
data class Utterance(
    val chosen: String,
    val alternatives: List<String> = emptyList()
)

/**
 * The complete result of a capture.
 *
 * [text] is what every existing caller wants and is the concatenation of each
 * utterance's chosen hypothesis. [utterances] is kept alongside it because a
 * capture is not one recognition — the engine restarts after each pause, so a
 * minute of speech is a dozen sessions, and the alternatives only line up with
 * the words if they stay grouped by the session that produced them.
 */
data class Transcript(
    val text: String,
    val utterances: List<Utterance> = emptyList()
) {
    val isBlank: Boolean get() = text.isBlank()

    companion object {
        val EMPTY = Transcript("", emptyList())

        /** For callers that have text but no recogniser behind it — typed input, shared text. */
        fun of(text: String): Transcript = Transcript(text, listOf(Utterance(text)))
    }
}

interface SpeechToTextEngine {
    val state: StateFlow<SpeechState>
    val partialTranscript: StateFlow<String>
    val audioRms: StateFlow<Float> // 0f to 1f for waveform animation

    fun startListening()
    fun stopListening(onResult: (Transcript) -> Unit)
    fun cancel()
    fun isAvailable(): Boolean
}
