package com.naomi.app.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.naomi.app.data.database.entities.VocabularyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VocabularyDao {

    /**
     * IGNORE rather than REPLACE: a term arriving again from the topic tree must
     * not reset an occurrence count or clear a user's block. Growth goes through
     * [reinforce], which is additive.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoring(term: VocabularyEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnoring(terms: List<VocabularyEntity>)

    /**
     * Records another sighting of a term already known.
     *
     * Never touches `source`, so a term first learned from a topic and later
     * confirmed by the user keeps the stronger provenance.
     */
    @Query(
        """
        UPDATE vocabulary
           SET occurrences = occurrences + 1,
               lastSeenAt = :seenAt
         WHERE normalized = :normalized
        """
    )
    suspend fun reinforce(normalized: String, seenAt: Long)

    /** Promotes a term to user-confirmed and clears any block on it. */
    @Query(
        """
        UPDATE vocabulary
           SET source = 'USER',
               isBlocked = 0,
               occurrences = occurrences + 5,
               lastSeenAt = :seenAt
         WHERE normalized = :normalized
        """
    )
    suspend fun confirmByUser(normalized: String, seenAt: Long)

    @Query("UPDATE vocabulary SET isBlocked = :blocked WHERE normalized = :normalized")
    suspend fun setBlocked(normalized: String, blocked: Boolean)

    /**
     * Everything eligible to be corrected *to*.
     *
     * Blocked terms are excluded here rather than filtered by the caller, so a
     * block cannot be forgotten at one of several call sites.
     */
    @Query("SELECT * FROM vocabulary WHERE isBlocked = 0")
    suspend fun getUsableTerms(): List<VocabularyEntity>

    @Query("SELECT * FROM vocabulary ORDER BY occurrences DESC, term ASC")
    fun getAllFlow(): Flow<List<VocabularyEntity>>

    @Query("SELECT * FROM vocabulary WHERE normalized = :normalized LIMIT 1")
    suspend fun findByNormalized(normalized: String): VocabularyEntity?

    @Query("SELECT COUNT(*) FROM vocabulary")
    suspend fun count(): Int

    @Query("DELETE FROM vocabulary WHERE normalized = :normalized")
    suspend fun delete(normalized: String)
}
