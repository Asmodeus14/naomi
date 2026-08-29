package com.naomi.app.ai.intelligence

import com.naomi.app.data.database.entities.TopicEntity
import com.naomi.app.domain.model.Keyphrase
import java.util.Locale

/**
 * Decides where a new memory belongs in the user's topic tree.
 *
 * The important property is that the tree grows from the user's *own* vocabulary
 * rather than a built-in taxonomy. The first time someone mentions a subject it
 * becomes a root topic; the next time they mention it alongside something more
 * specific, that specific thing is filed underneath. Nobody has to name a topic
 * or pick from a list, and Naomi never invents a category the user didn't say.
 *
 * When nothing is confident enough, the memory goes to [INBOX] rather than
 * minting a junk topic — an inbox the user can triage is far better than a root
 * list polluted with entries like "Remember" and "Today".
 */
object TopicResolver {

    const val INBOX = "Inbox"

    /**
     * @param path            root-to-leaf topic names, e.g. ["Nyx", "Ring Buffer"]
     * @param confidence      0..1, how sure we are this is the right home
     * @param matchedExisting whether this attached to a topic the user already had
     */
    data class TopicAssignment(
        val path: List<String>,
        val confidence: Float,
        val matchedExisting: Boolean
    )

    /** Below this, we file to the inbox instead of guessing. */
    private const val MIN_CONFIDENCE = 0.45f

    /** How many candidate phrases to consider as potential topics. */
    private const val CANDIDATES_CONSIDERED = 5

    /**
     * @param keyphrases    ranked output of [KeyphraseExtractor.extract]
     * @param existingTopics every topic currently in the database
     */
    fun resolve(
        keyphrases: List<Keyphrase>,
        existingTopics: List<TopicEntity>
    ): TopicAssignment {
        val candidates = keyphrases.take(CANDIDATES_CONSIDERED)
        if (candidates.isEmpty()) {
            return TopicAssignment(listOf(INBOX), 0f, matchedExisting = false)
        }

        val byId = existingTopics.associateBy { it.id }

        // Prefer attaching to something the user already talks about. Scanning
        // all candidates rather than just the top one matters: the strongest
        // phrase is usually the most *specific* detail, while the phrase that
        // matches an existing topic is usually the broader subject it lives under.
        var anchor: TopicEntity? = null
        var anchorCandidate: Keyphrase? = null

        for (candidate in candidates) {
            val match = TopicMatcher.findBestMatch(candidate.text, existingTopics)
            if (match != null) {
                anchor = match
                anchorCandidate = candidate
                break
            }
        }

        if (anchor != null) {
            val ancestry = ancestryOf(anchor, byId)

            // If another strong phrase names something more specific than the
            // topic we matched, nest it underneath — this is how depth appears
            // over time without the user ever building the tree by hand.
            //
            // The bar is a multi-word phrase, or a single word that outscores
            // the phrase we anchored on. Without it any stray noun in the
            // sentence became a subtopic, and the tree grew a level per memory
            // instead of a level per genuine distinction.
            val anchorScore = anchorCandidate?.score ?: 0.0
            val child = candidates.firstOrNull { candidate ->
                candidate !== anchorCandidate &&
                    (candidate.wordCount >= 2 || candidate.score >= anchorScore) &&
                    !isRedundantWith(candidate.text, ancestry) &&
                    TopicMatcher.findBestMatch(candidate.text, existingTopics) == null
            }

            val path = if (child != null) {
                ancestry + KeyphraseExtractor.titleCase(child.text)
            } else {
                ancestry
            }

            return TopicAssignment(
                path = path,
                confidence = if (child != null) 0.85f else 0.95f,
                matchedExisting = true
            )
        }

        // Nothing matched: this is a genuinely new subject.
        val primary = candidates.first()

        // A one-word phrase that barely outscored the noise is not a topic.
        val strength = normalizedStrength(primary, candidates)
        if (strength < MIN_CONFIDENCE) {
            return TopicAssignment(listOf(INBOX), strength, matchedExisting = false)
        }

        val root = KeyphraseExtractor.titleCase(primary.text)
        val secondary = candidates.drop(1).firstOrNull {
            !isRedundantWith(it.text, listOf(root)) && it.score >= primary.score * 0.55
        }

        val path = if (secondary != null) {
            listOf(root, KeyphraseExtractor.titleCase(secondary.text))
        } else {
            listOf(root)
        }

        return TopicAssignment(path = path, confidence = strength, matchedExisting = false)
    }

    /**
     * Walks parent links to produce the root-to-leaf name path for [topic].
     */
    private fun ancestryOf(topic: TopicEntity, byId: Map<Long, TopicEntity>): List<String> {
        val chain = mutableListOf<String>()
        var current: TopicEntity? = topic
        val guard = mutableSetOf<Long>()

        while (current != null && guard.add(current.id)) {
            chain.add(current.name)
            current = current.parentId?.let { byId[it] }
        }
        return chain.reversed()
    }

    /**
     * True if [text] merely restates something already in the path, so we don't
     * create "Nyx > Nyx" or "Ring Buffer > Buffer".
     */
    private fun isRedundantWith(text: String, path: List<String>): Boolean {
        val normalized = TopicMatcher.normalize(text)
        if (normalized.isBlank()) return true

        return path.any { existing ->
            val other = TopicMatcher.normalize(existing)
            other == normalized ||
                other.contains(normalized) ||
                normalized.contains(other)
        }
    }

    /**
     * Scores the top phrase against the field. A phrase that clearly dominates
     * is a confident topic; one barely separated from the pack is not.
     */
    private fun normalizedStrength(
        primary: Keyphrase,
        candidates: List<Keyphrase>
    ): Float {
        if (primary.text.isBlank()) return 0f

        val total = candidates.sumOf { it.score }
        if (total <= 0.0) return 0f

        val share = (primary.score / total).toFloat()

        // Multi-word phrases are far more likely to be a real subject than a
        // single word that survived stop-word filtering by accident.
        val specificity = when (primary.wordCount) {
            1 -> 0.7f
            2 -> 1.0f
            else -> 1.1f
        }

        return (share * specificity * 1.8f).coerceIn(0f, 1f)
    }

    /** Names that should never become topics regardless of score. */
    fun isUsableTopicName(name: String): Boolean {
        val normalized = TopicMatcher.normalize(name)
        return normalized.isNotBlank() &&
            normalized.length >= 2 &&
            !normalized.all { it.isDigit() } &&
            !Lexicon.isStopWord(normalized)
    }

    fun titleCaseName(raw: String): String =
        raw.trim().split(Regex("\\s+")).joinToString(" ") { word ->
            word.replaceFirstChar { it.titlecase(Locale.ROOT) }
        }
}
