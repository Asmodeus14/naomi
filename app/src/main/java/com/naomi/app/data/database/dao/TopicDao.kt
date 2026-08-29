package com.naomi.app.data.database.dao

import androidx.room.*
import com.naomi.app.data.database.entities.TopicEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TopicDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(topic: TopicEntity): Long

    @Update
    suspend fun update(topic: TopicEntity)

    @Delete
    suspend fun delete(topic: TopicEntity)

    @Query("SELECT * FROM topics WHERE id = :id")
    suspend fun getById(id: Long): TopicEntity?

    @Query("SELECT * FROM topics WHERE normalizedName = :normalizedName LIMIT 1")
    suspend fun getByNormalizedName(normalizedName: String): TopicEntity?

    @Query("SELECT * FROM topics WHERE parentId IS NULL ORDER BY updatedAt DESC")
    fun getRootTopicsFlow(): Flow<List<TopicEntity>>

    @Query("SELECT * FROM topics WHERE parentId IS NULL ORDER BY updatedAt DESC")
    suspend fun getRootTopics(): List<TopicEntity>

    @Query("SELECT * FROM topics WHERE parentId = :parentId ORDER BY name ASC")
    fun getSubtopicsFlow(parentId: Long): Flow<List<TopicEntity>>

    @Query("SELECT * FROM topics WHERE parentId = :parentId ORDER BY name ASC")
    suspend fun getSubtopics(parentId: Long): List<TopicEntity>

    @Query("SELECT * FROM topics ORDER BY updatedAt DESC")
    suspend fun getAllTopics(): List<TopicEntity>

    @Query("SELECT * FROM topics ORDER BY updatedAt DESC")
    fun getAllTopicsFlow(): Flow<List<TopicEntity>>

    /** [query] must arrive with LIKE wildcards already escaped by the caller. */
    @Query(
        """
        SELECT * FROM topics
        WHERE name LIKE '%' || :query || '%' ESCAPE '\'
           OR normalizedName LIKE '%' || :query || '%' ESCAPE '\'
        ORDER BY updatedAt DESC
        LIMIT :limit
        """
    )
    suspend fun searchTopics(query: String, limit: Int = 30): List<TopicEntity>

    /**
     * Other topics sharing [parentId], used to suggest neighbours for a memory.
     * The null branch is spelled out because `parentId = NULL` never matches in
     * SQL, which would silently return nothing for every root-level topic.
     */
    @Query(
        """
        SELECT * FROM topics
        WHERE id != :excludeId
          AND ((:parentId IS NULL AND parentId IS NULL) OR parentId = :parentId)
        ORDER BY updatedAt DESC
        LIMIT :limit
        """
    )
    suspend fun getSiblings(parentId: Long?, excludeId: Long, limit: Int = 8): List<TopicEntity>
}
