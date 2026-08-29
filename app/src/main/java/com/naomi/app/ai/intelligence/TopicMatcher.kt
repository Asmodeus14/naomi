package com.naomi.app.ai.intelligence

import com.naomi.app.data.database.entities.TopicEntity
import kotlin.math.max
import kotlin.math.min

object TopicMatcher {

    /**
     * Normalizes a topic string by removing noise words, punctuation, and casing.
     * Examples:
     * - "Project Nyx" -> "nyx"
     * - "Nyx OS" -> "nyx"
     * - "DBMS Lecture" -> "dbms"
     * - "Ring Buffer issue" -> "ring buffer"
     */
    fun normalize(raw: String): String {
        var clean = raw.trim().lowercase()
        // Remove common prefixes and suffixes
        val prefixes = listOf("project ", "the ", "about ", "on ")
        for (prefix in prefixes) {
            if (clean.startsWith(prefix)) {
                clean = clean.removePrefix(prefix)
            }
        }
        val suffixes = listOf(" project", " os", " system", " lecture", " topic", " issue", " bug", " note")
        for (suffix in suffixes) {
            if (clean.endsWith(suffix) && clean.length > suffix.length) {
                clean = clean.removeSuffix(suffix)
            }
        }
        // Remove punctuation
        clean = clean.replace(Regex("[^a-z0-9\\s]"), "").replace(Regex("\\s+"), " ").trim()
        return clean
    }

    /**
     * Default similarity required to treat two topic names as the same subject.
     *
     * This is deliberately strict. A loose threshold is far more damaging than a
     * strict one: merging two distinct topics silently destroys the user's
     * organisation and is nearly impossible to notice, whereas failing to merge
     * leaves a visible duplicate they can fix in one tap.
     */
    const val DEFAULT_THRESHOLD: Double = 0.82

    /**
     * Finds the best matching existing topic using fuzzy and token matching.
     * Returns null when no candidate clears [threshold].
     */
    fun findBestMatch(
        candidateName: String,
        existingTopics: List<TopicEntity>,
        threshold: Double = DEFAULT_THRESHOLD
    ): TopicEntity? {
        val normCandidate = normalize(candidateName)
        if (normCandidate.isBlank() || existingTopics.isEmpty()) return null

        var bestTopic: TopicEntity? = null
        var highestScore = 0.0

        for (topic in existingTopics) {
            val normTopic = normalize(topic.name)
            // Exact normalized match
            if (normTopic == normCandidate) {
                return topic
            }

            // Substring or token overlap match
            val score = calculateSimilarity(normCandidate, normTopic)
            if (score > highestScore && score >= threshold) {
                highestScore = score
                bestTopic = topic
            }
        }

        return bestTopic
    }

    /**
     * Calculates combined Levenshtein similarity and token Jaccard similarity.
     */
    fun calculateSimilarity(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0

        // Token Jaccard
        val tokens1 = s1.split(" ").filter { it.isNotBlank() }.toSet()
        val tokens2 = s2.split(" ").filter { it.isNotBlank() }.toSet()
        val intersection = tokens1.intersect(tokens2).size
        val union = tokens1.union(tokens2).size
        val jaccard = if (union > 0) intersection.toDouble() / union else 0.0

        // Containment: one name is entirely inside the other.
        //
        // Jaccard alone cannot express this. "tomato seedlings" against
        // "tomato seedlings look taller" scores 0.5 and fails the threshold, so
        // the second memory about the same plants minted a duplicate root topic
        // — the exact failure the hierarchy exists to prevent.
        //
        // The coverage guard is what keeps this honest: the contained name must
        // account for at least half the longer one, so "Soil" does not swallow
        // "Balcony Soil Drainage Problem" on the strength of a single word.
        val containment = containmentScore(tokens1, tokens2)

        // Levenshtein ratio
        val maxLen = max(s1.length, s2.length)
        val distance = levenshteinDistance(s1, s2)
        val levRatio = 1.0 - (distance.toDouble() / maxLen)

        // Edit distance is meaningless on short strings: "2nf" and "3nf" differ
        // by one character out of three, which scores 0.67 and would merge two
        // completely different concepts. Allow roughly one edit per five
        // characters and fall back to token overlap when that budget is blown.
        val editBudget = maxLen / 5
        val levUsable = distance <= editBudget

        val fuzzy = if (levUsable) max(jaccard, levRatio) else jaccard
        return max(fuzzy, containment)
    }

    /**
     * How completely the shorter token set sits inside the longer one.
     *
     * Returns 0 unless containment is total, so a partial overlap falls back to
     * Jaccard rather than being flattered by this signal.
     */
    private fun containmentScore(tokens1: Set<String>, tokens2: Set<String>): Double {
        if (tokens1.isEmpty() || tokens2.isEmpty()) return 0.0

        val (smaller, larger) =
            if (tokens1.size <= tokens2.size) tokens1 to tokens2 else tokens2 to tokens1

        if (!larger.containsAll(smaller)) return 0.0

        // A single shared word inside a much longer name is a coincidence, not
        // the same subject.
        val coverage = smaller.size.toDouble() / larger.size.toDouble()
        return if (coverage >= 0.5) 1.0 else 0.0
    }

    private fun levenshteinDistance(lhs: CharSequence, rhs: CharSequence): Int {
        var lhsLength = lhs.length
        var rhsLength = rhs.length

        var cost = Array(lhsLength + 1) { IntArray(rhsLength + 1) }

        for (i in 0..lhsLength) cost[i][0] = i
        for (j in 0..rhsLength) cost[0][j] = j

        for (i in 1..lhsLength) {
            for (j in 1..rhsLength) {
                val match = if (lhs[i - 1] == rhs[j - 1]) 0 else 1
                cost[i][j] = min(
                    min(cost[i - 1][j] + 1, cost[i][j - 1] + 1),
                    cost[i - 1][j - 1] + match
                )
            }
        }
        return cost[lhsLength][rhsLength]
    }
}
