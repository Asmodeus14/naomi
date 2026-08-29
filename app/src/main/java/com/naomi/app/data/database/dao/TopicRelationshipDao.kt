package com.naomi.app.data.database.dao

import androidx.room.*
import com.naomi.app.data.database.entities.TopicEntity
import com.naomi.app.data.database.entities.TopicRelationshipEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TopicRelationshipDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(relationship: TopicRelationshipEntity): Long

    @Query("""
        SELECT t.* FROM topics t
        INNER JOIN topic_relationships r ON (r.toTopicId = t.id AND r.fromTopicId = :topicId)
           OR (r.fromTopicId = t.id AND r.toTopicId = :topicId)
        WHERE t.id != :topicId
    """)
    fun getRelatedTopicsFlow(topicId: Long): Flow<List<TopicEntity>>

    @Query("""
        SELECT t.* FROM topics t
        INNER JOIN topic_relationships r ON (r.toTopicId = t.id AND r.fromTopicId = :topicId)
           OR (r.fromTopicId = t.id AND r.toTopicId = :topicId)
        WHERE t.id != :topicId
    """)
    suspend fun getRelatedTopics(topicId: Long): List<TopicEntity>
}
