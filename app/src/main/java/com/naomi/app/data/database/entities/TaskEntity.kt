package com.naomi.app.data.database.entities

import androidx.room.ColumnInfo
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
    /**
     * What kind of thing this is: work to do, an interruption the user asked
     * for, or an occasion.
     *
     * A separate `events` table was the obvious shape and the wrong one. All
     * three keep the same link back to the memory that produced them, the same
     * due date, the same completion, and appear in the same list — a second
     * table would have duplicated the foreign keys, the reminder machinery and
     * the bucketing in TasksScreen to express one adjective.
     */
    @ColumnInfo(defaultValue = "TASK")
    val kind: String = KIND_TASK,
    /** When an occasion finishes. Only set for [KIND_EVENT]. */
    val endAt: Long? = null,
    /**
     * Whether the user actually said a clock time, as opposed to Naomi
     * resolving "tomorrow" to a default hour.
     *
     * This is what decides between an exact and an inexact alarm, and it has to
     * be stored rather than recomputed: reminders are re-scheduled from the
     * database after a reboot, long after the sentence is gone.
     */
    @ColumnInfo(defaultValue = "0")
    val hasExactTime: Boolean = false,
    /**
     * When this was handed to the calendar app, if it was.
     *
     * Naomi does not hold the calendar permission and cannot read back whether
     * the event was actually saved — the user may have cancelled the screen. So
     * this records that Naomi *offered*, which is the only thing it honestly
     * knows, and is enough to stop it offering the same event twice.
     */
    val calendarAddedAt: Long? = null,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val KIND_TASK = "TASK"
        const val KIND_REMINDER = "REMINDER"
        const val KIND_EVENT = "EVENT"
    }
}
