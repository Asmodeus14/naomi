package com.naomi.app.domain.usecases

import com.naomi.app.ai.intelligence.LocalIntelligenceEngine
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

    suspend operator fun invoke(chunkTranscript: String): AmbientChunkResult? {
        val clean = chunkTranscript.trim()
        if (clean.isBlank()) return null

        val knowledge = LocalIntelligenceEngine.analyze(clean)
        val note = knowledgeRepository.saveNote(
            knowledge = knowledge,
            rawTranscript = clean,
            cleanTranscript = clean
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

    fun reset() {
        lastTopicId = null
    }
}
