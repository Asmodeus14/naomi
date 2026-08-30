package com.naomi.app.domain.usecases

import android.util.Log
import com.naomi.app.ai.intelligence.LocalIntelligenceEngine
import com.naomi.app.ai.intelligence.TranscriptNormalizer
import com.naomi.app.ai.speech.Transcript
import com.naomi.app.data.database.entities.NoteEntity
import com.naomi.app.domain.repository.KnowledgeRepository

data class AmbientChunkResult(
    val note: NoteEntity,
    val isNewTopic: Boolean,
    val currentTopicName: String
)

/**
 * Handles one chunk of a continuous listening session.
 *
 * Unlike [ProcessThoughtUseCase] this does not segment: a chunk is already a
 * time slice, and cutting it further tends to strand half-sentences.
 */
class ProcessAmbientChunkUseCase(
    private val knowledgeRepository: KnowledgeRepository
) {
    private var lastTopicId: Long? = null

    suspend operator fun invoke(chunk: Transcript): AmbientChunkResult? {
        val clean = chunk.text.trim()
        if (clean.isBlank()) return null

        // Ambient capture is where mishearings hurt most — nobody is watching
        // the screen to catch one — so it gets the same repair as a deliberate
        // recording, and for the same reason the raw text is kept as spoken.
        val corrected = correctProperNouns(clean, chunk)

        val knowledge = LocalIntelligenceEngine.analyze(corrected)
        val note = knowledgeRepository.saveNote(
            knowledge = knowledge,
            rawTranscript = clean,
            cleanTranscript = corrected
        )

        // Compare where the memory actually landed rather than what the text
        // looked like, so this reflects the real filing decision.
        val landedTopicId = note.subtopicId ?: note.topicId
        val isNewTopic = lastTopicId != null && lastTopicId != landedTopicId
        lastTopicId = landedTopicId

        val topicName = knowledgeRepository.getTopicById(landedTopicId)?.name.orEmpty()

        return AmbientChunkResult(
            note = note,
            isNewTopic = isNewTopic,
            currentTopicName = topicName
        )
    }

    private suspend fun correctProperNouns(clean: String, chunk: Transcript): String = try {
        TranscriptNormalizer.correct(
            transcript = Transcript(clean, chunk.utterances),
            vocabulary = knowledgeRepository.getVocabulary(),
            topicPaths = knowledgeRepository.getTopicPaths()
        ).text
    } catch (e: Exception) {
        Log.w(TAG, "Could not load vocabulary; keeping the chunk as heard", e)
        clean
    }

    fun reset() {
        lastTopicId = null
    }

    private companion object {
        const val TAG = "ProcessAmbientChunk"
    }
}
