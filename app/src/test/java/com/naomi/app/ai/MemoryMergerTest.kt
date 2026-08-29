package com.naomi.app.ai

import com.naomi.app.ai.intelligence.MemoryMerger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Merging is the one operation here that can destroy information the user gave
 * Naomi: continuing the wrong memory rewrites its summary and buries a distinct
 * thought inside another's history, and nothing in the UI would show that it
 * happened. So most of these tests assert that merging does *not* occur.
 */
class MemoryMergerTest {

    private fun candidates(vararg titles: String) =
        titles.mapIndexed { index, title -> MemoryMerger.Candidate((index + 1).toLong(), title) }

    @Test
    fun `the same subject said again continues the existing memory`() {
        val decision = MemoryMerger.decide("Ring Buffer", candidates("Ring Buffer"))
        assertTrue(decision is MemoryMerger.Decision.Continue)
        assertEquals(1L, (decision as MemoryMerger.Decision.Continue).noteId)
    }

    @Test
    fun `a more specific update continues the memory and keeps the broader name`() {
        // "I fixed the ring buffer vertex count" is an update to Ring Buffer,
        // not a new subject — and the memory should still be called Ring Buffer
        // afterwards, because that is the thing being remembered.
        val decision = MemoryMerger.decide("Ring Buffer Vertex Count", candidates("Ring Buffer"))
        assertTrue("expected a continuation, got $decision", decision is MemoryMerger.Decision.Continue)
        assertEquals("Ring Buffer", (decision as MemoryMerger.Decision.Continue).title)
    }

    @Test
    fun `different subjects under the same topic stay apart`() {
        val decision = MemoryMerger.decide("Render Pipeline", candidates("Ring Buffer"))
        assertEquals(MemoryMerger.Decision.StartNew, decision)
    }

    @Test
    fun `near-miss technical names are never merged`() {
        // The 2NF/3NF failure mode, at memory level: one character apart and
        // completely different concepts.
        assertEquals(MemoryMerger.Decision.StartNew, MemoryMerger.decide("3NF", candidates("2NF")))
        assertEquals(MemoryMerger.Decision.StartNew, MemoryMerger.decide("1NF", candidates("2NF")))
    }

    @Test
    fun `merging is stricter than topic matching`() {
        // These two belong in the same topic but are not the same memory. If the
        // merge threshold ever gets lowered to the topic threshold, this fails.
        assertTrue(MemoryMerger.MERGE_THRESHOLD > com.naomi.app.ai.intelligence.TopicMatcher.DEFAULT_THRESHOLD)
    }

    @Test
    fun `the best of several candidates wins`() {
        val decision = MemoryMerger.decide(
            "Slab Allocator",
            candidates("Ring Buffer", "Slab Allocator", "Render Pipeline")
        )
        assertTrue(decision is MemoryMerger.Decision.Continue)
        assertEquals(2L, (decision as MemoryMerger.Decision.Continue).noteId)
    }

    @Test
    fun `an empty or unusable title never merges`() {
        assertEquals(MemoryMerger.Decision.StartNew, MemoryMerger.decide("", candidates("Ring Buffer")))
        assertEquals(MemoryMerger.Decision.StartNew, MemoryMerger.decide("Ring Buffer", emptyList()))
    }
}
