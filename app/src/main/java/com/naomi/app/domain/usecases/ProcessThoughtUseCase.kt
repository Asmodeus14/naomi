package com.naomi.app.domain.usecases

import android.util.Log
import com.naomi.app.ai.intelligence.CorrectionDetector
import com.naomi.app.ai.intelligence.LocalIntelligenceEngine
import com.naomi.app.ai.intelligence.TranscriptNormalizer
import com.naomi.app.ai.speech.Transcript
import com.naomi.app.data.database.entities.MemoryEntryEntity
import com.naomi.app.data.database.entities.NoteEntity
import com.naomi.app.domain.intelligence.IntelligenceProvider
import com.naomi.app.domain.model.ExtractedKnowledge
import com.naomi.app.domain.model.FailureReason
import com.naomi.app.domain.model.ProcessingStage
import com.naomi.app.domain.repository.KnowledgeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Turns spoken words into organised memories.
 *
 * A [Flow] rather than a plain suspend call so the UI can report real progress.
 * Every stage corresponds to work that is actually running — nothing is
 * announced before it starts or held open after it finishes.
 */
class ProcessThoughtUseCase(
    private val knowledgeRepository: KnowledgeRepository,
    /**
     * Tried in order. The first one that is available and returns a usable
     * result wins; the last entry must always succeed, since a memory is never
     * worth losing to a model being unavailable.
     */
    private val providers: List<IntelligenceProvider>
) {

    /**
     * @param source    where the words came from — spoken, typed, or shared in
     *                  from another app. Stored on each memory entry so shared
     *                  web content stays distinguishable from the user's own
     *                  words later.
     * @param sourceUrl the link the text arrived with, when there was one.
     */
    operator fun invoke(
        transcript: Transcript,
        source: String = MemoryEntryEntity.SOURCE_SPOKEN,
        sourceUrl: String? = null
    ): Flow<ProcessingStage> = flow {
        val rawTranscript = transcript.text
        val clean = rawTranscript.trim()
        if (clean.isBlank()) {
            emit(ProcessingStage.Failed(FailureReason.NOTHING_HEARD))
            return@flow
        }

        emit(ProcessingStage.Understanding)

        val knownTopics = try {
            knowledgeRepository.getTopicPaths()
        } catch (e: Exception) {
            Log.w(TAG, "Could not load topic tree; placing without it", e)
            emptyList()
        }

        // Repair proper nouns before anything reads the words. Everything
        // downstream — the title, the topic it files under, the search index —
        // inherits whatever the recogniser produced, so this is the only place
        // a fix is worth making. `rawTranscript` below is untouched, so the
        // original survives even when a correction turns out to be wrong.
        val corrected = correctProperNouns(clean, transcript, source, knownTopics)

        // One recording can hold several unrelated thoughts; each becomes its
        // own memory so they file in different places.
        val segments = LocalIntelligenceEngine.segmentSpeech(corrected)
        if (segments.isEmpty()) {
            emit(ProcessingStage.Failed(FailureReason.NOTHING_HEARD))
            return@flow
        }

        val analysed = try {
            segments.map { segment -> segment to analyse(segment, knownTopics) }
        } catch (e: Exception) {
            Log.e(TAG, "Extraction failed", e)
            emit(ProcessingStage.Failed(FailureReason.COULD_NOT_UNDERSTAND, e))
            return@flow
        }

        emit(ProcessingStage.Organizing)

        val saved = mutableListOf<NoteEntity>()
        try {
            for ((segment, knowledge) in analysed) {
                saved += knowledgeRepository.saveNote(
                    knowledge = knowledge,
                    rawTranscript = rawTranscript,
                    cleanTranscript = segment,
                    source = source,
                    sourceUrl = sourceUrl
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Save failed", e)
            emit(ProcessingStage.Failed(FailureReason.COULD_NOT_SAVE, e))
            return@flow
        }

        emit(ProcessingStage.Done(saved))
    }

    /**
     * For text that never went through a recogniser — typed input, or a
     * paragraph shared in from another app. There are no rival hypotheses to
     * weigh, because nothing was ever uncertain about the spelling.
     */
    operator fun invoke(
        rawTranscript: String,
        source: String = MemoryEntryEntity.SOURCE_SPOKEN,
        sourceUrl: String? = null
    ): Flow<ProcessingStage> = invoke(Transcript.of(rawTranscript), source, sourceUrl)

    /**
     * Puts the user's own words back where the recogniser guessed at them.
     *
     * Only for speech, because only speech was ever uncertain. Typed text is
     * exactly what the user meant — someone who types "next" has said so — and
     * shared text is someone else's words entirely, where rewriting a quote
     * against this user's private vocabulary would be fabrication, not repair.
     *
     * Runs against the device's own database and nothing else. A failure here
     * returns the original text — a mishearing is a much smaller problem than a
     * lost memory.
     */
    private suspend fun correctProperNouns(
        clean: String,
        transcript: Transcript,
        source: String,
        knownTopics: List<String>
    ): String {
        // "It's Nyx, not next" is the user teaching Naomi a word, and typing it
        // is a better lesson than saying it. Learned before this capture is
        // corrected, so it takes effect now rather than on the next recording.
        if (source != MemoryEntryEntity.SOURCE_SHARED) {
            try {
                CorrectionDetector.detectSpelling(clean)?.let { lesson ->
                    knowledgeRepository.confirmSpelling(lesson.correct)
                    knowledgeRepository.blockSpelling(lesson.misheard)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not record a spelling correction", e)
            }
        }

        if (source != MemoryEntryEntity.SOURCE_SPOKEN) return clean
        return try {
            val vocabulary = knowledgeRepository.getVocabulary()
            val result = TranscriptNormalizer.correct(
                transcript = Transcript(clean, transcript.utterances),
                vocabulary = vocabulary,
                topicPaths = knownTopics
            )
            if (result.changed) {
                // Counts only, never the words — a log line naming what someone
                // said is the same leak as sending it somewhere.
                Log.d(TAG, "Repaired ${result.changes.size} misheard term(s)")
            }
            result.text
        } catch (e: Exception) {
            Log.w(TAG, "Could not load vocabulary; keeping the transcript as heard", e)
            clean
        }
    }

    /**
     * Walks the provider chain until one produces a result. A provider that is
     * unavailable, times out, or returns something unusable is skipped rather
     * than allowed to fail the capture.
     */
    private suspend fun analyse(segment: String, knownTopics: List<String>): ExtractedKnowledge {
        for (provider in providers) {
            val result = try {
                if (provider.isAvailable()) provider.analyze(segment, knownTopics) else null
            } catch (e: Exception) {
                Log.w(TAG, "Provider '${provider.id}' failed; trying the next one", e)
                null
            }
            if (result != null) return result
        }
        // The chain is configured with a provider that cannot fail at the end,
        // so this is a wiring error rather than a runtime condition.
        error("No intelligence provider produced a result")
    }

    private companion object {
        const val TAG = "ProcessThought"
    }
}
