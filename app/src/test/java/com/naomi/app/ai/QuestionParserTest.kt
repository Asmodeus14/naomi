package com.naomi.app.ai

import com.naomi.app.ai.intelligence.QuestionParser
import com.naomi.app.ai.intelligence.QuestionParser.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parser's whole job is to leave the subject and drop everything else.
 * Leaving one word of the question in ("say ring buffer") matches nothing,
 * because those words are not what the user recorded — so these tests are mostly
 * about what gets removed.
 */
class QuestionParserTest {

    @Test
    fun `the question around the subject is removed`() {
        assertEquals("ring buffer", QuestionParser.parse("What did I say about the ring buffer?").subject)
        assertEquals("ring buffer", QuestionParser.parse("what do I know about ring buffer").subject)
        assertEquals("ring buffer", QuestionParser.parse("Tell me about the ring buffer.").subject)
        assertEquals("ring buffer", QuestionParser.parse("do I have anything on the ring buffer?").subject)
    }

    @Test
    fun `a bare subject is left alone`() {
        // People type keywords too. That must not be mangled.
        assertEquals("ring buffer", QuestionParser.parse("ring buffer").subject)
    }

    @Test
    fun `timing questions are recognised as being about deadlines`() {
        assertEquals(Intent.DEADLINE, QuestionParser.parse("When is the presentation?").intent)
        assertEquals(Intent.DEADLINE, QuestionParser.parse("when did I say the report was due").intent)
        assertEquals(Intent.DEADLINE, QuestionParser.parse("what is the deadline for the report").intent)
    }

    @Test
    fun `everything else is a recall question`() {
        assertEquals(Intent.RECALL, QuestionParser.parse("What did I say about the ring buffer?").intent)
        assertEquals(Intent.RECALL, QuestionParser.parse("ring buffer").intent)
    }

    @Test
    fun `the longest matching opener wins`() {
        // "what" alone would leave "did i say about the ring buffer", which
        // matches nothing the user ever said.
        val parsed = QuestionParser.parse("what did I say about the ring buffer")
        assertEquals("ring buffer", parsed.subject)
        assertEquals(listOf("ring", "buffer"), parsed.terms)
    }

    @Test
    fun `a question with no subject is not answerable`() {
        // Better to ask what they mean than to match every memory in the store
        // and present the pile as an answer.
        assertFalse(QuestionParser.parse("what did I say about it?").isAnswerable)
        assertFalse(QuestionParser.parse("what?").isAnswerable)
        assertFalse(QuestionParser.parse("   ").isAnswerable)
    }

    @Test
    fun `trailing filler is dropped`() {
        assertEquals("presentation", QuestionParser.parse("when is the presentation due?").subject)
        assertEquals("ring buffer", QuestionParser.parse("what did I say about the ring buffer again").subject)
    }

    @Test
    fun `terms exclude stop words but the subject keeps them readable`() {
        val parsed = QuestionParser.parse("what did I say about the cost of the trip")
        assertEquals("cost of the trip", parsed.subject)
        assertEquals(listOf("cost", "trip"), parsed.terms)
        assertTrue(parsed.isAnswerable)
    }
}
