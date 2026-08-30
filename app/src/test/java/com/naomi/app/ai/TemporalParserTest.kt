package com.naomi.app.ai

import com.naomi.app.ai.intelligence.TemporalParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Naomi could not tell the time. Every parsed date landed on a hardcoded 09:00
 * or 19:00, so "remind me tomorrow at 6" produced a reminder nine hours early —
 * which is worse than no reminder, because it is silently wrong.
 *
 * Everything here runs on a fixed clock. A test that passes only on Tuesdays is
 * not a test.
 */
class TemporalParserTest {

    // Monday 1 September 2025, quarter past ten in the morning.
    private val monday: LocalDateTime = LocalDateTime.of(2025, 9, 1, 10, 15)
    private val zone: ZoneId = ZoneId.of("UTC")

    private fun at(text: String, now: LocalDateTime = monday) =
        TemporalParser.parse(text, now, zone)

    private fun local(text: String, now: LocalDateTime = monday): LocalDateTime =
        Instant.ofEpochMilli(at(text, now)!!.dueAt).atZone(zone).toLocalDateTime()

    // ---- the five cases the brief requires ----

    @Test
    fun `tomorrow at 6`() {
        val parsed = at("remind me tomorrow at 6 to call Rahul")!!
        assertEquals(LocalDateTime.of(2025, 9, 2, 18, 0), local("remind me tomorrow at 6 to call Rahul"))
        assertTrue(parsed.hasExplicitTime)
        // Nobody said PM. Naomi read it that way, and records that it did.
        assertFalse(parsed.meridiemStated)
    }

    @Test
    fun `today at 7 PM`() {
        val parsed = at("the call is today at 7 PM")!!
        assertEquals(LocalDateTime.of(2025, 9, 1, 19, 0), local("the call is today at 7 PM"))
        assertTrue(parsed.hasExplicitTime)
        assertTrue(parsed.meridiemStated)
    }

    @Test
    fun `Friday at 5`() {
        assertEquals(LocalDateTime.of(2025, 9, 5, 17, 0), local("standup moved to Friday at 5"))
        assertTrue(at("standup moved to Friday at 5")!!.hasExplicitTime)
    }

    /**
     * A time of day is not a time. "Tomorrow morning" must set a sensible hour
     * for sorting without ever earning an exact alarm — the user did not commit
     * to a minute, and Naomi must not pretend they did.
     */
    @Test
    fun `tomorrow morning is a wish, not an appointment`() {
        assertEquals(LocalDateTime.of(2025, 9, 2, 9, 0), local("finish the deck tomorrow morning"))
        assertFalse(at("finish the deck tomorrow morning")!!.hasExplicitTime)
    }

    @Test
    fun `September 4 at 3 PM`() {
        val parsed = at("the interview is September 4 at 3 PM")!!
        assertEquals(LocalDateTime.of(2025, 9, 4, 15, 0), local("the interview is September 4 at 3 PM"))
        assertTrue(parsed.hasExplicitTime)
        assertTrue(parsed.meridiemStated)
    }

    // ---- clock formats ----

    @Test
    fun `the shapes a clock time is spoken in`() {
        assertEquals(LocalDateTime.of(2025, 9, 2, 18, 30), local("tomorrow at 6:30"))
        assertEquals(LocalDateTime.of(2025, 9, 2, 18, 0), local("tomorrow 6pm"))
        assertEquals(LocalDateTime.of(2025, 9, 2, 6, 0), local("tomorrow at 6 AM"))
        assertEquals(LocalDateTime.of(2025, 9, 2, 18, 0), local("tomorrow at 18:00"))
        assertEquals(LocalDateTime.of(2025, 9, 2, 12, 0), local("tomorrow at noon"))
        assertEquals(LocalDateTime.of(2025, 9, 2, 0, 0), local("tomorrow at midnight"))
        assertEquals(LocalDateTime.of(2025, 9, 2, 21, 0), local("tomorrow at 9 o'clock at night"))
    }

    /**
     * A bare hour is a guess, and the guess follows the shape of a day rather
     * than the clock's midpoint. Someone who means six in the morning says so.
     */
    @Test
    fun `a bare hour is read as the waking part of the day`() {
        assertEquals(18, local("tomorrow at 6").hour)
        assertEquals(19, local("tomorrow at 7").hour)
        assertEquals(9, local("tomorrow at 9").hour)
        assertEquals(11, local("tomorrow at 11").hour)
        assertEquals(12, local("tomorrow at 12").hour)
    }

    /** A time-of-day word beside the hour settles it, and beats the guess. */
    @Test
    fun `morning overrides the bare-hour guess`() {
        assertEquals(6, local("tomorrow morning at 6").hour)
        assertEquals(18, local("tomorrow evening at 6").hour)
    }

    /**
     * A bare number is not a time. Dictation is full of "version 2" and "sprint
     * 3", and inventing a due date from one is worse than missing it.
     */
    @Test
    fun `a number with no cue is not a clock time`() {
        assertNull(at("ship version 2"))
        assertFalse(at("finish sprint 3 tomorrow")!!.hasExplicitTime)
    }

    // ---- explicit dates ----

    @Test
    fun `an explicit calendar date in either order`() {
        assertEquals(LocalDateTime.of(2025, 9, 4, 9, 0).toLocalDate(), local("September 4").toLocalDate())
        assertEquals(LocalDateTime.of(2025, 9, 4, 9, 0).toLocalDate(), local("4 September").toLocalDate())
        assertEquals(LocalDateTime.of(2025, 9, 4, 9, 0).toLocalDate(), local("Sept 4").toLocalDate())
        assertEquals(LocalDateTime.of(2025, 9, 4, 9, 0).toLocalDate(), local("on the 4th").toLocalDate())
    }

    /**
     * "September 4" said in October means next September. Nobody dictating a
     * plan means a date three hundred days behind them.
     */
    @Test
    fun `a month that has passed rolls to next year`() {
        val october = LocalDateTime.of(2025, 10, 6, 9, 0)
        assertEquals(2026, local("September 4", october).year)
    }

    // ---- composition and rolling ----

    /** A time with no day attached means the next one there is. */
    @Test
    fun `a bare time rolls forward when it has already passed today`() {
        // 10:15 on the Monday; 9 AM is gone.
        assertEquals(LocalDateTime.of(2025, 9, 2, 9, 0), local("call the bank at 9 AM"))
        // ...but 6 PM has not.
        assertEquals(LocalDateTime.of(2025, 9, 1, 18, 0), local("call the bank at 6"))
    }

    /**
     * A named day is never rolled. "Today at 7" said at eight in the evening is
     * genuinely overdue, and moving it to tomorrow would relocate an appointment
     * the user already missed.
     */
    @Test
    fun `a stated day is left alone even when it is already past`() {
        val evening = LocalDateTime.of(2025, 9, 1, 20, 0)
        assertEquals(LocalDateTime.of(2025, 9, 1, 19, 0), local("the call was today at 7 PM", evening))
    }

    // ---- regressions ----

    /**
     * The spelled-out numbers stopped at twelve and then skipped to fourteen, so
     * "in thirteen days" and everything from fifteen up silently lost its date.
     */
    @Test
    fun `every spelled-out count resolves`() {
        assertEquals(13L, days("in thirteen days"))
        assertEquals(15L, days("in fifteen days"))
        assertEquals(20L, days("in twenty days"))
        assertEquals(30L, days("in thirty days"))
    }

    private fun days(text: String): Long =
        java.time.temporal.ChronoUnit.DAYS.between(monday.toLocalDate(), local(text).toLocalDate())

    @Test
    fun `next friday and this friday stay distinct`() {
        val thisFriday = local("on friday")
        val nextFriday = local("next friday")
        assertEquals(LocalDateTime.of(2025, 9, 5, 9, 0), thisFriday)
        assertEquals(LocalDateTime.of(2025, 9, 12, 9, 0), nextFriday)
    }

    @Test
    fun `text with no date at all resolves to nothing`() {
        assertNull(at("fix the parser"))
        assertNull(at("the ring buffer overflows"))
    }

    /**
     * The date and time spans are reported separately so a caller can strip both
     * from a task title. They are not always adjacent.
     */
    @Test
    fun `both phrases are reported so a title can be cleaned`() {
        val parsed = at("tomorrow I should call Sam at 6")!!
        assertEquals(2, parsed.ranges.size)

        val adjacent = at("call Sam tomorrow at 6")!!
        assertEquals("a contiguous phrase should strip as one", 1, adjacent.ranges.size)
    }

    @Test
    fun `the label reads the way a person would say it back`() {
        assertEquals("Tomorrow at 6 PM", at("tomorrow at 6")!!.displayText.replace("pm", "PM"))
        assertEquals("Tomorrow", at("finish it tomorrow")!!.displayText)
        assertNotNull(at("at 6"))
    }
}
