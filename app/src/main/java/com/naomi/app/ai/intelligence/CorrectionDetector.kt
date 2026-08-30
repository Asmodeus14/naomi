package com.naomi.app.ai.intelligence

data class CorrectionResult(
    val isCorrection: Boolean,
    val correctedSubject: String? = null,
    val newAssertion: String? = null
)

/** The user telling Naomi how to hear a word: "it's Nyx, not next". */
data class SpellingCorrection(val correct: String, val misheard: String)

object CorrectionDetector {

    private val CORRECTION_TRIGGERS = listOf(
        "actually",
        "i checked again",
        "i checked it",
        "the bug isn't",
        "the bug is not",
        "not in the",
        "correction:",
        "correction",
        "scratch that",
        "turns out",
        "instead of",
        "mistake",
        "my bad"
    )

    fun analyze(text: String): CorrectionResult {
        val lower = text.lowercase()
        val hasTrigger = CORRECTION_TRIGGERS.any { lower.contains(it) }

        if (!hasTrigger) {
            return CorrectionResult(isCorrection = false)
        }

        // Pattern: "The bug isn't in X. It's in Y" or "Actually, ... not in X, it's in Y"
        var subject: String? = null
        var newFact: String? = null

        val notInRegex = Regex("""(?:isn't in|is not in|not in)\s+([a-zA-Z0-9_\-\s]+?)(?:\.|\,|it\'s in|it is in|but in)""", RegexOption.IGNORE_CASE)
        val notInMatch = notInRegex.find(text)
        if (notInMatch != null) {
            subject = notInMatch.groupValues[1].trim()
            if (subject.startsWith("the ", ignoreCase = true)) {
                subject = subject.substring(4).trim()
            }
        }

        val itsInRegex = Regex("""(?:it\'s in|it is in|but in|turns out to be in)\s+([a-zA-Z0-9_\-\s]+?)(?:\.|\,|$)""", RegexOption.IGNORE_CASE)
        val itsInMatch = itsInRegex.find(text)
        if (itsInMatch != null) {
            newFact = itsInMatch.groupValues[1].trim()
            if (newFact.startsWith("the ", ignoreCase = true)) {
                newFact = newFact.substring(4).trim()
            }
        }

        return CorrectionResult(
            isCorrection = true,
            correctedSubject = subject,
            newAssertion = newFact
        )
    }

    /**
     * "It's Nyx, not next."
     *
     * Naomi has no keyboard, so speech is the only channel a user has for
     * teaching it a word. This is separate from [analyze] because the two are
     * different acts: that one revises a *fact* the memory records, this one
     * revises how a *sound* is written down, and conflating them would file a
     * vocabulary lesson as something worth remembering.
     */
    fun detectSpelling(text: String): SpellingCorrection? {
        val match = SPELLING_PATTERN.find(text) ?: return null
        val correct = match.groupValues[1].trim()
        val misheard = match.groupValues[2].trim()

        if (correct.equals(misheard, ignoreCase = true)) return null
        // Learning a word costs nothing; learning an ordinary English word costs
        // a great deal, because it becomes something other words can be
        // rewritten into.
        if (Lexicon.isCommonWord(correct) || correct.length < 3) return null

        // The two words must be confusable by ear. Without this, "it's Tuesday,
        // not Wednesday" — a perfectly ordinary sentence about a schedule —
        // would be read as a pronunciation lesson.
        if (!Phonetics.soundsAlike(correct, misheard)) return null

        return SpellingCorrection(correct = correct, misheard = misheard)
    }

    /**
     * Anchored on an explicit "X, not Y" contrast. A looser pattern would catch
     * every sentence containing "not", and each false positive permanently adds
     * a word Naomi will rewrite other words into.
     */
    private val SPELLING_PATTERN = Regex(
        """\b(?:it'?s|its|that'?s|i said|the word is|the name is|it should be|it'?s spelled|spelled)\s+""" +
            """([A-Za-z][A-Za-z0-9'\-]*)\s*,?\s+not\s+([A-Za-z][A-Za-z0-9'\-]*)""",
        RegexOption.IGNORE_CASE
    )
}
