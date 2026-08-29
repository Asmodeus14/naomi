package com.naomi.app.domain.usecases

import com.naomi.app.ai.intelligence.QuestionParser
import com.naomi.app.data.database.entities.MemoryEntryEntity
import com.naomi.app.data.database.entities.NoteEntity
import com.naomi.app.data.database.entities.TaskEntity
import com.naomi.app.domain.model.Answer
import com.naomi.app.domain.model.RecalledMemory
import com.naomi.app.domain.repository.KnowledgeRepository

/**
 * Answers a question from what the user has already said.
 *
 * Two properties matter more than answer quality here.
 *
 * It never leaves the device. Recall runs entirely against the local store —
 * there is no branch in this file, or below it, that puts a question or a
 * memory on a network, and the app declares no `INTERNET` permission, so there
 * could not be one.
 *
 * It never makes anything up. Every sentence Naomi returns is either a fixed
 * phrase or a span of text the user themselves recorded. Nothing is generated,
 * paraphrased or inferred. An assistant that invents a plausible memory is worse
 * than one that says it has nothing, because the user cannot tell the two apart
 * — and Naomi's only real promise is that what it hands back is what was said.
 */
class AskNaomiUseCase(
    private val repository: KnowledgeRepository
) {

    suspend operator fun invoke(rawQuestion: String): Answer {
        val question = QuestionParser.parse(rawQuestion)
        if (!question.isAnswerable) return Answer.Unanswerable

        val notes = findNotes(question.terms, question.subject)
        if (notes.isEmpty()) {
            return Answer.Nothing(subject = question.subject)
        }

        val memories = notes.take(MAX_MEMORIES).map { note ->
            val entries = repository.getEntriesForNote(note.id)
            RecalledMemory(
                note = note,
                topicPath = pathFor(note),
                // The moments that actually matched, newest first — a memory with
                // a long history should show the part that answers the question,
                // not its first line.
                matches = entries
                    .filter { entry -> question.terms.any { entry.matches(it) } }
                    .asReversed()
                    .take(MAX_ENTRIES_PER_MEMORY)
            )
        }

        val tasks = if (question.intent == QuestionParser.Intent.DEADLINE) {
            findTasks(question.terms)
        } else {
            emptyList()
        }

        return Answer.Found(
            subject = question.subject,
            intent = question.intent,
            memories = memories,
            tasks = tasks
        )
    }

    /**
     * Whole phrase first, then individual terms.
     *
     * The phrase is the stronger signal — "ring buffer" as one string cannot
     * match a memory that only mentions buffers — so a phrase hit is preferred
     * outright. Falling back to terms is what keeps a question answerable when
     * the user's wording differs from their own earlier wording, which it
     * usually does.
     */
    private suspend fun findNotes(terms: List<String>, subject: String): List<NoteEntity> {
        if (subject.isNotBlank()) {
            val exact = repository.search(subject).notes.map { it.note }
            if (exact.isNotEmpty()) return exact
        }

        val scores = mutableMapOf<Long, Int>()
        val byId = mutableMapOf<Long, NoteEntity>()
        for (term in terms) {
            for (hit in repository.search(term).notes) {
                byId[hit.note.id] = hit.note
                scores[hit.note.id] = (scores[hit.note.id] ?: 0) + 1
            }
        }

        // Most terms matched wins; ties go to the memory touched most recently,
        // because the thing you asked about is usually the thing you were just
        // doing.
        return scores.entries
            .sortedWith(
                compareByDescending<Map.Entry<Long, Int>> { it.value }
                    .thenByDescending { byId.getValue(it.key).updatedAt }
            )
            .mapNotNull { byId[it.key] }
    }

    private suspend fun findTasks(terms: List<String>): List<TaskEntity> {
        val seen = mutableSetOf<Long>()
        return terms
            .flatMap { repository.search(it).tasks }
            .filter { seen.add(it.id) }
            .sortedWith(compareBy(nullsLast()) { it.dueAt })
            .take(MAX_TASKS)
    }

    private suspend fun pathFor(note: NoteEntity): String =
        repository.getNoteDetail(note.id)?.topicPath ?: ""

    private fun MemoryEntryEntity.matches(term: String): Boolean =
        summary.contains(term, ignoreCase = true) || transcript.contains(term, ignoreCase = true)

    private companion object {
        const val MAX_MEMORIES = 5
        const val MAX_ENTRIES_PER_MEMORY = 3
        const val MAX_TASKS = 5
    }
}
