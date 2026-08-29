package com.naomi.app.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = TopicEntity::class,
            parentColumns = ["id"],
            childColumns = ["topicId"],
            onDelete = ForeignKey.CASCADE
        ),
        // Deleting a subtopic should return the memory to its root topic, not
        // destroy it. Without this the column just dangles at a missing row.
        ForeignKey(
            entity = TopicEntity::class,
            parentColumns = ["id"],
            childColumns = ["subtopicId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("topicId"),
        Index("subtopicId"),
        Index("createdAt")
    ]
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val topicId: Long,
    val subtopicId: Long? = null,
    val title: String,
    val summary: String,
    val rawTranscript: String,
    val cleanTranscript: String,
    val idea: String? = null,
    val decision: String? = null,
    val isCorrection: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
