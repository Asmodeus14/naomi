package com.naomi.app.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TopicEntity::class,
            parentColumns = ["id"],
            childColumns = ["topicId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("noteId"),
        Index("topicId"),
        // Deliberately no index on isCompleted: a two-value column has almost no
        // selectivity, so an index there costs every write and buys no read.
        Index("dueAt")
    ]
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val noteId: Long,
    val topicId: Long,
    val title: String,
    /**
     * The wording the speaker used ("Friday", "Tomorrow"), kept for display
     * because it reads more naturally than a formatted date.
     */
    val deadline: String? = null,
    /**
     * The resolved instant. Stored alongside [deadline] because the phrase on
     * its own cannot be sorted, bucketed or tested for overdue — and quietly
     * means something different tomorrow than it did when it was spoken.
     */
    val dueAt: Long? = null,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
