package com.naomi.app.ai.intelligence

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Resolves the date and time expressions people actually say ("tomorrow at 6",
 * "next Friday", "September 4 at 3 PM") into a real instant.
 *
 * Storing the literal phrase instead is subtly broken: a task captured yesterday
 * saying "tomorrow" still reads "tomorrow" today, and cannot be sorted, bucketed
 * or reminded on. Naomi keeps both — the instant for logic, the phrase for
 * display, because "Friday at 6 PM" reads better than a formatted timestamp.
 *
 * Date and time are found independently and then composed, because that is how
 * they are spoken. "Tomorrow at 6" is two facts, and a single pattern trying to
 * match both would need one entry per combination.
 */
object TemporalParser {

    /**
     * @param dueAt          resolved instant, in epoch milliseconds
     * @param displayText    the phrase as a person would read it back
     * @param ranges         where the phrases sat in the source text, so callers
     *                       can strip them. Plural because a date and a time are
     *                       often not adjacent — "call Sam tomorrow at 6" has one
     *                       span, "tomorrow I should call Sam at 6" has two.
     * @param hasExplicitTime whether the speaker actually stated a clock time.
     *                       This is the field the alarm decision hangs on: an
     *                       exact alarm is worth its cost when someone said
     *                       "6 PM" and is an imposition when Naomi guessed 09:00.
     * @param meridiemStated whether "am" or "pm" was actually spoken. A bare
     *                       "at 6" is interpreted, not known.
     */
    data class ParsedDate(
        val dueAt: Long,
        val displayText: String,
        val ranges: List<IntRange>,
        val hasExplicitTime: Boolean = false,
        val meridiemStated: Boolean = false
    ) {
        /** The whole span from the first matched phrase to the last. */
        val matchRange: IntRange
            get() = ranges.first().first..ranges.last().last
    }

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

    private val monthNames = mapOf(
        "january" to Month.JANUARY, "jan" to Month.JANUARY,
        "february" to Month.FEBRUARY, "feb" to Month.FEBRUARY,
        "march" to Month.MARCH, "mar" to Month.MARCH,
        "april" to Month.APRIL, "apr" to Month.APRIL,
        "may" to Month.MAY,
        "june" to Month.JUNE, "jun" to Month.JUNE,
        "july" to Month.JULY, "jul" to Month.JULY,
        "august" to Month.AUGUST, "aug" to Month.AUGUST,
        "september" to Month.SEPTEMBER, "sept" to Month.SEPTEMBER, "sep" to Month.SEPTEMBER,
        "october" to Month.OCTOBER, "oct" to Month.OCTOBER,
        "november" to Month.NOVEMBER, "nov" to Month.NOVEMBER,
        "december" to Month.DECEMBER, "dec" to Month.DECEMBER
    )

    private const val MONTH_ALTERNATION =
        "january|february|march|april|may|june|july|august|september|october|november|december|" +
            "jan|feb|mar|apr|jun|jul|aug|sept|sep|oct|nov|dec"

    /**
     * Spelled-out counts for "in three days".
     *
     * This used to stop at twelve and then skip to fourteen, so "in thirteen
     * days" and everything from fifteen up returned null and the task silently
     * lost its deadline.
     */
    private val numberWords = mapOf(
        "one" to 1L, "two" to 2L, "three" to 3L, "four" to 4L, "five" to 5L,
        "six" to 6L, "seven" to 7L, "eight" to 8L, "nine" to 9L, "ten" to 10L,
        "eleven" to 11L, "twelve" to 12L, "thirteen" to 13L, "fourteen" to 14L,
        "fifteen" to 15L, "sixteen" to 16L, "seventeen" to 17L, "eighteen" to 18L,
        "nineteen" to 19L, "twenty" to 20L, "thirty" to 30L, "forty" to 40L,
        "fifty" to 50L, "sixty" to 60L, "ninety" to 90L,
        "a" to 1L, "an" to 1L, "couple" to 2L, "few" to 3L
    )

    // ---- dates ----

    // Ordered by specificity: "day after tomorrow" must win over "tomorrow",
    // and "next monday" over a bare "monday". Order only decides ties, since the
    // earliest match in the sentence wins outright.
    private val datePatterns: List<Regex> = listOf(
        Regex("""\b(?:the )?day after tomorrow\b""", RegexOption.IGNORE_CASE),
        Regex("""\bin (\d+|\w+) (day|days|week|weeks|month|months)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b($MONTH_ALTERNATION)\.?\s+(\d{1,2})(?:st|nd|rd|th)?\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(\d{1,2})(?:st|nd|rd|th)?\s+(?:of\s+)?($MONTH_ALTERNATION)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bon the (\d{1,2})(?:st|nd|rd|th)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bnext (monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bthis (monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bnext week\b""", RegexOption.IGNORE_CASE),
        Regex("""\bnext month\b""", RegexOption.IGNORE_CASE),
        Regex("""\bthis weekend\b""", RegexOption.IGNORE_CASE),
        Regex("""\bend of (the )?week\b""", RegexOption.IGNORE_CASE),
        Regex("""\bend of (the )?month\b""", RegexOption.IGNORE_CASE),
        Regex("""\btomorrow\b""", RegexOption.IGNORE_CASE),
        Regex("""\btonight\b""", RegexOption.IGNORE_CASE),
        Regex("""\btoday\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(mon|tue|tues|wed|thu|thur|thurs|fri|sat|sun)\b""", RegexOption.IGNORE_CASE)
    )

    // ---- times ----

    /**
     * A clock time must be *announced*, by a preposition, a colon, a meridiem or
     * the word "o'clock". A bare number is not a time: "version 2" and "sprint 3"
     * are far more common in dictation than someone stating an hour with no cue
     * at all, and inventing a due date from one would be worse than missing it.
     */
    private val timePatterns: List<Regex> = listOf(
        // "at 6:30 pm", "at 6 pm", "at 6"
        Regex("""\bat\s+(\d{1,2})(?::(\d{2}))?\s*(a\.?m\.?|p\.?m\.?)?\b""", RegexOption.IGNORE_CASE),
        // "6:30 pm", "18:00"
        Regex("""\b(\d{1,2}):(\d{2})\s*(a\.?m\.?|p\.?m\.?)?\b""", RegexOption.IGNORE_CASE),
        // "6pm"
        Regex("""\b(\d{1,2})()\s*(a\.?m\.?|p\.?m\.?)\b""", RegexOption.IGNORE_CASE),
        // "6 o'clock"
        Regex("""\b(\d{1,2})()\s*o'?\s?clock\b""", RegexOption.IGNORE_CASE)
    )

    private val noonPattern = Regex("""\b(noon|midday|midnight)\b""", RegexOption.IGNORE_CASE)

    /**
     * Time-of-day words. These set an hour but are *not* an explicit time —
     * "tomorrow morning" is a wish, not an appointment, and must not earn an
     * exact alarm. They also disambiguate a bare hour: "tomorrow morning at 6"
     * is 6 AM, where "at 6" alone would be read as the evening.
     */
    private val periodPattern =
        Regex("""\b(morning|afternoon|evening|night)\b""", RegexOption.IGNORE_CASE)

    private data class DateMatch(val date: LocalDate, val range: IntRange, val label: String)
    private data class TimeMatch(
        val time: LocalTime,
        val range: IntRange,
        val meridiemStated: Boolean
    )

    /**
     * Finds the first date and/or time expression in [text], if any.
     *
     * [now] and [zone] are injectable so the behaviour is testable on a fixed
     * clock rather than whatever day the test suite happens to run. They are
     * also why nothing here is ever hardcoded: every relative phrase resolves
     * against the device's own clock and time zone.
     */
    fun parse(
        text: String,
        now: LocalDateTime = LocalDateTime.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): ParsedDate? {
        val period = periodPattern.find(text)?.value?.lowercase(Locale.ROOT)
        val date = findDate(text, now)
        val time = findTime(text, period)

        if (date == null && time == null && period == null) return null

        val periodRange = if (time == null) periodPattern.find(text)?.range else null

        val day = date?.date ?: now.toLocalDate()
        val timeOfDay = time?.time ?: period?.let { periodDefault(it) } ?: DEFAULT_DUE_TIME

        var resolved = day.atTime(timeOfDay)

        // "at 6" with no day attached means the next 6 there is. Rolling only
        // happens when the speaker named no date at all — if they said "today at
        // 7" after 7, that is genuinely overdue and saying otherwise would move
        // an appointment they already missed.
        if (date == null && !resolved.isAfter(now)) {
            resolved = resolved.plusDays(1)
        }

        val ranges = listOfNotNull(date?.range, time?.range, periodRange)
            .sortedBy { it.first }
            .let(::mergeAdjacent)

        return ParsedDate(
            dueAt = resolved.atZone(zone).toInstant().toEpochMilli(),
            displayText = label(date?.label, resolved, now, time != null),
            ranges = ranges,
            hasExplicitTime = time != null,
            meridiemStated = time?.meridiemStated == true
        )
    }

    private fun findDate(text: String, now: LocalDateTime): DateMatch? {
        var earliest: MatchResult? = null
        var earliestPattern: Regex? = null

        for (pattern in datePatterns) {
            val match = pattern.find(text) ?: continue
            // Prefer the earliest mention; break ties toward the more specific
            // pattern, which is whichever appears first in `datePatterns`.
            if (earliest == null || match.range.first < earliest.range.first) {
                earliest = match
                earliestPattern = pattern
            }
        }

        val match = earliest ?: return null
        val phrase = match.value.lowercase(Locale.ROOT).trim()
        val today = now.toLocalDate()
        val groups = match.groupValues

        val resolved: LocalDate = when {
            phrase.contains("day after tomorrow") -> today.plusDays(2)

            phrase.startsWith("in ") -> {
                val amount = groups[1].toLongOrNull()
                    ?: numberWords[groups[1].lowercase(Locale.ROOT)]
                    ?: return null
                when {
                    groups[2].startsWith("day", ignoreCase = true) -> today.plusDays(amount)
                    groups[2].startsWith("week", ignoreCase = true) -> today.plusWeeks(amount)
                    else -> today.plusMonths(amount)
                }
            }

            phrase.startsWith("on the ") -> {
                val dayOfMonth = groups[1].toIntOrNull() ?: return null
                onDayOfMonth(today, dayOfMonth) ?: return null
            }

            monthNames.containsKey(groups.getOrNull(1)?.lowercase(Locale.ROOT)) ->
                monthDay(today, groups[1], groups[2]) ?: return null

            monthNames.containsKey(groups.getOrNull(2)?.lowercase(Locale.ROOT)) ->
                monthDay(today, groups[2], groups[1]) ?: return null

            phrase.startsWith("next ") && phrase != "next week" && phrase != "next month" -> {
                val day = dayNames[phrase.removePrefix("next ").trim()] ?: return null
                // "next Friday" means the Friday of *next* week, not the coming
                // one — otherwise it collides with a bare "Friday" on any day
                // before that weekday. Anchoring to next week's Monday keeps the
                // two distinct no matter which day it is said on.
                today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                    .with(TemporalAdjusters.nextOrSame(day))
            }

            phrase == "next week" -> today.plusWeeks(1)
            phrase == "next month" -> today.plusMonths(1)
            phrase == "this weekend" -> today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
            phrase.startsWith("end of") && phrase.contains("week") ->
                today.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY))
            phrase.startsWith("end of") && phrase.contains("month") ->
                today.with(TemporalAdjusters.lastDayOfMonth())

            phrase == "tomorrow" -> today.plusDays(1)
            phrase == "tonight" || phrase == "today" -> today

            else -> {
                val key = phrase.removePrefix("this ").trim()
                val day = dayNames[key] ?: return null
                // A bare weekday means the next one that hasn't happened yet.
                today.with(TemporalAdjusters.nextOrSame(day))
            }
        }

        return DateMatch(resolved, match.range, displayLabel(match.value.trim()))
    }

    /**
     * A date in a month, rolling to next year when it has already passed.
     *
     * "September 4" said in October means next September; said in August it
     * means this one. Nobody dictating a plan means a date three hundred days
     * behind them.
     */
    private fun monthDay(today: LocalDate, monthText: String, dayText: String): LocalDate? {
        val month = monthNames[monthText.lowercase(Locale.ROOT)] ?: return null
        val day = dayText.toIntOrNull() ?: return null
        if (day !in 1..31) return null

        val candidate = runCatching { LocalDate.of(today.year, month, day) }.getOrNull() ?: return null
        return if (candidate.isBefore(today)) {
            runCatching { LocalDate.of(today.year + 1, month, day) }.getOrNull()
        } else {
            candidate
        }
    }

    /** "on the 4th" — this month if it is still to come, otherwise next. */
    private fun onDayOfMonth(today: LocalDate, day: Int): LocalDate? {
        if (day !in 1..31) return null
        val thisMonth = runCatching { today.withDayOfMonth(day) }.getOrNull()
        if (thisMonth != null && !thisMonth.isBefore(today)) return thisMonth
        val next = today.plusMonths(1)
        return runCatching { next.withDayOfMonth(day) }.getOrNull()
    }

    private fun findTime(text: String, period: String?): TimeMatch? {
        noonPattern.find(text)?.let { match ->
            val time = when (match.value.lowercase(Locale.ROOT)) {
                "midnight" -> LocalTime.MIDNIGHT
                else -> LocalTime.NOON
            }
            // Named times are as explicit as a clock reading, and unambiguous,
            // so they count as a stated meridiem.
            return TimeMatch(time, match.range, meridiemStated = true)
        }

        for (pattern in timePatterns) {
            val match = pattern.find(text) ?: continue
            val hour = match.groupValues[1].toIntOrNull() ?: continue
            val minute = match.groupValues[2].toIntOrNull() ?: 0
            if (hour !in 0..23 || minute !in 0..59) continue

            val meridiem = match.groupValues.getOrNull(3)
                ?.lowercase(Locale.ROOT)
                ?.replace(".", "")
                ?.takeIf { it.isNotBlank() }

            val resolvedHour = resolveHour(hour, meridiem, period) ?: continue
            return TimeMatch(
                time = LocalTime.of(resolvedHour, minute),
                range = match.range,
                meridiemStated = meridiem != null
            )
        }
        return null
    }

    /**
     * Turns a spoken hour into a 24-hour one.
     *
     * A stated meridiem settles it. A time-of-day word settles it next: someone
     * who says "tomorrow morning at 6" means 6 AM, and reading that as the
     * evening would put the reminder thirteen hours late.
     *
     * Failing both, this guesses, and the guess is the ordinary shape of a day
     * rather than the clock's midpoint: 1 to 7 said on its own is the afternoon
     * or evening, 8 to 11 is the morning. Someone who means 6 AM says "6 AM".
     * [ParsedDate.meridiemStated] records that this was a guess, so a caller can
     * decline to act on it.
     */
    private fun resolveHour(hour: Int, meridiem: String?, period: String?): Int? {
        if (hour > 23) return null

        return when {
            meridiem?.startsWith("a") == true -> if (hour == 12) 0 else hour
            meridiem?.startsWith("p") == true -> if (hour == 12) 12 else hour % 12 + 12
            // A 24-hour reading is already unambiguous.
            hour == 0 || hour > 12 -> hour
            period == "morning" -> if (hour == 12) 0 else hour
            period == "afternoon" || period == "evening" || period == "night" ->
                if (hour == 12) 12 else hour % 12 + 12
            hour == 12 -> 12
            hour in 1..7 -> hour + 12
            else -> hour
        }
    }

    private fun periodDefault(period: String): LocalTime = when (period) {
        "morning" -> LocalTime.of(9, 0)
        "afternoon" -> LocalTime.of(14, 0)
        "evening" -> EVENING_TIME
        else -> LocalTime.of(20, 0)
    }

    /** Joins spans separated only by whitespace, so "tomorrow at 6" strips as one. */
    private fun mergeAdjacent(ranges: List<IntRange>): List<IntRange> {
        if (ranges.size < 2) return ranges
        val out = mutableListOf(ranges.first())
        for (range in ranges.drop(1)) {
            val last = out.last()
            if (range.first <= last.last + 2) {
                out[out.lastIndex] = last.first..maxOf(last.last, range.last)
            } else {
                out += range
            }
        }
        return out
    }

    private fun label(
        dateLabel: String?,
        resolved: LocalDateTime,
        now: LocalDateTime,
        hasTime: Boolean
    ): String {
        val day = dateLabel ?: relativeDayLabel(resolved.toLocalDate(), now.toLocalDate())
        if (!hasTime) return day
        return "$day at ${formatTime(resolved.toLocalTime())}"
    }

    private fun relativeDayLabel(due: LocalDate, today: LocalDate): String = when {
        due == today -> "Today"
        due == today.plusDays(1) -> "Tomorrow"
        else -> due.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    }

    private fun formatTime(time: LocalTime): String {
        val pattern = if (time.minute == 0) "h a" else "h:mm a"
        return time.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
    }

    /**
     * How long something lasts — "for 30 minutes", "for two hours", "half an
     * hour" — in minutes.
     *
     * Only used to give a calendar event an end time. Null means the speaker
     * did not say, and the caller should pick a default rather than this
     * pretending to know one.
     */
    fun parseDuration(text: String): Int? {
        if (halfHourPattern.containsMatchIn(text)) return 30

        val match = durationPattern.find(text) ?: return null
        val amount = match.groupValues[1].toLongOrNull()
            ?: numberWords[match.groupValues[1].lowercase(Locale.ROOT)]
            ?: return null
        val minutes = when {
            match.groupValues[2].startsWith("h", ignoreCase = true) -> amount * 60
            else -> amount
        }
        // A meeting is not eight days long, and a "duration" that large is
        // almost always a misparse of something else in the sentence.
        return minutes.toInt().takeIf { it in 1..MAX_DURATION_MINUTES }
    }

    private val durationPattern = Regex(
        """\bfor\s+(\d+|\w+)\s*(hours?|hrs?|h|minutes?|mins?|m)\b""",
        RegexOption.IGNORE_CASE
    )
    private val halfHourPattern =
        Regex("""\b(half an hour|half hour|30 ?min)\b""", RegexOption.IGNORE_CASE)

    private const val MAX_DURATION_MINUTES = 12 * 60

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
            days < 7L -> due.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
            else -> due.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
        }
    }

    private fun displayLabel(raw: String): String =
        raw.split(" ").joinToString(" ") { word ->
            word.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
        }
}
