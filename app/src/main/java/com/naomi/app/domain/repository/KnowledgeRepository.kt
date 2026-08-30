package com.naomi.app.domain.repository

import com.naomi.app.ai.intelligence.VocabularyTerm
import com.naomi.app.data.database.entities.*
import com.naomi.app.domain.model.*
import kotlinx.coroutines.flow.Flow

interface KnowledgeRepository {
    fun getRootTopicsFlow(): Flow<List<TopicEntity>>
    suspend fun getRootTopics(): List<TopicEntity>
    fun getSubtopicsFlow(parentId: Long): Flow<List<TopicEntity>>
    suspend fun getSubtopics(parentId: Long): List<TopicEntity>
    suspend fun getAllTopics(): List<TopicEntity>
    fun getAllTopicsFlow(): Flow<List<TopicEntity>>

    /**
     * Every topic as a root-to-leaf path, e.g. "Nyx > Graphics > Ring Buffer".
     * Handed to an [com.naomi.app.domain.intelligence.IntelligenceProvider] so it
     * can file a memory under a topic the user already has rather than minting a
     * near-duplicate.
     */
    suspend fun getTopicPaths(): List<String>
    suspend fun getTopicById(id: Long): TopicEntity?
    suspend fun getTopicByNormalizedName(name: String): TopicEntity?
    suspend fun insertOrGetTopic(name: String, parentId: Long? = null): TopicEntity
    suspend fun updateTopic(topic: TopicEntity)

    fun getRecentNotesFlow(limit: Int = 20): Flow<List<NoteEntity>>
    suspend fun getRecentNotes(limit: Int = 20): List<NoteEntity>
    fun getNotesForTopicFlow(topicId: Long): Flow<List<NoteEntity>>
    suspend fun getNotesForTopic(topicId: Long): List<NoteEntity>
    suspend fun getNoteById(id: Long): NoteEntity?
    fun getNoteByIdFlow(id: Long): Flow<NoteEntity?>
    suspend fun getNoteDetail(noteId: Long): NoteDetail?
    /**
     * Records something the user said.
     *
     * This may create a memory or continue an existing one — the caller does
     * not choose, because the user did not either. [source] and [sourceUrl]
     * describe where the words came from, which is what lets shared web content
     * be distinguished from the user's own speech later.
     */
    suspend fun saveNote(
        knowledge: ExtractedKnowledge,
        rawTranscript: String,
        cleanTranscript: String,
        source: String = MemoryEntryEntity.SOURCE_SPOKEN,
        sourceUrl: String? = null
    ): NoteEntity

    /** The history of one memory, oldest first. */
    suspend fun getEntriesForNote(noteId: Long): List<MemoryEntryEntity>

    /** Everything said under a topic and its descendants, newest first. */
    suspend fun getTimelineForTopic(topicId: Long): List<TimelineEntry>
    suspend fun moveNoteToTopic(noteId: Long, newTopicId: Long)
    suspend fun deleteNote(noteId: Long)

    fun getTasksForNoteFlow(noteId: Long): Flow<List<TaskEntity>>
    fun getPendingTasksFlow(): Flow<List<TaskEntity>>

    /** Everything still outstanding. Used to reconcile reminders with reality. */
    suspend fun getPendingTasks(): List<TaskEntity>
    fun getAllTasksFlow(): Flow<List<TaskEntity>>
    suspend fun setTaskCompleted(taskId: Long, isCompleted: Boolean)

    fun getRelatedTopicsFlow(topicId: Long): Flow<List<TopicEntity>>
    suspend fun addTopicRelationship(fromTopicId: Long, toTopicId: Long, type: String = "RELATED")

    /**
     * Fills the vocabulary with the shipped seed list the first time it is
     * empty, and does nothing on every launch after that.
     *
     * Separate from learning because seeded terms are generic: they arrive with
     * an occurrence count of zero and no place in the topic tree, so they can
     * never accumulate the contextual evidence a correction needs on their own.
     */
    suspend fun ensureVocabularySeeded()

    /** Everything Naomi may correct a misheard word *to*. Blocked terms excluded. */
    suspend fun getVocabulary(): List<VocabularyTerm>

    /**
     * The user said the word out loud and meant it — "it's Nyx, not next".
     * Trusted above anything inferred from the topic tree.
     */
    suspend fun confirmSpelling(term: String)

    /** Stop correcting things to this word. */
    suspend fun blockSpelling(term: String)

    suspend fun search(query: String): SearchResult
    suspend fun buildTopicTree(): List<TopicNode>
    suspend fun getTopicSummary(topicId: Long): String
    suspend fun exportMarkdown(rootTopicId: Long? = null): String
}
