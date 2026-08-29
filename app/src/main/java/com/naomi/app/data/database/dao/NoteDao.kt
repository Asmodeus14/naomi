package com.naomi.app.data.database.dao

import androidx.room.*
import com.naomi.app.data.database.entities.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Delete
    suspend fun delete(note: NoteEntity)

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: Long): NoteEntity?

    @Query("SELECT * FROM notes WHERE id = :id")
    fun getByIdFlow(id: Long): Flow<NoteEntity?>

    /**
     * Every memory filed at [topicId] *or anywhere beneath it*.
     *
     * The recursive walk matters: a note about "Nyx > Graphics > Ring Buffer" is
     * linked to its root and its leaf, so a plain `topicId = ?` lookup returns
     * nothing for "Graphics" and every intermediate node in the tree renders as
     * an empty topic.
     */
    @Query(
        """
        WITH RECURSIVE descendants(topic) AS (
            SELECT :topicId
            UNION
            SELECT t.id FROM topics t JOIN descendants d ON t.parentId = d.topic
        )
        SELECT * FROM notes
        WHERE topicId IN (SELECT topic FROM descendants)
           OR subtopicId IN (SELECT topic FROM descendants)
        ORDER BY createdAt DESC
        """
    )
    fun getNotesForTopicFlow(topicId: Long): Flow<List<NoteEntity>>

    @Query(
        """
        WITH RECURSIVE descendants(topic) AS (
            SELECT :topicId
            UNION
            SELECT t.id FROM topics t JOIN descendants d ON t.parentId = d.topic
        )
        SELECT * FROM notes
        WHERE topicId IN (SELECT topic FROM descendants)
           OR subtopicId IN (SELECT topic FROM descendants)
        ORDER BY createdAt DESC
        """
    )
    suspend fun getNotesForTopic(topicId: Long): List<NoteEntity>

    /** Notes filed directly at this topic, not counting descendants. */
    @Query("SELECT COUNT(*) FROM notes WHERE topicId = :topicId OR subtopicId = :topicId")
    suspend fun countNotesDirectlyOn(topicId: Long): Int

    @Query("SELECT * FROM notes ORDER BY createdAt DESC LIMIT :limit")
    fun getRecentNotesFlow(limit: Int = 20): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecentNotes(limit: Int): List<NoteEntity>

    @Query("SELECT * FROM notes ORDER BY createdAt DESC")
    suspend fun getAllNotes(): List<NoteEntity>

    /**
     * [query] must already have `%`, `_` and `\` escaped by the caller — the
     * ESCAPE clause here is what makes those characters literal rather than
     * wildcards that match every row in the table.
     */
    @Query(
        """
        SELECT * FROM notes
        WHERE title LIKE '%' || :query || '%' ESCAPE '\'
           OR summary LIKE '%' || :query || '%' ESCAPE '\'
           OR cleanTranscript LIKE '%' || :query || '%' ESCAPE '\'
           OR rawTranscript LIKE '%' || :query || '%' ESCAPE '\'
        ORDER BY createdAt DESC
        LIMIT :limit
        """
    )
    suspend fun searchNotes(query: String, limit: Int = 50): List<NoteEntity>

    /**
     * Other memories that mention the same named things as [noteId], most
     * overlap first. This is what "Related" is built from — a real co-occurrence
     * lookup rather than a fixed list of associations.
     */
    @Query(
        """
        SELECT n.* FROM notes n
        JOIN entity_refs e ON e.noteId = n.id
        WHERE n.id != :noteId
          AND e.name IN (SELECT name FROM entity_refs WHERE noteId = :noteId)
        GROUP BY n.id
        ORDER BY COUNT(e.id) DESC, n.createdAt DESC
        LIMIT :limit
        """
    )
    suspend fun getRelatedNotes(noteId: Long, limit: Int = 5): List<NoteEntity>

    @Query("SELECT COUNT(*) FROM notes")
    suspend fun getNoteCount(): Int

    @Query("SELECT COALESCE(SUM(LENGTH(rawTranscript) + LENGTH(cleanTranscript) + LENGTH(summary) + LENGTH(title)), 0) FROM notes")
    suspend fun getTotalTextBytes(): Long

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * Clearing `subtopicId` is required, not incidental: leaving it set means
     * the moved note keeps matching its previous subtopic and shows up under
     * both the old and the new location.
     */
    @Query("UPDATE notes SET topicId = :newTopicId, subtopicId = NULL, updatedAt = :updatedAt WHERE id = :noteId")
    suspend fun updateNoteTopic(noteId: Long, newTopicId: Long, updatedAt: Long = System.currentTimeMillis())
}
