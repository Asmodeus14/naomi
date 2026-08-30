package com.naomi.app.ai.intelligence

import com.naomi.app.data.database.entities.TaskEntity
import com.naomi.app.domain.model.ExtractedEntity
import com.naomi.app.domain.model.ExtractedKnowledge
import com.naomi.app.domain.model.ExtractedTask
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

/**
 * Turns a raw transcript into structured knowledge, entirely on the device.
 *
 * Everything here is subject-matter neutral. Earlier versions of this file
 * branched on specific domains, which meant the app only produced sensible
 * output for the two topics it had been written around and produced nonsense
 * for everyone else. Extraction now works from sentence structure alone, so it
 * degrades gracefully on unfamiliar subjects instead of confidently inventing.
 *
 * Topic placement is *not* done here — it depends on what the user has already
 * recorded, so it belongs where the database is. See [TopicResolver].
 */
object LocalIntelligenceEngine {

    /**
     * Splits a recording into separate coherent thoughts when the speaker
     * clearly changes subject.
     *
     * Splitting happens in text order. Iterating the marker list instead means
     * whichever marker appears first *in the list* wins, so a recording that
     * says "on another note" before "also I need to" gets cut in the wrong place
     * and the leading segment keeps two unrelated subjects.
     */
    fun segmentSpeech(transcript: String): List<String> {
        val clean = transcript.trim()
        if (clean.isBlank()) return emptyList()

        val lower = clean.lowercase(Locale.ROOT)

        // Collect every marker occurrence, then sort by position in the text.
        val cutPoints = Lexicon.topicSwitchMarkers
            .flatMap { marker ->
                Regex(Regex.escape(marker)).findAll(lower).map { it.range.first }
            }
            // A marker in the first few words is an opener, not a pivot —
            // there is no preceding thought for it to separate.
            .filter { it > 12 }
            .distinct()
            .sorted()

        if (cutPoints.isEmpty()) return listOf(clean)

        val segments = mutableListOf<String>()
        var start = 0
        for (cut in cutPoints) {
            if (cut <= start) continue
            val piece = clean.substring(start, cut).trim().trimEnd('.', ',', ';')
            if (piece.isNotBlank()) segments.add(piece)
            start = cut
        }
        val tail = clean.substring(start).trim()
        if (tail.isNotBlank()) segments.add(tail)

        return segments.ifEmpty { listOf(clean) }
    }

    /**
     * Analyses one coherent thought.
     */
    fun analyze(
        transcript: String,
        now: LocalDateTime = LocalDateTime.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): ExtractedKnowledge {
        val text = transcript.trim()

        val correction = CorrectionDetector.analyze(text)
        val keyphrases = KeyphraseExtractor.extract(text)
        val title = KeyphraseExtractor.generateTitle(text)

        return ExtractedKnowledge(
            title = title,
            summary = generateSummary(text, correction),
            keyphrases = keyphrases,
            idea = extractIdea(text),
            decision = extractDecision(text),
            tasks = extractActions(text, title, now, zone),
            entities = extractEntities(text),
            isCorrection = correction.isCorrection
        )
    }

    /** An hour, which is what a calendar assumes when nobody says otherwise. */
    private const val DEFAULT_EVENT_MINUTES = 60

    /**
     * Everything actionable in one thought, labelled with what to do about it.
     *
     * [TaskExtractor] finds work the speaker committed to. [ActionClassifier]
     * decides whether the sentence as a whole was really a request to be
     * interrupted or an appointment — the two outcomes that reach outside the
     * app, and so the two that have to be right.
     */
    private fun extractActions(
        text: String,
        title: String,
        now: LocalDateTime,
        zone: ZoneId
    ): List<ExtractedTask> {
        val extracted = TaskExtractor.extract(text, now, zone)
        val parsed = TemporalParser.parse(text, now, zone)

        return when (ActionClassifier.classify(text, parsed)) {
            ActionIntent.REMINDER ->
                // "Wake me at 7" carries no task trigger, so there may be
                // nothing to relabel — in which case the sentence is itself the
                // reminder.
                if (extracted.isEmpty()) {
                    listOf(
                        ExtractedTask(
                            title = title,
                            deadline = parsed?.displayText,
                            dueAt = parsed?.dueAt,
                            kind = TaskEntity.KIND_REMINDER,
                            hasExactTime = parsed?.hasExplicitTime == true
                        )
                    )
                } else {
                    extracted.map { it.copy(kind = TaskEntity.KIND_REMINDER) }
                }

            ActionIntent.EVENT -> {
                val start = parsed?.dueAt
                if (start == null) {
                    extracted
                } else {
                    val minutes = TemporalParser.parseDuration(text) ?: DEFAULT_EVENT_MINUTES
                    listOf(
                        ExtractedTask(
                            title = title,
                            deadline = parsed.displayText,
                            dueAt = start,
                            kind = TaskEntity.KIND_EVENT,
                            endAt = start + minutes * 60_000L,
                            hasExactTime = parsed.hasExplicitTime
                        )
                    )
                }
            }

            // A task list is the right home for both, and a memory with nothing
            // actionable in it simply has no rows.
            ActionIntent.TASK, ActionIntent.MEMORY -> extracted
        }
    }

    /**
     * Rewrites the transcript as a readable statement by dropping the
     * conversational run-up. The full transcript is kept separately, so nothing
     * is lost by making the summary terse.
     */
    private fun generateSummary(text: String, correction: CorrectionResult): String {
        if (correction.isCorrection) {
            val subject = correction.correctedSubject
            val assertion = correction.newAssertion
            if (subject != null && assertion != null) {
                return "Correction: not $subject — $assertion."
            }
        }

        var clean = text.trim()

        // Peel off stacked openers: "Hey Naomi, so basically, ..."
        var changed = true
        while (changed) {
            changed = false
            for (opener in Lexicon.summaryOpeners) {
                val pattern = Regex("^${Regex.escape(opener)}\\b[\\s,.:;-]*", RegexOption.IGNORE_CASE)
                val stripped = pattern.replace(clean, "")
                if (stripped != clean) {
                    clean = stripped.trim()
                    changed = true
                }
            }
        }

        if (clean.isBlank()) clean = text.trim()
        if (clean.isBlank()) return ""

        clean = clean.replaceFirstChar { it.titlecase(Locale.ROOT) }
        if (!clean.endsWith(".") && !clean.endsWith("!") && !clean.endsWith("?")) {
            clean += "."
        }
        return clean
    }

    /**
     * Surfaces a speculative thought, if the speaker flagged one.
     *
     * Returns the speaker's own words rather than a generated sentence. A
     * memory assistant that paraphrases is one the user has to double-check.
     */
    private fun extractIdea(text: String): String? {
        for (marker in Lexicon.ideaMarkers) {
            val pattern = Regex(
                "\\b${Regex.escape(marker)}\\b\\s+(.+?)(?=[.!?\\n]|$)",
                RegexOption.IGNORE_CASE
            )
            val match = pattern.find(text) ?: continue
            val body = match.groupValues[1].trim()
            if (body.length < 8) continue
            return body.replaceFirstChar { it.titlecase(Locale.ROOT) }.trimEnd(',', ';') + "."
        }
        return null
    }

    /** Surfaces a settled conclusion, if the speaker flagged one. */
    private fun extractDecision(text: String): String? {
        for (marker in Lexicon.decisionMarkers) {
            val pattern = Regex(
                "\\b${Regex.escape(marker)}\\b\\s+(.+?)(?=[.!?\\n]|$)",
                RegexOption.IGNORE_CASE
            )
            val match = pattern.find(text) ?: continue
            val body = match.groupValues[1].trim()
            if (body.length < 4) continue
            return body.replaceFirstChar { it.titlecase(Locale.ROOT) }.trimEnd(',', ';') + "."
        }
        return null
    }

    /** Identifiers like PIPE_CONTROL, 2NF, IPv6, H264 — named, not described. */
    private val technicalToken = Regex("""\b(?=\w*[\p{N}_])(?=\w*\p{L})\w{2,}\b|\b\p{Lu}{2,}\b""")

    /** "Rahul will handle the backend", "Sam is taking notes". */
    private val assignmentPattern = Regex(
        """\b(\p{Lu}\p{Ll}+)\s+(?:will handle|handles|is handling|will take|is taking|owns|will own|is doing|will do)\s+(?:the\s+)?([\p{L}\p{N}_-]+)"""
    )

    /**
     * Extracts named things worth cross-referencing later.
     *
     * Detection is structural — capitalisation and character class — rather than
     * a fixed vocabulary, so it finds the nouns that matter in any field.
     */
    private fun extractEntities(text: String): List<ExtractedEntity> {
        val entities = mutableListOf<ExtractedEntity>()

        for (match in assignmentPattern.findAll(text)) {
            entities.add(
                ExtractedEntity(
                    name = match.groupValues[1],
                    type = "PERSON",
                    detail = match.groupValues[2]
                        .replaceFirstChar { it.titlecase(Locale.ROOT) }
                )
            )
        }

        for (match in technicalToken.findAll(text)) {
            val token = match.value
            if (token.length < 2) continue
            if (Lexicon.isStopWord(token)) continue
            // A lone "I" or an all-digit run is not an identifier.
            if (token.none { it.isLetter() }) continue
            entities.add(ExtractedEntity(name = token, type = "TERM"))
        }

        return entities
            .distinctBy { it.name.lowercase(Locale.ROOT) }
            .take(12)
    }
}
