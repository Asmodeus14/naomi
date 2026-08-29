package com.naomi.app.ai.intelligence

data class CorrectionResult(
    val isCorrection: Boolean,
    val correctedSubject: String? = null,
    val newAssertion: String? = null
)

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
}
