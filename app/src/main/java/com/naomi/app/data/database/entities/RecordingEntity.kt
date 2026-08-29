package com.naomi.app.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recordings",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("noteId"),
        Index("expiresAt")
    ]
)
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val noteId: Long? = null,
    val filePath: String,
    val fileSizeBytes: Long = 0L,
    val durationMs: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val retentionPolicy: String = "NEVER", // "NEVER", "24_HOURS", "7_DAYS", "FOREVER"
    val expiresAt: Long? = null,
    val isDeleted: Boolean = false
)
