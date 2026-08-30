package com.naomi.app.ai.intelligence

import java.util.Locale

/**
 * What the speaker wants Naomi to *do* about what they just said.
 *
 * [MEMORY] is the floor and the default, not a failure. Everything Naomi is told
 * is remembered; the other three add something on top, and each of those has a
 * cost if it fires wrongly — a phantom entry in a shared calendar, an alarm at
 * six in the morning, a task list full of things that were only mentioned.
 */
enum class ActionIntent {
    /** Belongs in the calendar: a thing happening at a stated time. */
    EVENT,

    /** Belongs in an alarm: the user asked to be interrupted. */
    REMINDER,

    /** Belongs in the task list: something to be done, time optional. */
    TASK,

    /** Belongs nowhere but the memory itself. */
    MEMORY
}

/**
 * Decides between remembering, listing, scheduling and interrupting — without
 * ever asking.
 *
 * The user never picks a mode. They talk, and Naomi works out what kind of thing
 * it was. That only stays pleasant if the confident cases act and the rest fall
 * quietly back to being a memory, so this is written as a series of reasons to
 * *not* escalate.
 *
 * Two of those reasons come straight from how people actually speak:
 *
 * - **"The meeting is usually at 6"** describes a pattern, not an appointment.
 *   A habitual marker means the sentence is about how things generally are.
 * - **"Rahul is coming tomorrow around 6"** is a hedge. The speaker deliberately
 *   did not commit to a time, and turning that into an alarm converts their
 *   vagueness into a false precision they will be woken by.
 */
object ActionClassifier {

    /**
     * Speech that describes a pattern rather than an occasion. Checked before
     * anything else, because these can co-occur with a perfectly well-formed
     * time and would otherwise look exactly like a commitment.
     */
    private val habitualMarkers = listOf(
        "usually", "normally", "typically", "generally", "always", "often",
        "every day", "every week", "every month", "every monday", "every tuesday",
        "every wednesday", "every thursday", "every friday", "every saturday",
        "every sunday", "every morning", "every evening", "each week",
        "tends to", "tend to", "used to", "in general", "as a rule"
    )

    /**
     * Words that withdraw the commitment from a time.
     *
     * Only counted next to the time itself — "we talked about the meeting
     * tomorrow at 6" contains "about" and is not a hedge.
     */
    private val hedgeWords = setOf(
        "around", "about", "approximately", "roughly", "maybe", "perhaps",
        "probably", "sometime", "somewhere", "circa", "like", "or so", "ish"
    )

    /** Standalone hedges that do not need to sit beside the clock. */
    private val looseHedges = listOf("sometime", "some time", "at some point", "or so", "-ish")

    private val reminderTriggers = listOf(
        "remind me", "reminder to", "reminder for", "set a reminder",
        "wake me", "set an alarm", "set alarm", "alarm for", "ping me",
        "nudge me", "don't let me forget", "dont let me forget", "alert me"
    )

    /**
     * Nouns that name an occasion. Deliberately things that happen *to* a
     * calendar rather than things a person does: "meeting" is an event, "email"
     * is a task, and the difference is whether someone else is waiting.
     */
    private val eventNouns = listOf(
        "meeting", "meet up", "meetup", "appointment", "interview", "call",
        "standup", "stand-up", "sync", "one on one", "1:1", "catch up", "catch-up",
        "check-in", "checkin", "review", "retro", "retrospective", "demo",
        "presentation", "webinar", "conference", "workshop", "session",
        "lunch", "dinner", "breakfast", "brunch", "coffee", "drinks",
        "party", "wedding", "birthday", "funeral", "reunion",
        "class", "lecture", "seminar", "exam", "test", "lesson", "training",
        "flight", "train", "bus", "pickup", "pick up", "drop off", "dropoff",
        "dentist", "doctor", "gp", "clinic", "hospital", "surgery", "scan",
        "haircut", "viewing", "inspection", "deadline", "hearing", "court"
    )

    /**
     * @param parsed the result of [TemporalParser.parse] on the same text.
     *               Passed in rather than re-parsed so a caller cannot end up
     *               classifying against a different time than it schedules.
     */
    fun classify(text: String, parsed: TemporalParser.ParsedDate?): ActionIntent {
        val lower = text.lowercase(Locale.ROOT)

        // A question is asking Naomi something, not telling it to do something.
        if (lower.trimEnd().endsWith("?")) return ActionIntent.MEMORY

        if (habitualMarkers.any { lower.contains(it) }) return ActionIntent.MEMORY
        if (parsed != null && isHedged(lower, parsed)) return ActionIntent.MEMORY

        val hasReminderTrigger = reminderTriggers.any { lower.contains(it) }
        val hasTaskTrigger = Lexicon.taskTriggers.any { lower.contains(it) }

        if (hasReminderTrigger) {
            // "Remind me to call the bank" is a task. Only a stated clock time
            // earns an interruption — Naomi will not pick an hour to wake
            // someone at and then claim they asked for it.
            return if (parsed?.hasExplicitTime == true) ActionIntent.REMINDER else ActionIntent.TASK
        }

        // Checked before events on purpose: "I need to call Rahul tomorrow at 6"
        // contains the event noun "call", but the speaker described work they
        // will do, not an occasion someone else is waiting at.
        if (hasTaskTrigger) return ActionIntent.TASK

        val namesAnOccasion = eventNouns.any { containsWord(lower, it) }
        if (namesAnOccasion && parsed != null && parsed.hasExplicitTime) {
            return ActionIntent.EVENT
        }

        // "Meeting tomorrow" — an occasion with no hour. The hour is not
        // Naomi's to invent, so this stays a memory rather than becoming an
        // event at a time nobody agreed to.
        return ActionIntent.MEMORY
    }

    /**
     * Whether the speaker backed away from the time they gave.
     *
     * The hedge has to sit beside the clock. Scanning the whole sentence would
     * catch "we talked about the meeting tomorrow at 6", where "about" is doing
     * ordinary work several words away and the 6 is meant exactly.
     */
    private fun isHedged(lower: String, parsed: TemporalParser.ParsedDate): Boolean {
        if (looseHedges.any { lower.contains(it) }) return true

        val start = parsed.ranges.first().first
        val window = lower.substring((start - HEDGE_WINDOW).coerceAtLeast(0), start)
        val words = window.split(Regex("[^a-z]+")).filter { it.isNotBlank() }

        // Only the last couple of words before the time can modify it.
        if (words.takeLast(2).any { it in hedgeWords }) return true

        // "6ish", "6-ish" — the hedge is attached to the number itself.
        val tail = lower.drop(parsed.ranges.last().last + 1).take(4)
        return tail.startsWith("ish") || tail.startsWith("-ish")
    }

    private fun containsWord(haystack: String, needle: String): Boolean =
        Regex("\\b${Regex.escape(needle)}\\b").containsMatchIn(haystack)

    /** Enough room for "around", "sometime" and a space, not for a clause. */
    private const val HEDGE_WINDOW = 24
}
