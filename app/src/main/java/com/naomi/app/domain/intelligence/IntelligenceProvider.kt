package com.naomi.app.domain.intelligence

import com.naomi.app.domain.model.ExtractedKnowledge

/**
 * Something that can read a transcript and work out what it means.
 *
 * Two implementations exist: a small language model running on the device, and
 * a pure-heuristic engine. Both run entirely offline — Naomi holds no INTERNET
 * permission, so neither one can send a transcript anywhere even if it wanted to.
 *
 * Implementations return `null` rather than throwing when they cannot produce a
 * usable result, so the caller can fall through to a simpler provider instead of
 * failing the memory.
 */
interface IntelligenceProvider {

    /** Stable identifier, shown in Settings so the user knows what is running. */
    val id: String

    /** Human-readable name for Settings. */
    val displayName: String

    /**
     * Whether this provider can run right now. May be false on hardware that
     * doesn't support on-device inference, so this is checked per call rather
     * than cached forever.
     */
    suspend fun isAvailable(): Boolean

    /**
     * @param transcript     what the user said
     * @param existingTopics root-to-leaf paths already in the user's tree, e.g.
     *                       ["Nyx > Graphics", "Nyx > Memory > GTT"]. Passing
     *                       these is what lets a provider file a memory under a
     *                       topic the user already has instead of inventing a
     *                       near-duplicate.
     * @return the extracted knowledge, or null if this provider couldn't do it
     */
    suspend fun analyze(
        transcript: String,
        existingTopics: List<String>
    ): ExtractedKnowledge?
}
