package com.naomi.app.ai.intelligence

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Resolves the date expressions people actually say ("tomorrow", "next Friday",
 * "in three days") into a real instant.
 *
 * Storing the literal phrase instead is subtly broken: a task captured yesterday
 * saying "tomorrow" still reads "tomorrow" today, and cannot be sorted, bucketed
 * or reminded on. Naomi keeps both — the instant for logic, the phrase for
 * display, because "Friday" reads better than a formatted date.
 */
object TemporalParser {

    /**
     * @param dueAt       resolved instant, in epoch milliseconds
     * @param displayText the phrase the speaker used, title-cased
     * @param matchRange  where the phrase sat in the source text, so callers can strip it
     */
    data class ParsedDate(
        val dueAt: Long,
        val displayText: String,
        val matchRange: IntRange
    )

    /** Default time-of-day for a date with no stated hour. */
    private val DEFAULT_DUE_TIME: LocalTime = LocalTime.of(9, 0)
    private val EVENING_TIME: LocalTime = LocalTime.of(19, 0)

    private val dayNames = mapOf(
        "monday" to DayOfWeek.MONDAY,
        "tuesday" to DayOfWeek.TUESDAY,
        "wednesday" to DayOfWeek.WEDNESDAY,
        "thursday" to DayOfWeek.THURSDAY,
        "friday" to DayOfWeek.FRIDAY,
        "saturday" to DayOfWeek.SATURDAY,
        "sunday" to DayOfWeek.SUNDAY,
        "mon" to DayOfWeek.MONDAY,
        "tue" to DayOfWeek.TUESDAY,
        "tues" to DayOfWeek.TUESDAY,
        "wed" to DayOfWeek.WEDNESDAY,
        "thu" to DayOfWeek.THURSDAY,
        "thur" to DayOfWeek.THURSDAY,
        "thurs" to DayOfWeek.THURSDAY,
        "fri" to DayOfWeek.FRIDAY,
        "sat" to DayOfWeek.SATURDAY,
        "sun" to DayOfWeek.SUNDAY
    )

    private val numberWords = mapOf(
        "one" to 1L, "two" to 2L, "three" to 3L, "four" to 4L, "five" to 5L,
        "six" to 6L, "seven" to 7L, "eight" to 8L, "nine" to 9L, "ten" to 10L,
        "eleven" to 11L, "twelve" to 12L, "fourteen" to 14L, "a" to 1L, "an" to 1L
    )

    // Ordered by specificity: "day after tomorrow" must win over "tomorrow",
    // and "next monday" over a bare "monday".
    private val patterns: List<Regex> = listOf(
        Regex("""\bday after tomorrow\b""", RegexOption.IGNORE_CASE),
        Regex("""\bthe day after tomorrow\b""", RegexOption.IGNORE_CASE),
        Regex("""\bin (\d+|\w+) (day|days|week|weeks|month|months)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bnext (monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bthis (monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bnext week\b""", RegexOption.IGNORE_CASE),
        Regex("""\bnext month\b""", RegexOption.IGNORE_CASE),
        Regex("""\bthis weekend\b""", RegexOption.IGNORE_CASE),
        Regex("""\bend of (the )?week\b""", RegexOption.IGNORE_CASE),
        Regex("""\bend of (the )?month\b""", RegexOption.IGNORE_CASE),
        Regex("""\btomorrow morning\b""", RegexOption.IGNORE_CASE),
        Regex("""\btomorrow night\b""", RegexOption.IGNORE_CASE),
        Regex("""\btomorrow\b""", RegexOption.IGNORE_CASE),
        Regex("""\btonight\b""", RegexOption.IGNORE_CASE),
        Regex("""\btoday\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(mon|tue|tues|wed|thu|thur|thurs|fri|sat|sun)\b""", RegexOption.IGNORE_CASE)
    )

    /**
     * Finds the first date expression in [text], if any.
     *
     * [now] and [zone] are injectable so the behaviour is testable on a fixed
     * clock rather than whatever day the test suite happens to run.
     */
    fun parse(
        text: String,
        now: LocalDateTime = LocalDateTime.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): ParsedDate? {
        var earliest: MatchResult? = null
        var earliestPattern: Regex? = null

        for (pattern in patterns) {
            val match = pattern.find(text) ?: continue
            // Prefer the earliest mention; break ties toward the more specific
            // pattern, which is whichever appears first in `patterns`.
            if (earliest == null || match.range.first < earliest.range.first) {
                earliest = match
                earliestPattern = pattern
            }
        }

        val match = earliest ?: return null
        val phrase = match.value.lowercase(Locale.ROOT).trim()
        val today = now.toLocalDate()

        val resolved: LocalDateTime = when {
            phrase.contains("day after tomorrow") ->
                today.plusDays(2).atTime(DEFAULT_DUE_TIME)

            phrase.startsWith("in ") -> {
                val groups = earliestPattern!!.find(text)!!.groupValues
                val amount = groups[1].toLongOrNull()
                    ?: numberWords[groups[1].lowercase(Locale.ROOT)]
                    ?: return null
                val unit = groups[2].lowercase(Locale.ROOT)
                when {
                    unit.startsWith("day") -> today.plusDays(amount)
                    unit.startsWith("week") -> today.plusWeeks(amount)
                    else -> today.plusMonths(amount)
                }.atTime(DEFAULT_DUE_TIME)
            }

            phrase.startsWith("next ") && phrase != "next week" && phrase != "next month" -> {
                val day = dayNames[phrase.removePrefix("next ").trim()] ?: return null
                // "next Friday" means the Friday of *next* week, not the coming
                // one — otherwise it collides with a bare "Friday" on any day
                // before that weekday. Anchoring to next week's Monday keeps the
                // two distinct no matter which day it is said on.
                today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                    .with(TemporalAdjusters.nextOrSame(day))
                    .atTime(DEFAULT_DUE_TIME)
            }

            phrase == "next week" -> today.plusWeeks(1).atTime(DEFAULT_DUE_TIME)
            phrase == "next month" -> today.plusMonths(1).atTime(DEFAULT_DUE_TIME)

            phrase == "this weekend" ->
                today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY)).atTime(DEFAULT_DUE_TIME)

            phrase.startsWith("end of") && phrase.contains("week") ->
                today.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY)).atTime(EVENING_TIME)

            phrase.startsWith("end of") && phrase.contains("month") ->
                today.with(TemporalAdjusters.lastDayOfMonth()).atTime(EVENING_TIME)

            phrase == "tomorrow morning" -> today.plusDays(1).atTime(LocalTime.of(9, 0))
            phrase == "tomorrow night" -> today.plusDays(1).atTime(EVENING_TIME)
            phrase == "tomorrow" -> today.plusDays(1).atTime(DEFAULT_DUE_TIME)
            phrase == "tonight" -> today.atTime(EVENING_TIME)
            phrase == "today" -> today.atTime(DEFAULT_DUE_TIME)

            else -> {
                val key = phrase.removePrefix("this ").trim()
                val day = dayNames[key] ?: return null
                // A bare weekday means the next one that hasn't happened yet.
                today.with(TemporalAdjusters.nextOrSame(day)).atTime(DEFAULT_DUE_TIME)
            }
        }

        return ParsedDate(
            dueAt = resolved.atZone(zone).toInstant().toEpochMilli(),
            displayText = displayLabel(match.value.trim()),
            matchRange = match.range
        )
    }

    /**
     * Human label for a due date relative to [now] — "Today", "Tomorrow",
     * a weekday name within the coming week, otherwise a short date.
     */
    fun describe(
        dueAt: Long,
        now: LocalDateTime = LocalDateTime.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): String {
        val due: LocalDate = java.time.Instant.ofEpochMilli(dueAt).atZone(zone).toLocalDate()
        val today = now.toLocalDate()
        val days = java.time.temporal.ChronoUnit.DAYS.between(today, due)

        return when {
            days < 0L -> "Overdue"
            days == 0L -> "Today"
            days == 1L -> "Tomorrow"
            days < 7L -> due.dayOfWeek.getDisplayName(
                java.time.format.TextStyle.FULL,
                Locale.getDefault()
            )
            else -> due.format(java.time.format.DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
        }
    }

    private fun displayLabel(raw: String): String =
        raw.split(" ").joinToString(" ") { word ->
            word.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
        }
}
