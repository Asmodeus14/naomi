package com.naomi.app.ai

import com.naomi.app.ai.intelligence.KeyphraseExtractor
import com.naomi.app.ai.intelligence.Lexicon
import com.naomi.app.ai.intelligence.LocalIntelligenceEngine
import com.naomi.app.ai.intelligence.TaskExtractor
import com.naomi.app.ai.intelligence.TemporalParser
import com.naomi.app.ai.intelligence.TopicMatcher
import com.naomi.app.ai.intelligence.TopicResolver
import com.naomi.app.data.database.entities.TopicEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The previous version of this suite asserted on hardcoded demo strings with
 * loose `contains` checks, which let real defects pass. These tests assert on
 * behaviour, use exact equality where the output is deterministic, and cover the
 * negative cases — a topic engine that never proves two distinct topics stay
 * distinct is not being tested at all.
 */
class IntelligenceEngineTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val monday = LocalDateTime.of(2026, 3, 2, 9, 0) // a Monday

    private fun topic(id: Long, name: String) =
        TopicEntity(id = id, name = name, normalizedName = TopicMatcher.normalize(name))

    // ---- Topic matching -----------------------------------------------------

    @Test
    fun `distinct short topics are not merged`() {
        // The regression that motivated raising the threshold: "2nf" and "3nf"
        // differ by one character in three, which scored 0.67 and silently
        // merged two unrelated concepts.
        val existing = listOf(topic(1, "2NF"))
        assertNull(
            "3NF must not resolve to the existing 2NF topic",
            TopicMatcher.findBestMatch("3NF", existing)
        )
        assertNull(TopicMatcher.findBestMatch("1NF", existing))
    }

    @Test
    fun `unrelated four letter topics are not merged`() {
        val existing = listOf(topic(1, "Work"))
        assertNull(TopicMatcher.findBestMatch("Word", existing))
    }

    @Test
    fun `genuine variants of the same topic do merge`() {
        val existing = listOf(topic(1, "Ring Buffer"))
        assertEquals(1L, TopicMatcher.findBestMatch("ring buffers", existing)?.id)
        assertEquals(1L, TopicMatcher.findBestMatch("Ring Buffer issue", existing)?.id)
    }

    @Test
    fun `a longer phrase about the same subject attaches to the existing topic`() {
        // Observed on device: a second memory about the same tomato seedlings
        // created a duplicate root topic "Tomato Seedlings Look Taller" beside
        // the "Tomato Seedlings" it should have joined. Jaccard scored the pair
        // at 0.5 and the threshold rejected it.
        val existing = listOf(topic(1, "Tomato Seedlings"))
        assertEquals(
            1L,
            TopicMatcher.findBestMatch("tomato seedlings look taller", existing)?.id
        )
    }

    @Test
    fun `a single shared word does not merge two topics`() {
        // The other half of containment: "Soil" must not swallow a much longer
        // name just because the word appears in it.
        val existing = listOf(topic(1, "Soil"))
        assertNull(
            TopicMatcher.findBestMatch("balcony soil drainage problem", existing)
        )
    }

    @Test
    fun `containment does not merge topics that merely share a word`() {
        val existing = listOf(topic(1, "Tomato Seedlings"))
        assertNull(TopicMatcher.findBestMatch("tomato sauce", existing))
    }

    @Test
    fun `normalize strips decoration but keeps the subject`() {
        assertEquals("nyx", TopicMatcher.normalize("Project Nyx"))
        assertEquals("dbms", TopicMatcher.normalize("DBMS Lecture"))
    }

    // ---- Topic placement ----------------------------------------------------

    @Test
    fun `a second memory on the same subject reuses the topic`() {
        // The whole hierarchical-memory promise in one assertion: talking about
        // the same thing twice must deepen one topic, not create two.
        val first = TopicResolver.resolve(
            KeyphraseExtractor.extract(
                "Repotted the tomato seedlings today. The balcony soil needs better drainage."
            ),
            existingTopics = emptyList()
        )
        assertFalse("a first memory has nothing to match", first.matchedExisting)

        // Persist what the first memory produced, the way the repository does.
        val saved = first.path.mapIndexed { index, name ->
            topic(index + 1L, name)
        }

        val second = TopicResolver.resolve(
            KeyphraseExtractor.extract(
                "The tomato seedlings look taller this week. " +
                    "I should move them to a bigger pot on Friday."
            ),
            existingTopics = saved
        )

        assertTrue(
            "second memory should attach to the existing tree, got ${second.path}",
            second.matchedExisting
        )
        assertEquals(
            "it should land under the topic the first memory created",
            saved.first().name,
            second.path.first()
        )
    }

    @Test
    fun `an unrelated memory does not attach to an existing topic`() {
        val saved = listOf(topic(1, "Tomato Seedlings"))
        val result = TopicResolver.resolve(
            KeyphraseExtractor.extract("The DBMS lecture covered partial dependency in 2NF."),
            existingTopics = saved
        )
        assertFalse(
            "an unrelated subject must not be filed under gardening, got ${result.path}",
            result.matchedExisting
        )
    }

    @Test
    fun `an unrecognisable passage goes to the inbox rather than inventing a topic`() {
        // "Remember to call the plumber" used to mint a root topic named
        // "REMEMBER" — the first word over three characters, uppercased.
        val result = TopicResolver.resolve(
            KeyphraseExtractor.extract("um so yeah anyway"),
            existingTopics = emptyList()
        )
        assertEquals(listOf(TopicResolver.INBOX), result.path)
    }

    // ---- Titles -------------------------------------------------------------

    @Test
    fun `title is derived from the passage not a template`() {
        val title = KeyphraseExtractor.generateTitle(
            "The ring buffer is working now but synchronization is still broken."
        )
        // The exact phrase depends on scoring, but it must come from the text
        // and must not be the old hardcoded "Nyx — ... Synchronization" shape.
        assertTrue("title should be drawn from the passage", title.isNotBlank())
        assertFalse(title.contains("—"))
        assertTrue(
            "expected a phrase from the passage, got '$title'",
            title.lowercase().split(" ").any {
                it in setOf("ring", "buffer", "synchronization", "working", "broken")
            }
        )
    }

    @Test
    fun `titles differ for different subjects`() {
        // The old engine returned the same title for every note in a domain.
        val a = KeyphraseExtractor.generateTitle("Normalization removes partial dependency in 2NF.")
        val b = KeyphraseExtractor.generateTitle("I repotted the tomato seedlings on the balcony.")
        assertFalse("unrelated passages must not share a title", a.equals(b, ignoreCase = true))
    }

    @Test
    fun `title is a noun phrase not a clause fragment`() {
        // Observed on device: this produced "Balcony Soil Needs Better", a
        // truncated clause, because verbs and comparatives were allowed inside
        // a candidate phrase.
        val title = KeyphraseExtractor.generateTitle(
            "Repotted the tomato seedlings today. The balcony soil needs better drainage."
        )
        val words = title.lowercase().split(" ")
        assertFalse(
            "title '$title' should not contain a predicate verb",
            words.any { it in setOf("needs", "need", "is", "was", "has") }
        )
        assertFalse(
            "title '$title' should not end on a comparative",
            words.last() in setOf("better", "worse", "more", "less")
        )
    }

    @Test
    fun `a bare verb breaks a phrase just as its inflections do`() {
        // "look" was absent from the breaker list while "looks" and "looked"
        // were present, so this titled a memory "Tomato Seedlings Look Taller".
        val title = KeyphraseExtractor.generateTitle(
            "The tomato seedlings look taller this week. " +
                "I should move them to a bigger pot on Friday."
        )
        val words = title.lowercase().split(" ")
        assertFalse(
            "title '$title' should not contain a bare verb",
            words.any { it in setOf("look", "move", "seem", "feel") }
        )
        assertFalse(
            "title '$title' should not contain a comparative",
            words.any { it in setOf("taller", "bigger", "smaller", "longer") }
        )
    }

    @Test
    fun `when something happens is never what it is about`() {
        // Observed on device: this produced a root topic named
        // "Fence Waits Tomorrow". The date is already captured structurally as
        // a due date; letting it into the name corrupts the topic and repeats
        // information the memory holds properly elsewhere.
        val text = "The ring buffer in Nyx is stable now. Synchronisation between " +
            "the producer and consumer still drops frames. " +
            "I need to profile the fence waits tomorrow."

        val title = KeyphraseExtractor.generateTitle(text)
        assertFalse(
            "title '$title' should not contain a date word",
            title.lowercase().split(" ").any { it in setOf("tomorrow", "today", "friday", "week") }
        )

        val placement = TopicResolver.resolve(
            KeyphraseExtractor.extract(text),
            existingTopics = emptyList()
        )
        assertFalse(
            "topic path ${placement.path} should not contain a date word",
            placement.path.any { name ->
                name.lowercase().split(" ").any { it in Lexicon.temporalWords }
            }
        )
    }

    @Test
    fun `a two word subject outranks a longer rambling phrase`() {
        // RAKE sums word scores, which in a passage where nothing repeats means
        // the longest run always wins — "fence waits tomorrow" beat "ring
        // buffer" purely on length.
        val phrases = KeyphraseExtractor.extract(
            "The ring buffer in Nyx is stable now. Synchronisation between " +
                "the producer and consumer still drops frames."
        )
        assertEquals("ring buffer", phrases.first().text.lowercase())
    }

    @Test
    fun `a quantifier never leads a keyphrase`() {
        // Observed on device: "uses too much memory under load" nested a
        // subtopic called "Much Memory" under Ring Buffer.
        val phrases = KeyphraseExtractor.extract(
            "The ring buffer uses too much memory under load."
        )
        assertFalse(
            "no phrase should start with a quantifier, got ${phrases.map { it.text }}",
            phrases.any { it.text.lowercase().startsWith("much ") }
        )
    }

    @Test
    fun `title survives a passage with no usable keyphrase`() {
        assertTrue(KeyphraseExtractor.generateTitle("um so yeah").isNotBlank())
        assertEquals("Untitled Memory", KeyphraseExtractor.generateTitle("   "))
    }

    // ---- Tasks --------------------------------------------------------------

    @Test
    fun `task title excludes the trigger phrase and trailing preposition`() {
        // Previously produced "Finish the frontend by" — the trigger leaked in
        // via match.value and the dangling preposition was never stripped.
        val tasks = TaskExtractor.extract("I'll finish the frontend by Friday.", monday, zone)
        assertEquals(1, tasks.size)
        assertEquals("Finish the frontend", tasks.first().title)
    }

    @Test
    fun `modal verb is stripped from the task title`() {
        // Previously produced "Should revise 2NF before".
        val tasks = TaskExtractor.extract("I should revise 2NF before Friday.", monday, zone)
        assertEquals(1, tasks.size)
        assertEquals("Revise 2NF", tasks.first().title)
    }

    @Test
    fun `multiple tasks are all extracted`() {
        // The old extractor broke after the first match and capped at ~2 tasks.
        val tasks = TaskExtractor.extract(
            "I need to call the bank. I need to book a flight. Remind me to water the plants.",
            monday, zone
        )
        assertEquals(3, tasks.size)
    }

    @Test
    fun `each task keeps its own deadline`() {
        val tasks = TaskExtractor.extract(
            "I need to call Sam tomorrow. I need to file taxes on Friday.",
            monday, zone
        )
        assertEquals(2, tasks.size)
        val due = tasks.mapNotNull { it.dueAt }
        assertEquals("both tasks should carry a resolved date", 2, due.size)
        assertTrue("the two deadlines must differ", due[0] != due[1])
    }

    @Test
    fun `passages with no stated intent produce no tasks`() {
        assertTrue(TaskExtractor.extract("The weather was nice today.", monday, zone).isEmpty())
    }

    // ---- Dates --------------------------------------------------------------

    @Test
    fun `tomorrow resolves to an actual instant`() {
        val parsed = TemporalParser.parse("call her tomorrow", monday, zone)
        assertNotNull(parsed)
        assertEquals("Tomorrow", parsed!!.displayText)
        val due = java.time.Instant.ofEpochMilli(parsed.dueAt).atZone(zone).toLocalDate()
        assertEquals(monday.toLocalDate().plusDays(1), due)
    }

    @Test
    fun `next friday resolves ahead of this friday`() {
        val thisFriday = TemporalParser.parse("on friday", monday, zone)!!
        val nextFriday = TemporalParser.parse("next friday", monday, zone)!!
        assertTrue("next friday must be later", nextFriday.dueAt > thisFriday.dueAt)
    }

    @Test
    fun `relative counts are parsed`() {
        val parsed = TemporalParser.parse("in 3 days", monday, zone)
        assertNotNull(parsed)
        val due = java.time.Instant.ofEpochMilli(parsed!!.dueAt).atZone(zone).toLocalDate()
        assertEquals(monday.toLocalDate().plusDays(3), due)
    }

    @Test
    fun `text with no date returns null`() {
        assertNull(TemporalParser.parse("fix the parser"))
    }

    // ---- Segmentation -------------------------------------------------------

    @Test
    fun `segments split at the marker that appears first in the text`() {
        // The old implementation iterated the marker list rather than the text,
        // so whichever marker came first in the list won and the leading segment
        // kept two unrelated subjects.
        val segments = LocalIntelligenceEngine.segmentSpeech(
            "Fixed the parser today. On another note, I need to book a flight. " +
                "Also I need to buy milk."
        )
        assertEquals(3, segments.size)
        assertTrue(segments[0].contains("parser"))
        assertFalse("first segment must not absorb later subjects", segments[0].contains("flight"))
        assertTrue(segments[1].contains("flight"))
        assertTrue(segments[2].contains("milk"))
    }

    @Test
    fun `a single thought is not split`() {
        val segments = LocalIntelligenceEngine.segmentSpeech("The ring buffer works but sync is broken.")
        assertEquals(1, segments.size)
    }

    @Test
    fun `blank input produces no segments`() {
        assertTrue(LocalIntelligenceEngine.segmentSpeech("   ").isEmpty())
    }

    // ---- Whole-passage analysis --------------------------------------------

    @Test
    fun `analysis works on a subject the engine has never seen`() {
        // The engine used to be hardcoded around two demo domains and produced
        // junk for anything else.
        val result = LocalIntelligenceEngine.analyze(
            "The sourdough starter doubled overnight, so the kitchen is warm enough now."
        )
        assertTrue(result.title.isNotBlank())
        assertTrue(result.summary.isNotBlank())
        assertTrue("keyphrases should be extracted", result.keyphrases.isNotEmpty())
    }

    @Test
    fun `summary drops the conversational opener`() {
        val result = LocalIntelligenceEngine.analyze("Hey Naomi, the deploy finished cleanly.")
        assertFalse(result.summary.contains("Hey Naomi", ignoreCase = true))
        assertTrue(result.summary.contains("deploy", ignoreCase = true))
    }

    @Test
    fun `an idea is surfaced only when the speaker flags one`() {
        val withIdea = LocalIntelligenceEngine.analyze("I think PIPE_CONTROL might fix the flush issue.")
        assertNotNull("an explicit 'I think' should surface an idea", withIdea.idea)

        val withoutIdea = LocalIntelligenceEngine.analyze("The flush issue is still open.")
        assertNull("no idea should be invented", withoutIdea.idea)
    }

    @Test
    fun `a decision is surfaced only when the speaker flags one`() {
        val decided = LocalIntelligenceEngine.analyze("We decided to ship on Tuesday.")
        assertNotNull(decided.decision)
        assertNull(LocalIntelligenceEngine.analyze("Shipping is hard.").decision)
    }
}
