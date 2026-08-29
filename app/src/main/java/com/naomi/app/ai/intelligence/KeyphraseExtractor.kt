package com.naomi.app.ai.intelligence

import com.naomi.app.domain.model.Keyphrase
import java.util.Locale
import kotlin.math.sqrt

/**
 * Extracts the phrases a passage is actually *about*, using a RAKE-style
 * (Rapid Automatic Keyword Extraction) pass.
 *
 * The approach is deliberately domain-neutral: candidate phrases are runs of
 * words containing no stop word, and each word is scored by how many other words
 * it tends to appear alongside relative to how often it occurs. Words that recur
 * inside long phrases score highest, which is a good proxy for "what the speaker
 * kept circling back to" without needing a model or a subject-matter word list.
 */
object KeyphraseExtractor {

    private const val MAX_PHRASE_WORDS = 4
    private const val MAX_TITLE_WORDS = 5

    /** Splits on any run of characters that cannot form part of a word. */
    private val phraseBoundary = Regex("[^\\p{L}\\p{N}_'’-]+")

    /** Tokens like PIPE_CONTROL, 2NF, H264, IPv6 — strong topical signal. */
    private val technicalToken = Regex("^(?=.*\\p{L})(?:.*[\\p{N}_].*|\\p{Lu}{2,})$")

    /**
     * Returns candidate keyphrases ranked strongest first.
     */
    fun extract(text: String): List<Keyphrase> {
        val candidates = candidatePhrases(text)
        if (candidates.isEmpty()) return emptyList()

        // RAKE word scores: degree(w) / frequency(w).
        val frequency = mutableMapOf<String, Int>()
        val degree = mutableMapOf<String, Int>()

        for (phrase in candidates) {
            // A word's degree is the total size of every phrase it appears in,
            // so words that co-occur widely outrank words that merely repeat.
            val span = phrase.size - 1
            for (word in phrase) {
                frequency[word] = (frequency[word] ?: 0) + 1
                degree[word] = (degree[word] ?: 0) + span
            }
        }

        val wordScore = frequency.mapValues { (word, freq) ->
            val deg = (degree[word] ?: 0) + freq
            deg.toDouble() / freq.toDouble()
        }

        val scored = candidates
            .map { phrase ->
                // Plain RAKE sums word scores, and in a short passage where
                // nothing repeats that reduces to "the longest phrase wins" —
                // a word in an n-word phrase always scores n, so the sum is n².
                // Dividing by sqrt(n) keeps a genuine preference for multi-word
                // phrases without letting a rambling four-word run beat the
                // two-word noun phrase the note is actually about.
                var score = phrase.sumOf { wordScore[it] ?: 0.0 } / sqrt(phrase.size.toDouble())

                // Technical tokens are rarely incidental — someone saying "2NF"
                // or "PIPE_CONTROL" is naming the subject, not describing it.
                if (phrase.any { technicalToken.matches(it) }) score *= 1.6

                // Mildly prefer two- and three-word phrases: single words are
                // usually too broad to title a memory, and long runs are usually
                // a whole clause that slipped past the stop-word filter.
                score *= when (phrase.size) {
                    1 -> 0.75
                    2, 3 -> 1.15
                    else -> 0.95
                }

                Keyphrase(
                    text = phrase.joinToString(" "),
                    score = score,
                    wordCount = phrase.size
                )
            }
            .groupBy { it.text.lowercase(Locale.ROOT) }
            // Repetition is itself evidence, so fold duplicates by summing.
            .map { (_, group) ->
                group.first().copy(score = group.sumOf { it.score })
            }
            .sortedByDescending { it.score }

        return scored
    }

    /**
     * Produces a short, human-readable title for a passage.
     *
     * Falls back through progressively weaker signals so that *something*
     * sensible is always returned — an empty or stop-word-only utterance still
     * needs a name.
     */
    fun generateTitle(text: String): String {
        val phrases = extract(text)

        val best = phrases.firstOrNull()
        if (best != null && best.text.isNotBlank()) {
            // A single generic word makes a weak title; if a runner-up adds a
            // distinct idea, combine them the way a person naturally would.
            if (best.wordCount == 1) {
                val partner = phrases.drop(1).firstOrNull {
                    it.wordCount >= 1 && !it.text.equals(best.text, ignoreCase = true)
                }
                if (partner != null) {
                    return titleCase("${best.text} ${partner.text}")
                }
            }
            return titleCase(best.text)
        }

        // No keyphrase survived — fall back to the opening words of the passage.
        val words = text.split(phraseBoundary).filter { it.isNotBlank() }
        if (words.isEmpty()) return "Untitled Memory"
        return titleCase(words.take(MAX_TITLE_WORDS).joinToString(" "))
    }

    /**
     * Title-cases a phrase, keeping minor words lowercase unless they lead, and
     * leaving already-capitalised technical tokens (PIPE_CONTROL, 2NF) intact.
     */
    fun titleCase(phrase: String): String {
        val words = phrase.split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(MAX_TITLE_WORDS)

        if (words.isEmpty()) return "Untitled Memory"

        return words.mapIndexed { index, word ->
            when {
                // Preserve the author's own capitalisation for acronyms/identifiers.
                technicalToken.matches(word) || word.all { !it.isLetter() || it.isUpperCase() } -> word
                index > 0 && Lexicon.titleMinorWords.contains(word.lowercase(Locale.ROOT)) ->
                    word.lowercase(Locale.ROOT)
                else -> word.lowercase(Locale.ROOT)
                    .replaceFirstChar { it.titlecase(Locale.ROOT) }
            }
        }.joinToString(" ")
    }

    /**
     * Breaks the text into runs of consecutive non-stop words.
     */
    private fun candidatePhrases(text: String): List<List<String>> {
        val phrases = mutableListOf<List<String>>()

        // Sentence and clause punctuation always terminates a phrase, so a
        // keyphrase can never span "..., and" or a sentence boundary.
        for (clause in text.split(Regex("[.!?;:,\\n\\r()\\[\\]\"]+"))) {
            var current = mutableListOf<String>()

            for (rawWord in clause.split(phraseBoundary)) {
                val word = rawWord.trim('\'', '’', '-')
                if (word.isBlank()) continue

                // Verbs and state-adjectives break a phrase as surely as a stop
                // word does; without that, candidates run straight through the
                // predicate and produce clause fragments instead of subjects.
                val isNoise = Lexicon.isStopWord(word) ||
                    Lexicon.isPhraseBreaker(word) ||
                    word.length < 2 ||
                    word.all { it.isDigit() }

                if (isNoise) {
                    if (current.isNotEmpty()) {
                        phrases.add(current)
                        current = mutableListOf()
                    }
                } else {
                    current.add(word)
                    // Cap runaway phrases; anything longer is a clause, not a topic.
                    if (current.size == MAX_PHRASE_WORDS) {
                        phrases.add(current)
                        current = mutableListOf()
                    }
                }
            }
            if (current.isNotEmpty()) phrases.add(current)
        }

        return phrases
    }
}
