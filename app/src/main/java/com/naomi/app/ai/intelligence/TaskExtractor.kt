package com.naomi.app.ai.intelligence

import com.naomi.app.domain.model.ExtractedTask
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

/**
 * Pulls actionable items out of dictated speech.
 *
 * Scope is deliberately narrow: only clauses with an explicit intent marker
 * ("I need to", "remind me to", "don't forget to") become tasks. Inferring
 * intent from bare verbs produces a task list full of things the user merely
 * mentioned, which is worse than missing a few.
 */
object TaskExtractor {

    private const val MIN_TITLE_LENGTH = 3
    private const val MAX_TITLE_WORDS = 10

    /**
     * Each pattern captures the action in group 1 and stops at a clause
     * boundary or a temporal preposition, so the date never leaks into the title.
     */
    private val patterns: List<Regex> = listOf(
        Regex(
            """\b(?:remind me to|don't forget to|dont forget to|make sure to|remember to)\s+(.+?)(?=[.!?;,\n]|\s+\b(?:by|before|on|at|next|this|tomorrow|today|tonight)\b|$)""",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            """\b(?:i|we)\s+(?:need to|have to|must|should)\s+(.+?)(?=[.!?;,\n]|\s+\b(?:by|before|on|at|next|this|tomorrow|today|tonight)\b|$)""",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            """\b(?:i'll|i will|we'll|we will)\s+(.+?)(?=[.!?;,\n]|\s+\b(?:by|before|on|at|next|this|tomorrow|today|tonight)\b|$)""",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            """\b(?:need to|have to|got to|gotta)\s+(.+?)(?=[.!?;,\n]|\s+\b(?:by|before|on|at|next|this|tomorrow|today|tonight)\b|$)""",
            RegexOption.IGNORE_CASE
        )
    )

    /**
     * Extracts every distinct task in [text].
     *
     * A date is attached per-clause rather than per-transcript: "call Sam
     * tomorrow and file taxes on Friday" yields two tasks with two dates, where
     * a single transcript-wide deadline would stamp both with whichever came first.
     */
    fun extract(
        text: String,
        now: LocalDateTime = LocalDateTime.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): List<ExtractedTask> {
        val found = mutableListOf<ExtractedTask>()

        for (pattern in patterns) {
            for (match in pattern.findAll(text)) {
                // group 1 is the action alone; match.value would drag in the
                // trigger phrase and produce titles like "Should revise notes".
                val raw = match.groupValues.getOrNull(1)?.trim().orEmpty()
                if (raw.isBlank()) continue

                // Look for a date in the remainder of the sentence containing
                // this action, so each task gets its own deadline.
                val tail = text.substring(match.range.last.coerceAtMost(text.length - 1))
                    .substringBefore('.')
                    .substringBefore('\n')
                val date = TemporalParser.parse(tail, now, zone)
                    ?: TemporalParser.parse(match.value, now, zone)

                val title = cleanTitle(raw)
                if (title.length < MIN_TITLE_LENGTH) continue

                found.add(
                    ExtractedTask(
                        title = title,
                        deadline = date?.displayText,
                        dueAt = date?.dueAt,
                        // Per clause, not per transcript: only this task's own
                        // date can say whether its time was actually spoken.
                        hasExactTime = date?.hasExplicitTime == true
                    )
                )
            }
        }

        return found.distinctBy { normalizeForDedup(it.title) }
    }

    /**
     * Turns a captured clause into a clean imperative title.
     */
    private fun cleanTitle(raw: String): String {
        var title = raw.trim()

        // Strip any residual trigger wording. Repeat because speech stacks them:
        // "I need to make sure to call the bank".
        var changed = true
        while (changed) {
            changed = false
            for (prefix in Lexicon.taskTitlePrefixes) {
                val candidate = Regex("^${Regex.escape(prefix)}\\b\\s*", RegexOption.IGNORE_CASE)
                val stripped = candidate.replace(title, "")
                if (stripped != title) {
                    title = stripped.trim()
                    changed = true
                }
            }
        }

        // Remove any date or time phrase that survived inside the clause.
        // Back to front, so removing one span does not shift the next.
        TemporalParser.parse(title)?.let { parsed ->
            for (range in parsed.ranges.asReversed()) {
                title = title.removeRange(range)
            }
            title = title.trim()
        }

        // Drop prepositions left dangling by the removals above, which is what
        // turns "finish the frontend by" back into "finish the frontend".
        changed = true
        while (changed) {
            changed = false
            title = title.trim().trimEnd(',', ';', ':', '-', '—')
            val lastWord = title.substringAfterLast(' ', title)
            if (title.contains(' ') && Lexicon.danglingSuffixes.contains(lastWord.lowercase(Locale.ROOT))) {
                title = title.substringBeforeLast(' ').trim()
                changed = true
            }
        }

        val words = title.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return ""

        return words.take(MAX_TITLE_WORDS)
            .joinToString(" ")
            .replaceFirstChar { it.titlecase(Locale.ROOT) }
    }

    private fun normalizeForDedup(title: String): String =
        title.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
}
