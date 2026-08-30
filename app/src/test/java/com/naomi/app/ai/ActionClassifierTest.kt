package com.naomi.app.ai

import com.naomi.app.ai.intelligence.ActionClassifier
import com.naomi.app.ai.intelligence.ActionIntent
import com.naomi.app.ai.intelligence.TemporalParser
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The user never picks a mode. They talk, and Naomi decides whether that was
 * something to remember, list, schedule or be interrupted by.
 *
 * Every escalation past [ActionIntent.MEMORY] has a cost when it fires wrongly —
 * a phantom entry in a shared calendar, an alarm at six in the morning — so most
 * of these tests are about the cases that must *not* escalate.
 */
class ActionClassifierTest {

    private val monday: LocalDateTime = LocalDateTime.of(2025, 9, 1, 10, 15)
    private val zone: ZoneId = ZoneId.of("UTC")

    private fun classify(text: String): ActionIntent =
        ActionClassifier.classify(text, TemporalParser.parse(text, monday, zone))

    // ---- the four classifications the brief requires ----

    @Test
    fun `an explicit request to be interrupted is a reminder`() {
        assertEquals(ActionIntent.REMINDER, classify("remind me tomorrow at 6 to call Rahul"))
        assertEquals(ActionIntent.REMINDER, classify("wake me at 7 AM"))
        assertEquals(ActionIntent.REMINDER, classify("set an alarm for 5:30 tomorrow"))
    }

    @Test
    fun `an occasion at a stated time is an event`() {
        assertEquals(ActionIntent.EVENT, classify("meeting tomorrow at 6 with the team"))
        assertEquals(ActionIntent.EVENT, classify("dentist appointment on the 4th at 3 PM"))
        assertEquals(ActionIntent.EVENT, classify("the interview is September 4 at 3 PM"))
    }

    @Test
    fun `work the speaker committed to is a task`() {
        assertEquals(ActionIntent.TASK, classify("I need to file the taxes on Friday"))
        assertEquals(ActionIntent.TASK, classify("don't forget to water the plants"))
    }

    @Test
    fun `everything else is simply remembered`() {
        assertEquals(ActionIntent.MEMORY, classify("the ring buffer overflows at sixty frames"))
        assertEquals(ActionIntent.MEMORY, classify("Rahul mentioned the new pricing"))
    }

    // ---- the refusals ----

    /**
     * "The meeting is usually at 6" describes a pattern, not an appointment.
     * Every ingredient of an event is present — an occasion noun and a clock
     * time — which is exactly why the habitual marker is checked first.
     */
    @Test
    fun `a habitual marker keeps a well-formed event as a memory`() {
        assertEquals(ActionIntent.MEMORY, classify("the meeting is usually at 6"))
        assertEquals(ActionIntent.MEMORY, classify("standup normally starts at 9:30"))
        assertEquals(ActionIntent.MEMORY, classify("I always call my mother on Sunday at 7 PM"))
    }

    /**
     * The speaker deliberately did not commit. Turning a hedge into an alarm
     * converts their vagueness into a precision they get woken by.
     */
    @Test
    fun `a hedged time is never acted on`() {
        assertEquals(ActionIntent.MEMORY, classify("Rahul is coming tomorrow around 6"))
        assertEquals(ActionIntent.MEMORY, classify("the call is sometime tomorrow"))
        assertEquals(ActionIntent.MEMORY, classify("lunch tomorrow at 1ish"))
    }

    /**
     * ...but the hedge has to be modifying the time. Scanning the whole
     * sentence would catch the ordinary "about" here and silently drop a real
     * appointment.
     */
    @Test
    fun `a hedge word doing ordinary work several words away does not count`() {
        assertEquals(ActionIntent.EVENT, classify("we talked about the meeting tomorrow at 6"))
    }

    /**
     * The hour is not Naomi's to invent. An occasion with no time stays a
     * memory rather than becoming an event at a time nobody agreed to.
     */
    @Test
    fun `an occasion with no stated time is not scheduled`() {
        assertEquals(ActionIntent.MEMORY, classify("meeting tomorrow"))
        assertEquals(ActionIntent.MEMORY, classify("dentist appointment next Friday"))
    }

    /** Asked to be reminded, but gave no hour — that is a task, not an alarm. */
    @Test
    fun `a reminder with no time degrades to a task`() {
        assertEquals(ActionIntent.TASK, classify("remind me to call the bank"))
        assertEquals(ActionIntent.TASK, classify("remind me to renew the passport tomorrow"))
    }

    /**
     * "Call" is both an occasion and something a person does. What separates
     * them is whether the speaker described work they committed to.
     */
    @Test
    fun `work that happens to name an occasion is still work`() {
        assertEquals(ActionIntent.TASK, classify("I need to call Rahul tomorrow at 6"))
        assertEquals(ActionIntent.EVENT, classify("the call is today at 7 PM"))
    }

    @Test
    fun `a question is asking Naomi something, not instructing it`() {
        assertEquals(ActionIntent.MEMORY, classify("when is the meeting tomorrow at 6?"))
    }
}
