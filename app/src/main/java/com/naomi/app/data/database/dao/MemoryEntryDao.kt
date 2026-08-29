package com.naomi.app.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.naomi.app.data.database.entities.MemoryEntryEntity

@Dao
interface MemoryEntryDao {

    @Insert
    suspend fun insert(entry: MemoryEntryEntity): Long

    /** Oldest first: a history reads forwards. */
    @Query("SELECT * FROM memory_entries WHERE noteId = :noteId ORDER BY createdAt ASC")
    suspend fun getEntriesForNote(noteId: Long): List<MemoryEntryEntity>

    @Query("SELECT COUNT(*) FROM memory_entries WHERE noteId = :noteId")
    suspend fun countForNote(noteId: Long): Int

    /**
     * Every entry under a topic and all its descendants, newest first.
     *
     * The recursive walk matters for the same reason it does elsewhere: a
     * timeline for "Nyx" that omitted everything said about "Ring Buffer" would
     * be showing the user a gap where their actual history is.
     */
    @Query(
        """
        WITH RECURSIVE descendants(topic) AS (
            SELECT :topicId
            UNION
            SELECT t.id FROM topics t JOIN descendants d ON t.parentId = d.topic
        )
        SELECT e.* FROM memory_entries e
        JOIN notes n ON n.id = e.noteId
        WHERE n.topicId IN (SELECT topic FROM descendants)
           OR n.subtopicId IN (SELECT topic FROM descendants)
        ORDER BY e.createdAt DESC
        LIMIT :limit
        """
    )
    suspend fun getTimelineForTopic(topicId: Long, limit: Int = 100): List<MemoryEntryEntity>

    @Query("DELETE FROM memory_entries WHERE noteId = :noteId")
    suspend fun deleteForNote(noteId: Long)
}
