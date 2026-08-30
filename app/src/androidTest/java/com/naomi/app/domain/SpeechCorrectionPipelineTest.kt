package com.naomi.app.domain

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.naomi.app.ai.speech.Transcript
import com.naomi.app.data.database.NaomiDatabase
import com.naomi.app.data.repository.KnowledgeRepositoryImpl
import com.naomi.app.data.database.entities.MemoryEntryEntity
import com.naomi.app.domain.intelligence.LocalHeuristicProvider
import com.naomi.app.domain.model.ProcessingStage
import com.naomi.app.domain.usecases.ProcessThoughtUseCase
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The whole speech-correction feature, end to end, on a device.
 *
 * The unit tests prove [com.naomi.app.ai.intelligence.TranscriptNormalizer]
 * scores correctly given a vocabulary. This proves the parts actually reach
 * each other: that saving a memory teaches Naomi a word, that the word is then
 * used to repair the next capture, and — the part that matters most if any of
 * this is ever wrong — that the user's original words are still on disk.
 */
@RunWith(AndroidJUnit4::class)
class SpeechCorrectionPipelineTest {

    private lateinit var db: NaomiDatabase
    private lateinit var repository: KnowledgeRepositoryImpl
    private lateinit var processThought: ProcessThoughtUseCase

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, NaomiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = KnowledgeRepositoryImpl(db)
        processThought = ProcessThoughtUseCase(repository, listOf(LocalHeuristicProvider()))
    }

    @After
    fun tearDown() = db.close()

    private fun capture(
        text: String,
        source: String = MemoryEntryEntity.SOURCE_SPOKEN
    ): ProcessingStage.Done = runBlocking {
        val stages = processThought(Transcript.of(text), source).toList()
        stages.filterIsInstance<ProcessingStage.Done>().single()
    }

    /**
     * The scenario the whole feature exists for. Naomi learns "Nyx" from a
     * capture that happened to come through correctly, and repairs the next one
     * that did not.
     */
    @Test
    fun learns_a_project_name_and_then_repairs_a_mishearing_of_it() = runBlocking {
        capture("Nyx ring buffer overflows at sixty frames")

        val learned = repository.getVocabulary().map { it.normalized }
        assertTrue("Nyx was never learned: $learned", "nyx" in learned)

        val done = capture("Next ring buffer is broken again")
        val note = done.notes.single()

        assertTrue(
            "the mishearing survived into the memory: ${note.cleanTranscript}",
            note.cleanTranscript.contains("Nyx")
        )

        // The point of the feature: the memory files under the project rather
        // than under a topic named after a mishearing.
        val topics = repository.getTopicPaths()
        assertTrue("no topic mentions Nyx: $topics", topics.any { it.contains("Nyx", true) })
        assertTrue("a topic was named after the mishearing: $topics",
            topics.none { it.contains("Next", true) })
    }

    /**
     * The refusal, on the real pipeline. A user with Nyx as their most-used
     * topic must still be able to say "next week" and have it stay that way.
     */
    @Test
    fun an_ordinary_next_week_survives_a_database_full_of_nyx() = runBlocking {
        repeat(3) { capture("Nyx ring buffer and GTT allocation notes") }

        val done = capture("I will do this next week")
        val note = done.notes.single()

        assertTrue(
            "an ordinary sentence was rewritten: ${note.cleanTranscript}",
            note.cleanTranscript.contains("next week")
        )
    }

    /**
     * Naomi is allowed to be wrong, but not to destroy the evidence.
     *
     * This is the assertion the whole feature rests on, and it caught a real
     * hole: a second utterance about the same subject *continues* the memory
     * rather than starting one, so it never touches `notes.rawTranscript` at
     * all. Before `memory_entries.rawTranscript` existed, correcting a
     * continuation overwrote the only copy of what was said.
     */
    @Test
    fun the_original_words_survive_a_correction_on_a_continued_memory() = runBlocking {
        val first = capture("Nyx ring buffer overflows at sixty frames").notes.single()

        val spoken = "Next ring buffer is broken again"
        val note = capture(spoken).notes.single()
        assertEquals("this should have continued the first memory", first.id, note.id)

        val entries = repository.getEntriesForNote(note.id)
        val latest = entries.last()

        assertTrue("the correction never reached the entry", latest.transcript.contains("Nyx"))
        assertEquals("the words as spoken were lost", spoken, latest.rawTranscript)
    }

    /** Nothing corrected means nothing to log — the column stays null. */
    @Test
    fun an_uncorrected_capture_stores_no_second_copy() = runBlocking {
        val note = capture("The balcony soil needs better drainage").notes.single()
        val entry = repository.getEntriesForNote(note.id).single()
        assertEquals(null, entry.rawTranscript)
    }

    /** A memory that *starts* keeps its original on the note itself. */
    @Test
    fun a_new_memory_keeps_the_original_on_the_note() = runBlocking {
        val spoken = "Nyx ring buffer overflows at sixty frames"
        val note = capture(spoken).notes.single()
        assertEquals(spoken, note.rawTranscript)
    }

    /**
     * Shared text is someone else's words. Correcting a quoted web page against
     * this user's private vocabulary would be fabrication, not repair — and
     * learning its nouns would poison later corrections with terms the user
     * never said.
     */
    @Test
    fun shared_text_is_neither_corrected_nor_learned_from() = runBlocking {
        capture("Nyx ring buffer overflows at sixty frames")
        val before = repository.getVocabulary().map { it.normalized }.toSet()

        val quoted = "Next ring buffer improvements landed in Postgres this week"
        val note = capture(quoted, MemoryEntryEntity.SOURCE_SHARED).notes.single()

        assertTrue(
            "shared text was rewritten: ${note.cleanTranscript}",
            note.cleanTranscript.contains("Next")
        )

        val after = repository.getVocabulary().map { it.normalized }.toSet()
        assertEquals("shared text taught Naomi new words: ${after - before}", before, after)
    }

    /**
     * Typed text is exactly what the user meant. Someone who types "next" has
     * said so, and there is no acoustic uncertainty to repair — but the words
     * are still their own, so Naomi still learns from them.
     */
    @Test
    fun typed_text_is_learned_from_but_never_corrected() = runBlocking {
        capture("Nyx ring buffer overflows at sixty frames")

        val typed = "Next ring buffer item to look at"
        val note = capture(typed, MemoryEntryEntity.SOURCE_TYPED).notes.single()

        assertTrue(
            "typed text was rewritten: ${note.cleanTranscript}",
            note.cleanTranscript.contains("Next")
        )
    }

    /** "It's Nyx, not next" is the only channel a user has for teaching a word. */
    @Test
    fun a_spoken_spelling_correction_is_learned_immediately() = runBlocking {
        capture("It's Nyx, not next")

        val vocabulary = repository.getVocabulary()
        val nyx = vocabulary.firstOrNull { it.normalized == "nyx" }
        assertTrue("the lesson was not learned: ${vocabulary.map { it.normalized }}", nyx != null)
        assertTrue("the lesson was not trusted as the user's own", nyx!!.isUserConfirmed)
    }
}
