package com.naomi.app.ai.intelligence

import com.naomi.app.ai.speech.Transcript
import java.util.Locale

/** A word Naomi knows, flattened out of the database for matching. */
data class VocabularyTerm(
    val term: String,
    val normalized: String,
    val phoneticKey: String,
    val occurrences: Int = 0,
    val isUserConfirmed: Boolean = false
)

/** One word the normalizer rewrote, and why it was confident enough to. */
data class Change(
    val from: String,
    val to: String,
    val tokenIndex: Int,
    val confidence: Double
)

data class Correction(val text: String, val changes: List<Change> = emptyList()) {
    val changed: Boolean get() = changes.isNotEmpty()
}

/**
 * Repairs proper nouns the speech recogniser got wrong.
 *
 * A general-purpose recogniser has never heard of your project. Told "Nyx", it
 * returns the nearest word it does know — "next" — with complete confidence, and
 * every stage downstream inherits the mistake: the title, the topic it files
 * under, the search index. For an app whose only job is recall, that is the
 * worst place for an error to enter.
 *
 * ## The thing this must not do
 *
 * Rewriting the user's words is a destructive act. "I'll do this next week" must
 * survive untouched no matter how much Naomi knows about a project called Nyx.
 * So the design is deliberately asymmetric: it is built to refuse.
 *
 * 1. **A protected collocation is never overridden.** Checked before any
 *    scoring, so no accumulation of evidence can defeat it.
 * 2. **Sound is necessary, never sufficient.** A word must both sound like a
 *    known term *and* be corroborated — by context, or by the recogniser having
 *    offered the term itself.
 * 3. **Ordinary English words need adjacent evidence.** A mishearing sits inside
 *    the noun phrase it belongs to: "Nyx ring buffer" becomes "next ring
 *    buffer", with the giveaway right next to it. In "Next, I looked at the
 *    buffer", the related word is four tokens away and the sentence is fine as
 *    it stands. Distance is what separates the two, so common words get a
 *    two-token window and everything else gets six.
 * 4. **A clear winner or nothing.** Two plausible candidates means neither wins.
 *
 * The original text is never lost: callers keep the recogniser's output in
 * `notes.rawTranscript` and only the corrected form flows onward.
 */
object TranscriptNormalizer {

    /**
     * Total evidence a correction needs.
     *
     * Calibrated against the cases this must get right rather than picked round:
     * "next ring buffer" scores 0.75 with no help from the recogniser, and
     * "next GTT allocation" 0.65, while the discourse marker in "Next, I looked
     * at the buffer" reaches only 0.35.
     */
    const val MIN_CONFIDENCE: Double = 0.60

    /** How far the best candidate must beat the second — otherwise it is a coin toss. */
    const val MIN_MARGIN: Double = 0.15

    private const val W_ALTERNATIVE = 0.50
    private const val W_PHONETIC = 0.30
    private const val W_ORTHOGRAPHIC = 0.10
    private const val W_CONTEXT = 0.30
    private const val W_CONTEXT_EXTRA = 0.10
    private const val W_USER_CONFIRMED = 0.15
    private const val W_FAMILIARITY_MAX = 0.10

    private const val WINDOW_COMMON = 2
    private const val WINDOW_DISTINCTIVE = 6

    private const val ORTHOGRAPHIC_FLOOR = 0.6

    /**
     * How alike a word must sound before the recogniser's own alternatives count
     * for it.
     *
     * The n-best hypotheses are whole sentences, so "nyx appeared in hypothesis
     * two" says the recogniser considered that word for this audio — not for
     * *which* token. Without a floor that half-point would attach to every word
     * in the utterance, and a single related neighbour would be enough to
     * rewrite "the" into a project name. Set below [Phonetics.SOUNDS_ALIKE_THRESHOLD]
     * on purpose: the recogniser's opinion is allowed to lower the bar, not to
     * remove it.
     */
    private const val ALTERNATIVE_PHONETIC_FLOOR = 0.5

    /**
     * Phrases whose first word is doing ordinary English work.
     *
     * Every entry is a real collocation, not a guess: "next week", "next
     * Monday", "next step". If the word after the candidate is in its set, the
     * candidate is left alone unconditionally. This is the single most important
     * safeguard in the file — it is what makes "I'll do this next week" safe
     * even for a user whose most-used topic is called Nyx.
     */
    private val PROTECTED_FOLLOWERS: Map<String, Set<String>> = mapOf(
        "next" to setOf(
            "week", "weeks", "month", "months", "year", "years", "time", "times",
            "day", "days", "morning", "afternoon", "evening", "night", "weekend",
            "monday", "tuesday", "wednesday", "thursday", "friday", "saturday",
            "sunday", "step", "steps", "level", "stage", "phase", "round",
            "one", "two", "three", "few", "couple", "thing", "things", "item",
            "question", "slide", "page", "chapter", "section", "sprint",
            "quarter", "release", "version", "meeting", "up", "to", "door",
            "i", "we", "you", "he", "she", "they", "it", "is", "was", "will",
            "let", "on", "in", "the", "a", "an", "my", "our", "their"
        ),
        "last" to setOf(
            "week", "weeks", "month", "months", "year", "years", "time", "night",
            "monday", "tuesday", "wednesday", "thursday", "friday", "saturday",
            "sunday", "one", "thing", "step", "i", "we", "you", "he", "she", "they"
        ),
        "first" to setOf("time", "thing", "step", "one", "of", "i", "we", "you")
    )

    private val TOKEN = Regex("""\S+""")

    /**
     * @param topicPaths the user's topic tree as "Nyx > Graphics > Ring Buffer"
     *                   strings. This is where relatedness comes from: words
     *                   sharing a path are evidence for one another.
     */
    fun correct(
        transcript: Transcript,
        vocabulary: List<VocabularyTerm>,
        topicPaths: List<String> = emptyList()
    ): Correction {
        val text = transcript.text
        if (text.isBlank() || vocabulary.isEmpty()) return Correction(text)

        // Only single words can be corrected onto a single token. Multi-word
        // terms still matter — they feed relatedness below.
        val targets = vocabulary.filter { it.normalized.isNotBlank() && !it.normalized.contains(' ') }
        if (targets.isEmpty()) return Correction(text)

        val known = vocabulary.map { it.normalized }.toSet()
        val related = buildRelatedness(vocabulary, topicPaths)
        val alternativeWords = alternativeWordsFor(transcript)

        val tokens = TOKEN.findAll(text).toList()
        val cores = tokens.map { coreOf(it.value) }
        val normalizedCores = cores.map { it.word.lowercase(Locale.ROOT) }

        val changes = mutableListOf<Change>()
        val rebuilt = StringBuilder(text)
        // Rewrite right-to-left so earlier ranges stay valid.
        val edits = mutableListOf<Triple<IntRange, String, Change>>()

        for (i in tokens.indices) {
            val core = cores[i]
            val word = normalizedCores[i]
            if (word.isEmpty()) continue

            // Already the right word.
            if (word in known) continue
            // Nothing to gain from rewriting a number.
            if (word.none { it.isLetter() }) continue

            if (isProtected(word, normalizedCores.getOrNull(i + 1), core.trailing)) continue

            val isCommon = Lexicon.isCommonWord(word)
            val window = if (isCommon) WINDOW_COMMON else WINDOW_DISTINCTIVE
            val neighbours = neighbourhood(normalizedCores, i, window)

            val scored = targets.mapNotNull { term ->
                score(word, term, neighbours, related, alternativeWords)?.let { term to it }
            }.sortedByDescending { it.second }

            val best = scored.firstOrNull() ?: continue
            val runnerUp = scored.getOrNull(1)?.second ?: 0.0

            if (best.second < MIN_CONFIDENCE) continue
            if (best.second - runnerUp < MIN_MARGIN) continue

            val replacement = core.leading + best.first.term + core.possessive + core.trailing
            val change = Change(
                from = tokens[i].value,
                to = replacement,
                tokenIndex = i,
                confidence = best.second
            )
            edits += Triple(tokens[i].range, replacement, change)
            changes += change
        }

        if (edits.isEmpty()) return Correction(text)

        for ((range, replacement, _) in edits.asReversed()) {
            rebuilt.replace(range.first, range.last + 1, replacement)
        }
        return Correction(rebuilt.toString(), changes)
    }

    // ---- scoring ----

    /** Null when the candidate is not even eligible, which is most of them. */
    private fun score(
        word: String,
        term: VocabularyTerm,
        neighbours: Set<String>,
        related: Map<String, Set<String>>,
        alternativeWords: Set<String>
    ): Double? {
        val phoneticSimilarity = Phonetics.similarity(word, term.term)
        val soundsAlike = phoneticSimilarity >= Phonetics.SOUNDS_ALIKE_THRESHOLD
        val inAlternatives = term.normalized in alternativeWords &&
            phoneticSimilarity >= ALTERNATIVE_PHONETIC_FLOOR

        // The gate. Context alone must never rewrite a word: that would let a
        // sentence merely *about* a project rename an unrelated noun in it.
        if (!soundsAlike && !inAlternatives) return null

        var total = 0.0
        if (inAlternatives) total += W_ALTERNATIVE
        if (soundsAlike) total += W_PHONETIC

        if (TopicMatcher.calculateSimilarity(word, term.normalized) >= ORTHOGRAPHIC_FLOOR) {
            total += W_ORTHOGRAPHIC
        }

        val relatedHits = related[term.normalized]?.count { it in neighbours } ?: 0
        if (relatedHits >= 1) total += W_CONTEXT
        if (relatedHits >= 2) total += W_CONTEXT_EXTRA

        if (term.isUserConfirmed) total += W_USER_CONFIRMED
        total += (term.occurrences.coerceAtMost(10) / 10.0) * W_FAMILIARITY_MAX

        return total
    }

    private fun isProtected(word: String, follower: String?, trailing: String): Boolean {
        // "Next, I looked at..." — a comma after a common word marks it as a
        // discourse connective rather than the name of anything.
        if (trailing.contains(',') && Lexicon.isCommonWord(word)) return true

        val followers = PROTECTED_FOLLOWERS[word] ?: return false
        return follower != null && follower in followers
    }

    private fun neighbourhood(words: List<String>, index: Int, window: Int): Set<String> {
        val from = (index - window).coerceAtLeast(0)
        val to = (index + window).coerceAtMost(words.lastIndex)
        return (from..to).filter { it != index }.map { words[it] }.toSet()
    }

    /**
     * Which words are evidence for which.
     *
     * Two sources, both the user's own: words sharing a topic path ("Nyx >
     * Graphics > Ring Buffer" makes ring, buffer and graphics evidence for nyx),
     * and the words inside a multi-word term.
     */
    private fun buildRelatedness(
        vocabulary: List<VocabularyTerm>,
        topicPaths: List<String>
    ): Map<String, Set<String>> {
        val out = mutableMapOf<String, MutableSet<String>>()

        fun link(group: List<String>) {
            for (a in group) for (b in group) {
                if (a != b) out.getOrPut(a) { mutableSetOf() } += b
            }
        }

        for (path in topicPaths) {
            val words = path.split(">")
                .flatMap { it.trim().split(Regex("\\s+")) }
                .map { it.lowercase(Locale.ROOT).trim { c -> !c.isLetterOrDigit() } }
                .filter { it.isNotBlank() && !Lexicon.isStopWord(it) }
                .distinct()
            link(words)
        }

        for (term in vocabulary) {
            val parts = term.normalized.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (parts.size > 1) {
                link(parts)
                // The whole term is evidence for each of its words and back.
                for (p in parts) {
                    out.getOrPut(term.normalized) { mutableSetOf() } += p
                    out.getOrPut(p) { mutableSetOf() } += term.normalized
                }
            }
        }

        return out
    }

    /**
     * Words the recogniser offered in a losing hypothesis but did not choose.
     *
     * Union across the whole capture rather than per-utterance: the alternatives
     * are already scoped to the audio they came from, and the phonetic gate is
     * what decides *which* token a term could replace, so finer alignment buys
     * precision the rest of the pipeline does not need.
     */
    private fun alternativeWordsFor(transcript: Transcript): Set<String> {
        if (transcript.utterances.isEmpty()) return emptySet()

        val chosen = mutableSetOf<String>()
        val offered = mutableSetOf<String>()

        for (utterance in transcript.utterances) {
            utterance.chosen.split(Regex("\\s+")).forEach {
                chosen += it.lowercase(Locale.ROOT).trim { c -> !c.isLetterOrDigit() }
            }
            for (alternative in utterance.alternatives) {
                alternative.split(Regex("\\s+")).forEach {
                    offered += it.lowercase(Locale.ROOT).trim { c -> !c.isLetterOrDigit() }
                }
            }
        }
        return (offered - chosen).filter { it.isNotBlank() }.toSet()
    }

    // ---- tokens ----

    private data class Core(
        val leading: String,
        val word: String,
        val possessive: String,
        val trailing: String
    )

    /**
     * Splits a whitespace token into the word and the punctuation around it, so
     * a correction can replace the word and leave the rest exactly as spoken.
     */
    private fun coreOf(token: String): Core {
        val leading = token.takeWhile { !it.isLetterOrDigit() }
        val trailing = token.drop(leading.length).takeLastWhile { !it.isLetterOrDigit() }
        var word = token.substring(leading.length, token.length - trailing.length)

        // "Nyx's GTT" — the clitic belongs to the sentence, not the name, and
        // has to be put back after the term is substituted.
        var possessive = ""
        for (suffix in listOf("'s", "’s", "'S")) {
            if (word.endsWith(suffix)) {
                possessive = suffix
                word = word.dropLast(suffix.length)
                break
            }
        }
        return Core(leading, word, possessive, trailing)
    }
}
