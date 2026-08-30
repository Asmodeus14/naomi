package com.naomi.app.ai

import com.naomi.app.ai.intelligence.Phonetics
import com.naomi.app.ai.intelligence.TopicMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sound-key is the load-bearing part of proper-noun correction, so these
 * pin down actual keys rather than only relative similarities — a rule change
 * that quietly alters how a word reduces should fail here, not in a user's
 * transcript.
 */
class PhoneticsTest {

    // ---- keys ----

    @Test
    fun `nyx reduces to its consonant skeleton`() {
        assertEquals("NKS", Phonetics.key("Nyx"))
    }

    @Test
    fun `next keeps the trailing stop that nyx lacks`() {
        assertEquals("NKST", Phonetics.key("next"))
    }

    @Test
    fun `case and punctuation do not change the key`() {
        assertEquals(Phonetics.key("Nyx"), Phonetics.key("NYX"))
        assertEquals(Phonetics.key("Nyx"), Phonetics.key("nyx!"))
        assertEquals(Phonetics.key("Nyx"), Phonetics.key("Nyx's"))
    }

    @Test
    fun `doubled letters are one sound`() {
        assertEquals(Phonetics.key("buffer"), Phonetics.key("bufer"))
    }

    @Test
    fun `silent leading pairs are dropped`() {
        assertEquals("N", Phonetics.key("knee"))
        assertEquals("N", Phonetics.key("gnome").take(1))
        assertEquals("RST", Phonetics.key("wrist"))
    }

    @Test
    fun `ph sounds like f`() {
        assertEquals(Phonetics.key("phone"), Phonetics.key("fone"))
    }

    @Test
    fun `a word with no letters has no key`() {
        assertEquals("", Phonetics.key("123"))
        assertEquals("", Phonetics.key("!!"))
    }

    // ---- similarity ----

    @Test
    fun `nyx and next sound alike`() {
        assertTrue(Phonetics.soundsAlike("Nyx", "next"))
    }

    /**
     * The reason this class exists. If spelling distance were enough, the
     * existing TopicMatcher would already catch this mishearing — it does not,
     * and this test is what proves the extra machinery is earning its place.
     */
    @Test
    fun `sound is a better signal than spelling for this mishearing`() {
        val bySound = Phonetics.similarity("Nyx", "next")
        val bySpelling = TopicMatcher.calculateSimilarity("nyx", "next")

        assertTrue(
            "expected sound ($bySound) to beat spelling ($bySpelling)",
            bySound > bySpelling
        )
        assertTrue(
            "spelling alone should miss this at the matcher's own threshold",
            bySpelling < TopicMatcher.DEFAULT_THRESHOLD
        )
    }

    @Test
    fun `identical words are identical in sound`() {
        assertEquals(1.0, Phonetics.similarity("buffer", "Buffer"), 0.0001)
    }

    @Test
    fun `unrelated words do not sound alike`() {
        assertFalse(Phonetics.soundsAlike("Nyx", "database"))
        assertFalse(Phonetics.soundsAlike("meeting", "buffer"))
        assertFalse(Phonetics.soundsAlike("Rahul", "tomorrow"))
    }

    @Test
    fun `an empty key is never a match`() {
        assertEquals(0.0, Phonetics.similarity("123", "Nyx"), 0.0001)
    }

    /**
     * Found by reading the seeded vocabulary off a running device: "Vue"
     * reduces to the single character "F", and so does "few". Left unguarded
     * that is a perfect phonetic score, and enough evidence beside it would
     * turn "a few things" into "a Vue things".
     */
    @Test
    fun `a key too short to carry information is not evidence`() {
        assertEquals("F", Phonetics.key("Vue"))
        assertEquals(Phonetics.key("Vue"), Phonetics.key("few"))
        assertEquals(0.0, Phonetics.similarity("Vue", "few"), 0.0001)
        assertFalse(Phonetics.soundsAlike("Vue", "few"))
    }

    /** Three characters is enough to judge — this is where the cut-off sits. */
    @Test
    fun `a three character key still works`() {
        assertEquals("NKS", Phonetics.key("Nyx"))
        assertTrue(Phonetics.soundsAlike("Nyx", "Nix"))
    }
}
