package com.naomi.app.ai.intelligence

/**
 * Turns a question asked in ordinary words into something the local store can
 * be queried with.
 *
 * People do not search their own memory with keywords. They ask "what did I say
 * about the ring buffer" or "when is the presentation due". Handing that string
 * straight to a LIKE query matches nothing, because almost none of those words
 * appear in what the user actually said — so the opener has to come off before
 * anything else happens.
 *
 * This is deliberately a small amount of string handling rather than a model.
 * The answer Naomi gives is assembled from stored records, so the only job here
 * is working out which records to fetch; a wrong guess costs a worse result set,
 * never a wrong claim.
 */
object QuestionParser {

    enum class Intent {
        /** "What did I say about X" — wants the memory. */
        RECALL,

        /** "When is X due" — wants the date attached to it. */
        DEADLINE
    }

    data class Question(
        val intent: Intent,
        /** The subject as the user phrased it, for echoing back: "ring buffer". */
        val subject: String,
        /** Content words to match on, stop words removed. */
        val terms: List<String>
    ) {
        val isAnswerable: Boolean get() = terms.isNotEmpty()
    }

    /**
     * Openers stripped from the front of a question, longest first so that
     * "what did I say about" wins over "what".
     */
    private val openers: List<String> = listOf(
        "what did i tell you about",
        "what have i said about",
        "what did i say about",
        "what do i know about",
        "what did i note about",
        "did i say anything about",
        "do i have anything about",
        "do i have anything on",
        "is there anything about",
        "have i said anything about",
        "remind me what i said about",
        "remind me about",
        "remind me what",
        "tell me about",
        "tell me what",
        "search for",
        "look up",
        "show me",
        "find me",
        "anything about",
        "anything on",
        "what about",
        "how about",
        "when is the",
        "when was the",
        "when is",
        "when was",
        "when do i",
        "when did i",
        "when's",
        "whens",
        "where did i",
        "why did i",
        "how did i",
        "who is",
        "who was",
        "what is",
        "what was",
        "what's",
        "whats",
        "find",
        "recall"
    ).sortedByDescending { it.length }

    /**
     * Words that make a question about timing. Checked against the original
     * text, because the opener that reveals the intent is the part removed.
     */
    private val deadlineMarkers = listOf(
        "when is", "when was", "when's", "whens", "when do", "when did",
        "due", "deadline", "by when", "how long until"
    )

    /** Trailing words that end a question without being part of the subject. */
    private val danglingTail = setOf(
        "due", "again", "exactly", "already", "then", "now", "please", "about",
        "on", "for", "of", "to", "at", "in"
    )

    fun parse(raw: String): Question {
        val normalized = raw.lowercase()
            .replace(Regex("[?!.,;:]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (normalized.isBlank()) {
            return Question(Intent.RECALL, "", emptyList())
        }

        val intent = if (deadlineMarkers.any { normalized.startsWith(it) || normalized.contains(" $it") }) {
            Intent.DEADLINE
        } else {
            Intent.RECALL
        }

        var body = normalized
        for (opener in openers) {
            if (body == opener) {
                body = ""
                break
            }
            if (body.startsWith("$opener ")) {
                body = body.removePrefix("$opener ").trim()
                break
            }
        }

        // Leading determiners and possessives survive every opener and belong to
        // the question, not the subject: "the ring buffer" is filed as "ring
        // buffer".
        val words = body.split(" ")
            .filter { it.isNotBlank() }
            .toMutableList()
        while (words.isNotEmpty() && words.first() in setOf("the", "a", "an", "my", "our", "that", "this")) {
            words.removeAt(0)
        }
        while (words.isNotEmpty() && words.last() in danglingTail) {
            words.removeAt(words.size - 1)
        }

        val subject = words.joinToString(" ")
        val terms = words.filterNot { Lexicon.isStopWord(it) || it.length < 2 }.distinct()

        // A question that is nothing but stop words ("what did I say about it")
        // has no subject; falling back to the raw words would match everything,
        // which reads as an answer while being noise.
        return Question(intent = intent, subject = subject, terms = terms)
    }
}
