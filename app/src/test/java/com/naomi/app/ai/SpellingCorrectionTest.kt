package com.naomi.app.ai

import com.naomi.app.ai.intelligence.CorrectionDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Naomi has no keyboard, so "it's Nyx, not next" is the only way a user can
 * teach it a word. Every false positive here is permanent — it adds a term the
 * normalizer will rewrite other words into — so the tests lean on the refusals.
 */
class SpellingCorrectionTest {

    @Test
    fun `the canonical phrasing is understood`() {
        val result = CorrectionDetector.detectSpelling("It's Nyx, not next")
        assertNotNull(result)
        assertEquals("Nyx", result!!.correct)
        assertEquals("next", result.misheard)
    }

    @Test
    fun `the comma is optional and the opener can vary`() {
        for (phrasing in listOf(
            "I said Nyx not next",
            "that's Nyx not next",
            "the name is Nyx, not next",
            "it should be Nyx, not next"
        )) {
            val result = CorrectionDetector.detectSpelling(phrasing)
            assertEquals("failed on: $phrasing", "Nyx", result?.correct)
        }
    }

    /**
     * The guard that makes this safe to run on every capture. Without it, an
     * ordinary sentence about a schedule would be read as a pronunciation
     * lesson and put "Tuesday" in the vocabulary forever.
     */
    @Test
    fun `two words that sound nothing alike are a fact, not a spelling`() {
        assertNull(CorrectionDetector.detectSpelling("It's Tuesday, not Wednesday"))
        assertNull(CorrectionDetector.detectSpelling("the meeting is Thursday not Friday"))
    }

    @Test
    fun `an ordinary English word is never learned as vocabulary`() {
        assertNull(CorrectionDetector.detectSpelling("It's next, not nyx"))
    }

    @Test
    fun `a sentence merely containing not is not a correction`() {
        assertNull(CorrectionDetector.detectSpelling("It's not working again"))
        assertNull(CorrectionDetector.detectSpelling("I could not get the ring buffer to flush"))
    }

    @Test
    fun `a word cannot correct itself`() {
        assertNull(CorrectionDetector.detectSpelling("It's Nyx, not Nyx"))
    }
}
