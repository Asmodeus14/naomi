package com.naomi.app.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One thing the user said about a memory, at one moment.
 *
 * Naomi used to create a whole new note for every utterance, so saying "the ring
 * buffer is stable" and later "I fixed the ring buffer vertex count" left two
 * disconnected notes about the same thing. Repeat that for a month and the
 * product is a pile, not a memory.
 *
 * A [NoteEntity] is now the *subject* — "Ring Buffer" — and these are the things
 * said about it over time. The note's summary reflects the latest entry; the
 * entries are what make the history visible and what a timeline reads from.
 */
@Entity(
    tableName = "memory_entries",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("noteId"),
        Index("createdAt")
    ]
)
data class MemoryEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val noteId: Long,
    /** One line describing what this entry added, e.g. "Fixed vertex count". */
    val summary: String,
    /** Exactly what was said, kept so nothing the user told Naomi is lost. */
    val transcript: String,
    val idea: String? = null,
    val decision: String? = null,
    /**
     * How this entry arrived. [SOURCE_SPOKEN] and [SOURCE_TYPED] are the user's
     * own words; [SOURCE_SHARED] came in through the share sheet and may quote a
     * public page, which matters when deciding what is safe to show or export.
     */
    val source: String = SOURCE_SPOKEN,
    /** Set only for shared web content. Null for anything the user said. */
    val sourceUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val SOURCE_SPOKEN = "SPOKEN"
        const val SOURCE_TYPED = "TYPED"
        const val SOURCE_SHARED = "SHARED"
    }
}
