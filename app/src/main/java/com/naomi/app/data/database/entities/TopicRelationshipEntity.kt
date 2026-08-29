package com.naomi.app.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "topic_relationships",
    foreignKeys = [
        ForeignKey(
            entity = TopicEntity::class,
            parentColumns = ["id"],
            childColumns = ["fromTopicId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TopicEntity::class,
            parentColumns = ["id"],
            childColumns = ["toTopicId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("fromTopicId"),
        Index("toTopicId"),
        Index(value = ["fromTopicId", "toTopicId"], unique = true)
    ]
)
data class TopicRelationshipEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fromTopicId: Long,
    val toTopicId: Long,
    val relationshipType: String = "RELATED" // "RELATED", "DEPENDS_ON", "PART_OF"
)
