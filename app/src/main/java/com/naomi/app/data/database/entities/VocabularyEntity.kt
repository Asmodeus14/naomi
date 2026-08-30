package com.naomi.app.data.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A word Naomi should get right.
 *
 * Speech recognisers are trained on general English and will confidently render
 * a project called "Nyx" as "next" every time. This table is the counterweight:
 * the names, products and technical terms that matter to this particular user,
 * so a mishearing can be recognised as one.
 *
 * It is deliberately separate from `entity_refs`, which is a per-note occurrence
 * log with a row per mention and no global uniqueness. This is the opposite
 * shape — one row per term, deduplicated, counted, and queryable without a note
 * in hand.
 *
 * Nothing here ever leaves the device. A vocabulary is an unusually precise
 * description of what someone works on and who they know, and shipping it
 * somewhere to improve recognition would trade exactly the thing this app
 * exists to protect.
 */
@Entity(
    tableName = "vocabulary",
    indices = [
        // Uniqueness is on the normalised form so "Nyx", "nyx" and "NYX" are
        // one term rather than three competing corrections.
        Index(value = ["normalized"], unique = true),
        Index("phoneticKey")
    ]
)
data class VocabularyEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** How the word should be written once corrected — "Nyx", "PostgreSQL". */
    val term: String,

    /** Lowercased form, for lookup and uniqueness. */
    val normalized: String,

    /** Sound-key from [com.naomi.app.ai.intelligence.Phonetics], for "sounds like" matching. */
    val phoneticKey: String,

    val kind: String,

    val source: String,

    /**
     * How many times this term has been seen in the user's own words.
     *
     * Confidence scales with it: a term said thirty times is a safer correction
     * target than one seeded from a generic list and never used. A seeded term
     * starts at zero for exactly that reason.
     */
    val occurrences: Int = 0,

    val lastSeenAt: Long = System.currentTimeMillis(),

    /**
     * Set when the user has told Naomi to stop correcting *to* this term.
     *
     * Kept as a flag rather than deleting the row, because a deleted term would
     * simply be relearned from the topic tree on the next capture and the user
     * would have to refuse it again.
     */
    val isBlocked: Boolean = false
) {
    companion object {
        const val KIND_PROJECT = "PROJECT"
        const val KIND_PERSON = "PERSON"
        const val KIND_TECH = "TECH"
        const val KIND_TERM = "TERM"

        /** Shipped with the app; generic, never seen in this user's speech yet. */
        const val SOURCE_SEEDED = "SEEDED"
        /** Learned from a topic the user's own words created. */
        const val SOURCE_TOPIC = "TOPIC"
        /** Learned from an entity extracted from a memory. */
        const val SOURCE_ENTITY = "ENTITY"
        /** The user said it explicitly: "it's Nyx, not next". Trusted most. */
        const val SOURCE_USER = "USER"
    }
}
