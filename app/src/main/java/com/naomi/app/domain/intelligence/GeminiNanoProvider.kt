package com.naomi.app.domain.intelligence

import android.util.Log
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.naomi.app.ai.intelligence.TemporalParser
import com.naomi.app.domain.model.ExtractedEntity
import com.naomi.app.domain.model.ExtractedKnowledge
import com.naomi.app.domain.model.ExtractedTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * Understanding via Gemini Nano, running inside Android's AICore system service.
 *
 * The model is provided and updated by the platform rather than shipped or
 * downloaded by Naomi, which is what lets the app keep zero INTERNET permission
 * while still doing real language understanding. Nothing here can reach the
 * network — inference happens in a system service on the device.
 *
 * Nano is a small model, so every response is treated as untrusted: the JSON is
 * salvaged, validated, and rejected outright if it doesn't hold up. A rejected
 * response returns null and the caller falls back to the heuristic engine, which
 * is always better than persisting a hallucinated memory.
 */
class GeminiNanoProvider(
    private val modelProvider: () -> GenerativeModel = { Generation.getClient() }
) : IntelligenceProvider {

    override val id: String = "gemini-nano"
    override val displayName: String = "Gemini Nano"

    private val model: GenerativeModel by lazy { modelProvider() }

    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            // DOWNLOADABLE means AICore has the capability but hasn't fetched the
            // weights yet. Treated as unavailable for this call rather than
            // blocking a capture behind a system download — see ensureReady().
            model.checkStatus() == FeatureStatus.AVAILABLE
        } catch (e: Exception) {
            Log.w(TAG, "Nano availability check failed", e)
            false
        }
    }

    /**
     * Asks AICore to fetch the model if the device supports it but hasn't
     * downloaded it. Called from Settings, never from the capture path — the
     * user should never be left waiting on a system download mid-thought.
     */
    suspend fun ensureReady(): Boolean = withContext(Dispatchers.IO) {
        try {
            when (model.checkStatus()) {
                FeatureStatus.AVAILABLE -> true
                FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING -> {
                    model.download().collect { }
                    model.checkStatus() == FeatureStatus.AVAILABLE
                }
                else -> false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Nano download failed", e)
            false
        }
    }

    override suspend fun analyze(
        transcript: String,
        existingTopics: List<String>
    ): ExtractedKnowledge? = withContext(Dispatchers.IO) {
        val raw = try {
            // Bounded so a wedged inference can't strand the capture UI. The
            // heuristic engine picks up the moment this elapses.
            withTimeoutOrNull(INFERENCE_TIMEOUT_MS) {
                model.generateContent(buildPrompt(transcript, existingTopics))
                    .candidates
                    .firstOrNull()
                    ?.text
            }
        } catch (e: Exception) {
            Log.w(TAG, "Nano inference failed", e)
            null
        } ?: return@withContext null

        parse(raw, transcript)
    }

    /**
     * The existing topic tree goes in the prompt because reusing the user's own
     * vocabulary is the whole job — without it the model invents a fresh
     * near-duplicate topic for every memory.
     */
    private fun buildPrompt(transcript: String, existingTopics: List<String>): String {
        val topicBlock = if (existingTopics.isEmpty()) {
            "(none yet — this is their first memory)"
        } else {
            existingTopics.take(MAX_TOPICS_IN_PROMPT).joinToString("\n") { "- $it" }
        }

        return """
            You organise someone's spoken notes. Reply with JSON only — no prose, no code fences.

            Their existing topics:
            $topicBlock

            Rules:
            - Reuse an existing topic path when the note belongs there. Only invent a new one if it genuinely fits nowhere.
            - title: 2-5 words, specific, no trailing punctuation.
            - summary: one sentence, their meaning, not a paraphrase of these instructions.
            - idea / decision: only if they actually expressed one, else null.
            - tasks: only things they said they need to do. "due" is their own wording ("tomorrow", "Friday") or null.

            Schema:
            {"title":string,"topic_path":[string],"summary":string,"idea":string|null,"decision":string|null,"tasks":[{"title":string,"due":string|null}]}

            Note:
            $transcript
        """.trimIndent()
    }

    /**
     * Salvages and validates a response. Small models wrap JSON in prose or code
     * fences often enough that finding the object is worth doing, but anything
     * structurally wrong is discarded rather than half-used.
     */
    private fun parse(raw: String, transcript: String): ExtractedKnowledge? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null

        return try {
            val json = JSONObject(raw.substring(start, end + 1))

            val title = json.optString("title").trim()
            if (title.isBlank() || title.length > MAX_TITLE_LENGTH) return null

            val path = json.optJSONArray("topic_path")
                ?.let { array -> (0 until array.length()).mapNotNull { array.optString(it).trim().ifBlank { null } } }
                ?.take(MAX_TOPIC_DEPTH)
                .orEmpty()
            if (path.isEmpty()) return null

            val tasksArray = json.optJSONArray("tasks")
            val tasks = buildList {
                for (i in 0 until (tasksArray?.length() ?: 0)) {
                    val task = tasksArray!!.optJSONObject(i) ?: continue
                    val taskTitle = task.optString("title").trim()
                    if (taskTitle.isBlank()) continue
                    // The model reports the user's wording; resolving it to an
                    // actual instant stays with our own parser so a due date is
                    // never a hallucinated timestamp.
                    val due = task.optString("due").takeIf { it.isNotBlank() && it != "null" }
                    val parsed = due?.let { TemporalParser.parse(it) }
                    add(
                        ExtractedTask(
                            title = taskTitle,
                            deadline = parsed?.displayText ?: due,
                            dueAt = parsed?.dueAt
                        )
                    )
                }
            }

            ExtractedKnowledge(
                title = title,
                summary = json.optString("summary").trim().ifBlank { transcript.take(200) },
                topicPath = path,
                idea = json.optNullableString("idea"),
                decision = json.optNullableString("decision"),
                tasks = tasks,
                entities = emptyList<ExtractedEntity>()
            )
        } catch (e: Exception) {
            Log.w(TAG, "Nano returned unusable JSON", e)
            null
        }
    }

    /** `optString` yields the literal "null" for JSON nulls, which is never wanted. */
    private fun JSONObject.optNullableString(key: String): String? =
        optString(key).trim().takeIf { it.isNotBlank() && it != "null" }

    private companion object {
        const val TAG = "GeminiNano"
        const val INFERENCE_TIMEOUT_MS = 20_000L
        const val MAX_TOPICS_IN_PROMPT = 40
        const val MAX_TITLE_LENGTH = 80
        const val MAX_TOPIC_DEPTH = 3
    }
}
