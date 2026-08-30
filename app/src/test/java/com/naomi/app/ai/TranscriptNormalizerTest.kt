package com.naomi.app.ai

import com.naomi.app.ai.intelligence.TranscriptNormalizer
import com.naomi.app.ai.intelligence.VocabularyTerm
import com.naomi.app.ai.speech.Transcript
import com.naomi.app.ai.speech.Utterance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The correction engine is the one piece of Naomi that rewrites what the user
 * said. These tests are as much about what it refuses to do as what it does.
 */
class TranscriptNormalizerTest {

    // A user who works on a graphics project called Nyx. The vocabulary and the
    // topic tree are both things Naomi would have learned from their own notes.
    private val nyx = VocabularyTerm("Nyx", "nyx", "NKS", occurrences = 5)
    private val gtt = VocabularyTerm("GTT", "gtt", "KT", occurrences = 4)
    private val ringBuffer = VocabularyTerm("ring buffer", "ring buffer", "", occurrences = 6)

    private val vocabulary = listOf(nyx, gtt, ringBuffer)
    private val topics = listOf(
        "Nyx > Graphics > Ring Buffer",
        "Nyx > GTT Allocation"
    )

    private fun correct(text: String) =
        TranscriptNormalizer.correct(Transcript.of(text), vocabulary, topics)

    // ---- the four cases the brief requires ----

    @Test
    fun `a misheard project name next to its own vocabulary is repaired`() {
        val result = correct("Next ring buffer is broken")
        assertEquals("Nyx ring buffer is broken", result.text)
    }

    @Test
    fun `next week survives a sentence that is otherwise all about Nyx`() {
        val result = correct("Next week I will work on Nyx")
        assertEquals("Next week I will work on Nyx", result.text)
        assertFalse(result.changed)
    }

    @Test
    fun `a related technical term is enough context to repair the name`() {
        val result = correct("I fixed next GTT allocation")
        assertEquals("I fixed Nyx GTT allocation", result.text)
    }

    @Test
    fun `an ordinary sentence with no project words is left exactly alone`() {
        val result = correct("I will do this next week")
        assertEquals("I will do this next week", result.text)
        assertFalse(result.changed)
    }

    // ---- the refusals ----

    /**
     * The load-bearing test. A protected collocation is checked before anything
     * is scored, so piling on evidence cannot defeat it — here "next" sits in a
     * sentence stuffed with every word that would otherwise convict it.
     */
    @Test
    fun `a protected bigram survives maximal contextual evidence`() {
        val result = correct("next week the GTT allocation and ring buffer both broke")
        assertTrue(
            "context overrode a protected collocation: ${result.text}",
            result.text.startsWith("next week")
        )
        assertFalse(result.changed)
    }

    /**
     * A misheard proper noun sits *inside* the noun phrase it belongs to. When
     * the related words are further away the sentence is usually fine as it
     * stands, so a common English word only gets a two-token window. With the
     * six-token window this same sentence would score 0.75 and be rewritten.
     */
    @Test
    fun `related words further down the sentence do not convict a common word`() {
        val result = correct("next patch fixed the ring buffer")
        assertEquals("next patch fixed the ring buffer", result.text)
    }

    @Test
    fun `a comma marks a common word as a discourse connective`() {
        val result = correct("Next, I looked at the ring buffer")
        assertEquals("Next, I looked at the ring buffer", result.text)
    }

    @Test
    fun `no vocabulary means nothing is ever rewritten`() {
        val result = TranscriptNormalizer.correct(
            Transcript.of("Next ring buffer is broken"),
            emptyList(),
            topics
        )
        assertEquals("Next ring buffer is broken", result.text)
    }

    /**
     * Blocking happens at the DAO — [com.naomi.app.data.database.dao.VocabularyDao.getUsableTerms]
     * filters blocked rows out — so from here a blocked term is simply a term
     * that was never supplied. This pins the consequence.
     */
    @Test
    fun `a term the normalizer was not given is a term it cannot apply`() {
        val result = TranscriptNormalizer.correct(
            Transcript.of("Next ring buffer is broken"),
            listOf(gtt, ringBuffer),
            topics
        )
        assertEquals("Next ring buffer is broken", result.text)
    }

    @Test
    fun `two equally plausible candidates cancel each other out`() {
        val nix = VocabularyTerm("Nix", "nix", "NKS", occurrences = 5)
        val result = TranscriptNormalizer.correct(
            Transcript.of("Next ring buffer is broken"),
            listOf(nyx, nix, ringBuffer),
            topics + "Nix > Graphics > Ring Buffer"
        )
        assertEquals("Next ring buffer is broken", result.text)
    }

    // ---- the recogniser's own second guess ----

    /**
     * The recogniser returns several hypotheses and Naomi used to keep only the
     * first. When the right word is sitting in hypothesis two, that is stronger
     * evidence than anything the correction engine can infer, and it stands in
     * for context the sentence does not have.
     */
    @Test
    fun `a losing hypothesis containing the term is evidence on its own`() {
        val transcript = Transcript(
            text = "next stalled again",
            utterances = listOf(
                Utterance(chosen = "next stalled again", alternatives = listOf("nyx stalled again"))
            )
        )
        val result = TranscriptNormalizer.correct(transcript, vocabulary, topics)
        assertEquals("Nyx stalled again", result.text)
    }

    /**
     * ...but only for a word that could plausibly have been misheard. A losing
     * hypothesis that swaps in a completely different-sounding word is the
     * recogniser guessing at grammar, not at a name.
     */
    @Test
    fun `a losing hypothesis cannot rewrite a word that sounds nothing like it`() {
        val transcript = Transcript(
            text = "the compiler stalled again",
            utterances = listOf(
                Utterance(chosen = "the compiler stalled again", alternatives = listOf("nyx stalled again"))
            )
        )
        val result = TranscriptNormalizer.correct(transcript, vocabulary, topics)
        assertEquals("the compiler stalled again", result.text)
    }

    /**
     * The hypotheses are whole sentences, so the alternative list says the
     * recogniser considered "nyx" for this audio but not for which word. Left
     * ungated that half-point attaches to every token, and here "the" sits two
     * words from two related terms — enough to convict it of being a project
     * name if sound were not still required.
     */
    @Test
    fun `alternative evidence does not spill onto the rest of the sentence`() {
        val transcript = Transcript(
            text = "the ring buffer stalled",
            utterances = listOf(
                Utterance(chosen = "the ring buffer stalled", alternatives = listOf("nyx ring buffer stalled"))
            )
        )
        val result = TranscriptNormalizer.correct(transcript, vocabulary, topics)
        assertEquals("the ring buffer stalled", result.text)
    }

    // ---- mechanics ----

    @Test
    fun `punctuation around a corrected word is preserved`() {
        val result = correct("Next ring buffer, again.")
        assertEquals("Nyx ring buffer, again.", result.text)
    }

    @Test
    fun `a change records what it replaced and how sure it was`() {
        val result = correct("Next ring buffer is broken")
        assertEquals(1, result.changes.size)
        val change = result.changes.first()
        assertEquals("Next", change.from)
        assertEquals("Nyx", change.to)
        assertEquals(0, change.tokenIndex)
        assertTrue(change.confidence >= TranscriptNormalizer.MIN_CONFIDENCE)
    }

    @Test
    fun `a word already spelled correctly is never reconsidered`() {
        val result = correct("Nyx ring buffer is broken")
        assertEquals("Nyx ring buffer is broken", result.text)
        assertFalse(result.changed)
    }

    @Test
    fun `blank input is returned untouched`() {
        assertEquals("", TranscriptNormalizer.correct(Transcript.EMPTY, vocabulary, topics).text)
    }
}
