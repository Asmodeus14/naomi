package com.naomi.app.ai.intelligence

import java.util.Locale

/**
 * Turns a word into a key describing roughly how it sounds, so that words which
 * a speech recogniser is liable to confuse can be recognised as confusable.
 *
 * This exists because the mistake being corrected is an *acoustic* one, and
 * spelling distance is the wrong instrument for measuring it. "Nyx" and "next"
 * share two letters out of four — [TopicMatcher.calculateSimilarity] scores them
 * around 0.5, nowhere near its 0.82 threshold, so the existing matcher is blind
 * to exactly the confusion that motivates this whole feature. Reduced to sound,
 * they are `NKS` and `NKST`: one character apart, 0.75.
 *
 * The algorithm is Metaphone, trimmed to the rules that earn their place on
 * English words and identifiers. It is deliberately not Double Metaphone — the
 * second key mostly matters for names of non-English origin, and every extra
 * rule is another way to map two genuinely different words together, which here
 * means silently rewriting something the user actually said.
 */
object Phonetics {

    private val VOWELS = setOf('A', 'E', 'I', 'O', 'U')

    /**
     * The sound-key for [word]. Returns "" for anything with no letters in it,
     * which callers must treat as "no opinion" rather than as a match.
     */
    fun key(word: String): String {
        // The possessive clitic is grammar, not part of the name: "Nyx's GTT"
        // is a sentence about Nyx, and matching it against the term "Nyx" must
        // not be defeated by an apostrophe. Plurals are left alone, because
        // "buffers" really is a different word from "buffer".
        val bare = word
            .replace("’", "'")
            .removeSuffix("'s").removeSuffix("'S").removeSuffix("s'")
            .replace("'", "")

        val letters = bare.uppercase(Locale.ROOT).filter { it in 'A'..'Z' }
        if (letters.isEmpty()) return ""

        // Collapse doubled letters; a repeated letter is one sound. "TT" -> "T".
        val w = buildString {
            for (c in letters) if (isEmpty() || last() != c) append(c)
        }

        val out = StringBuilder()
        var i = 0

        // Silent leading pairs: the first letter of "knee", "gnome", "wrist"
        // and "pneumatic" is not pronounced.
        when {
            w.startsWith("KN") || w.startsWith("GN") ||
                w.startsWith("PN") || w.startsWith("WR") -> i = 1
            w.startsWith("X") -> { out.append('S'); i = 1 }
            w.startsWith("WH") -> { out.append('W'); i = 2 }
        }

        while (i < w.length) {
            val c = w[i]
            val next = w.getOrNull(i + 1)
            val prev = w.getOrNull(i - 1)

            when (c) {
                in VOWELS -> if (out.isEmpty()) out.append(c)

                'B' -> if (!(i == w.lastIndex && prev == 'M')) out.append('B')

                'C' -> when {
                    next == 'I' && w.getOrNull(i + 2) == 'A' -> out.append('X')
                    next == 'H' -> out.append('X')
                    next == 'I' || next == 'E' || next == 'Y' -> out.append('S')
                    else -> out.append('K')
                }

                'D' -> if (next == 'G' && w.getOrNull(i + 2) in setOf('E', 'Y', 'I')) {
                    out.append('J'); i++
                } else out.append('T')

                'G' -> when {
                    // "night", "though" — silent before a consonant or word end.
                    next == 'H' && (i + 2 >= w.length || w[i + 2] !in VOWELS) -> i++
                    next == 'N' -> {}
                    next == 'I' || next == 'E' || next == 'Y' -> out.append('J')
                    else -> out.append('K')
                }

                // Pronounced only between a vowel and a vowel.
                'H' -> if (prev != null && prev in VOWELS && next != null && next in VOWELS) out.append('H')

                'K' -> if (prev != 'C') out.append('K')

                'P' -> if (next == 'H') { out.append('F'); i++ } else out.append('P')

                'Q' -> out.append('K')

                'S' -> when {
                    next == 'H' -> { out.append('X'); i++ }
                    next == 'I' && w.getOrNull(i + 2) in setOf('O', 'A') -> out.append('X')
                    else -> out.append('S')
                }

                'T' -> when {
                    next == 'I' && w.getOrNull(i + 2) in setOf('O', 'A') -> out.append('X')
                    next == 'H' -> { out.append('0'); i++ }
                    else -> out.append('T')
                }

                'V' -> out.append('F')

                // W and Y are only sounds when they lead into a vowel.
                'W', 'Y' -> if (next != null && next in VOWELS) out.append(c)

                'X' -> out.append("KS")

                'Z' -> out.append('S')

                else -> out.append(c) // F J L M N R
            }
            i++
        }

        return out.toString()
    }

    /**
     * How alike two words sound, 0.0 to 1.0.
     *
     * Edit distance over the sound-keys rather than equality of them, because
     * the confusions that matter most are near-misses: a recogniser that hears
     * "Nyx" as "next" has added a sound, not chosen a different word. Requiring
     * identical keys would reject precisely the case this is for.
     */
    fun similarity(a: String, b: String): Double {
        val ka = key(a)
        val kb = key(b)
        // An empty key means "no letters to judge", which is not similarity.
        if (ka.isEmpty() || kb.isEmpty()) return 0.0
        // A very short key has thrown away most of the word, so a match on it is
        // coincidence rather than evidence. "Vue" and "few" both reduce to "F",
        // which would otherwise score a perfect 1.0 and let a vocabulary term
        // rewrite an ordinary English word. Found by reading the seeded terms
        // off a real device rather than by reasoning about the algorithm.
        if (ka.length < MIN_USABLE_KEY || kb.length < MIN_USABLE_KEY) return 0.0
        if (ka == kb) return 1.0

        val distance = editDistance(ka, kb)
        val longest = maxOf(ka.length, kb.length)
        return (1.0 - distance.toDouble() / longest).coerceIn(0.0, 1.0)
    }

    /** Whether two words are close enough in sound to be a plausible mishearing. */
    fun soundsAlike(a: String, b: String, threshold: Double = SOUNDS_ALIKE_THRESHOLD): Boolean =
        similarity(a, b) >= threshold

    /**
     * 0.7 lets "Nyx"/"next" (0.75) through while holding out "Nyx"/"nix"'s
     * neighbours that merely start the same. Raising it past 0.8 requires
     * identical keys for short words and defeats the purpose.
     */
    const val SOUNDS_ALIKE_THRESHOLD: Double = 0.7

    /**
     * Below this, a key is too lossy to draw a conclusion from.
     *
     * Three is where the threshold above starts meaning something: at three
     * characters a single edit scores 0.667 and is already rejected, so a
     * three-character key must match exactly — which for a real word it only
     * does when the words genuinely sound the same. At one or two characters,
     * exact matches are common and meaningless.
     */
    const val MIN_USABLE_KEY: Int = 3

    private fun editDistance(s1: String, s2: String): Int {
        if (s1 == s2) return 0
        if (s1.isEmpty()) return s2.length
        if (s2.isEmpty()) return s1.length

        var previous = IntArray(s2.length + 1) { it }
        var current = IntArray(s2.length + 1)

        for (i in 1..s1.length) {
            current[0] = i
            for (j in 1..s2.length) {
                val substitution = previous[j - 1] + if (s1[i - 1] == s2[j - 1]) 0 else 1
                current[j] = minOf(current[j - 1] + 1, previous[j] + 1, substitution)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[s2.length]
    }
}
