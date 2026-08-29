package com.naomi.app.ai.intelligence

/**
 * Decides whether something newly said continues an existing memory or starts a
 * new one.
 *
 * This is what makes Naomi a memory rather than a pile. Saying "the ring buffer
 * is stable" and, two days later, "I fixed the ring buffer vertex count" is one
 * subject with a history, not two unrelated notes — but "the ring buffer" and
 * "the render pipeline" are genuinely different things and must stay apart.
 *
 * The asymmetry is the same one that governs topic matching, and it points the
 * same way: wrongly merging two subjects destroys the user's history and is
 * nearly impossible to notice afterwards, while wrongly splitting leaves two
 * entries they can see and fix. So the bar is high.
 */
object MemoryMerger {

    /** A memory already stored under the topic this utterance resolved to. */
    data class Candidate(
        val noteId: Long,
        val title: String
    )

    sealed interface Decision {
        /** Append to [noteId]; [title] is what the memory should now be called. */
        data class Continue(val noteId: Long, val title: String) : Decision
        data object StartNew : Decision
    }

    /**
     * Deliberately stricter than topic matching (0.82).
     *
     * A topic is a container, so putting a memory in a slightly-too-broad one is
     * recoverable. Continuing the wrong memory rewrites its summary and buries a
     * distinct thought inside another's history.
     */
    const val MERGE_THRESHOLD: Double = 0.88

    /**
     * @param newTitle    the title extracted from what was just said
     * @param candidates  memories already filed under the same topic
     */
    fun decide(newTitle: String, candidates: List<Candidate>): Decision {
        val normalizedNew = TopicMatcher.normalize(newTitle)
        if (normalizedNew.isBlank()) return Decision.StartNew

        var best: Candidate? = null
        var bestScore = 0.0

        for (candidate in candidates) {
            val normalizedExisting = TopicMatcher.normalize(candidate.title)
            if (normalizedExisting.isBlank()) continue

            val score = TopicMatcher.calculateSimilarity(normalizedNew, normalizedExisting)
            if (score > bestScore) {
                bestScore = score
                best = candidate
            }
        }

        val match = best ?: return Decision.StartNew
        if (bestScore < MERGE_THRESHOLD) return Decision.StartNew

        // Keep whichever title names the subject more plainly. "Ring Buffer"
        // should survive a later "Ring Buffer Vertex Count" — the extra words
        // describe this one update, not the thing being remembered.
        val keptTitle = if (isMoreGeneral(match.title, newTitle)) match.title else newTitle
        return Decision.Continue(match.noteId, keptTitle)
    }

    /**
     * True when [existing] is the broader of the two names.
     *
     * Word count is the signal: a name that says less is the one that keeps
     * being true as more gets added to the memory.
     */
    private fun isMoreGeneral(existing: String, incoming: String): Boolean {
        val existingWords = TopicMatcher.normalize(existing).split(" ").count { it.isNotBlank() }
        val incomingWords = TopicMatcher.normalize(incoming).split(" ").count { it.isNotBlank() }
        return existingWords <= incomingWords
    }
}
