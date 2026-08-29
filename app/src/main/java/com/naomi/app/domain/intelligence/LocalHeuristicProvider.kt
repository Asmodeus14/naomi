package com.naomi.app.domain.intelligence

import com.naomi.app.ai.intelligence.LocalIntelligenceEngine
import com.naomi.app.domain.model.ExtractedKnowledge

/**
 * The always-available provider.
 *
 * Pure heuristics — no model, no hardware requirement, a few milliseconds. It is
 * the floor beneath every other provider: whatever the device is, this works.
 */
class LocalHeuristicProvider : IntelligenceProvider {

    override val id: String = "local"
    override val displayName: String = "Built-in"

    override suspend fun isAvailable(): Boolean = true

    override suspend fun analyze(
        transcript: String,
        existingTopics: List<String>
    ): ExtractedKnowledge {
        // Topic placement is resolved downstream from the returned keyphrases,
        // where the full topic entities (with parent links) are available.
        return LocalIntelligenceEngine.analyze(transcript)
    }
}
