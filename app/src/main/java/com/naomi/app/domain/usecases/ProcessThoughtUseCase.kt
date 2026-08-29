package com.naomi.app.domain.usecases

import android.util.Log
import com.naomi.app.ai.intelligence.LocalIntelligenceEngine
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

    operator fun invoke(rawTranscript: String): Flow<ProcessingStage> = flow {
        val clean = rawTranscript.trim()
        if (clean.isBlank()) {
            emit(ProcessingStage.Failed(FailureReason.NOTHING_HEARD))
            return@flow
        }

        emit(ProcessingStage.Understanding)

        // One recording can hold several unrelated thoughts; each becomes its
        // own memory so they file in different places.
        val segments = LocalIntelligenceEngine.segmentSpeech(clean)
        if (segments.isEmpty()) {
            emit(ProcessingStage.Failed(FailureReason.NOTHING_HEARD))
            return@flow
        }

        val knownTopics = try {
            knowledgeRepository.getTopicPaths()
        } catch (e: Exception) {
            Log.w(TAG, "Could not load topic tree; placing without it", e)
            emptyList()
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
                    cleanTranscript = segment
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
