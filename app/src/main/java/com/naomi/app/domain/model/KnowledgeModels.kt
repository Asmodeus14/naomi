package com.naomi.app.domain.model

import com.naomi.app.data.database.entities.*

/**
 * A phrase the speaker kept returning to, with the score that ranked it.
 * Lives in the domain layer because both the extractor that produces it and the
 * resolver that files memories by it need to agree on the shape.
 */
data class Keyphrase(
    val text: String,
    val score: Double,
    val wordCount: Int
)

/**
 * Everything Naomi understood from one coherent thought.
 */
data class ExtractedKnowledge(
    val title: String,
    val summary: String,
    /**
     * Ranked phrases the passage is about. Used to resolve a topic when
     * [topicPath] is null — the heuristic path can't see the topic tree, so
     * placement is worked out downstream where the tree is loaded.
     */
    val keyphrases: List<Keyphrase> = emptyList(),
    /**
     * Root-to-leaf placement chosen by a provider that could see the user's
     * existing topics, e.g. ["Nyx", "Graphics"]. When set, it is used directly;
     * when null, placement falls back to [keyphrases].
     */
    val topicPath: List<String>? = null,
    val idea: String? = null,
    val decision: String? = null,
    val tasks: List<ExtractedTask> = emptyList(),
    val entities: List<ExtractedEntity> = emptyList(),
    val isCorrection: Boolean = false
)

data class ExtractedTask(
    val title: String,
    /** The phrase the speaker used ("Friday"), kept because it reads better than a date. */
    val deadline: String? = null,
    /** The resolved instant, for sorting, bucketing and overdue detection. */
    val dueAt: Long? = null,
    val isCompleted: Boolean = false
)

data class ExtractedEntity(
    val name: String,
    val type: String, // "PERSON", "PROJECT", "TOOL", "CONCEPT", "METRIC"
    val detail: String? = null
)

data class TopicNode(
    val topic: TopicEntity,
    val subtopics: List<TopicNode> = emptyList(),
    val noteCount: Int = 0,
    val recentNotes: List<NoteEntity> = emptyList()
)

data class NoteDetail(
    val note: NoteEntity,
    val topic: TopicEntity,
    val subtopic: TopicEntity? = null,
    /** Full ancestry of where this memory is filed, e.g. "Nyx · Graphics · Ring Buffer". */
    val topicPath: String = topic.name,
    val tasks: List<TaskEntity> = emptyList(),
    val entities: List<EntityRefEntity> = emptyList(),
    /** Everything said about this memory, oldest first. */
    val entries: List<MemoryEntryEntity> = emptyList(),
    /** Topics reached from this memory's named things — computed, not stored. */
    val relatedTopics: List<TopicEntity> = emptyList(),
    /** Other memories that mention the same things, most overlap first. */
    val relatedNotes: List<NoteEntity> = emptyList()
)

/**
 * One moment in a topic's history, carrying the memory it belongs to so the
 * timeline can say what was being talked about, not just what was said.
 */
data class TimelineEntry(
    val entry: MemoryEntryEntity,
    val noteTitle: String
)

/**
 * A memory that answers a question, with the moments in it that did.
 */
data class RecalledMemory(
    val note: NoteEntity,
    /** Breadcrumb such as "Nyx · Graphics". */
    val topicPath: String,
    /** The entries containing what was asked about, newest first. */
    val matches: List<MemoryEntryEntity>
)

/**
 * What Naomi can say in reply to a question.
 *
 * There is deliberately no "generated answer" case. Every variant either states
 * a fact about the store ("nothing here about X") or hands back records the user
 * created themselves. See [com.naomi.app.domain.usecases.AskNaomiUseCase].
 */
sealed interface Answer {
    /** Nothing has been asked yet. */
    data object Idle : Answer

    /** The question had no subject left once the words around it came off. */
    data object Unanswerable : Answer

    /** Understood, but there is genuinely nothing stored about it. */
    data class Nothing(val subject: String) : Answer

    data class Found(
        val subject: String,
        val intent: com.naomi.app.ai.intelligence.QuestionParser.Intent,
        val memories: List<RecalledMemory>,
        val tasks: List<TaskEntity>
    ) : Answer
}

/**
 * A search hit carrying enough context to be recognisable without opening it —
 * where the memory lives and the words around the match.
 */
data class SearchHit(
    val note: NoteEntity,
    /** Breadcrumb such as "Nyx · Graphics". */
    val topicPath: String,
    /** Text around the match, elided at both ends. */
    val snippet: String
)

data class SearchResult(
    val notes: List<SearchHit> = emptyList(),
    val topics: List<TopicEntity> = emptyList(),
    val tasks: List<TaskEntity> = emptyList()
) {
    val isEmpty: Boolean get() = notes.isEmpty() && topics.isEmpty() && tasks.isEmpty()
}

data class StorageStats(
    val databaseBytes: Long,
    val audioBytes: Long,
    val totalBytes: Long,
    val noteCount: Int,
    val recordingCount: Int,
    val retentionPolicy: RetentionPolicy
)

enum class RetentionPolicy(val value: String, val label: String) {
    NEVER("NEVER", "Never keep"),
    HOURS_24("24_HOURS", "Keep for 24 hours"),
    DAYS_7("7_DAYS", "Keep for 7 days"),
    FOREVER("FOREVER", "Keep forever");

    companion object {
        fun fromValue(value: String?): RetentionPolicy {
            return entries.firstOrNull { it.value == value } ?: NEVER
        }
    }
}

enum class AppThemeMode(val value: String, val label: String) {
    SYSTEM("SYSTEM", "System"),
    LIGHT("LIGHT", "Light"),
    DARK("DARK", "Dark");

    companion object {
        fun fromValue(value: String?): AppThemeMode {
            return entries.firstOrNull { it.value.equals(value, ignoreCase = true) } ?: SYSTEM
        }
    }
}

