package com.naomi.app.data.repository

import androidx.room.withTransaction
import com.naomi.app.ai.intelligence.MemoryMerger
import com.naomi.app.ai.intelligence.TopicMatcher
import com.naomi.app.ai.intelligence.TopicResolver
import com.naomi.app.data.database.NaomiDatabase
import com.naomi.app.data.database.entities.*
import com.naomi.app.domain.model.*
import com.naomi.app.domain.repository.KnowledgeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class KnowledgeRepositoryImpl(
    private val db: NaomiDatabase
) : KnowledgeRepository {

    private val topicDao = db.topicDao()
    private val noteDao = db.noteDao()
    private val taskDao = db.taskDao()
    private val memoryEntryDao = db.memoryEntryDao()
    private val entityRefDao = db.entityRefDao()
    private val relationshipDao = db.topicRelationshipDao()

    override fun getRootTopicsFlow(): Flow<List<TopicEntity>> = topicDao.getRootTopicsFlow()

    override suspend fun getRootTopics(): List<TopicEntity> = withContext(Dispatchers.IO) {
        topicDao.getRootTopics()
    }

    override fun getSubtopicsFlow(parentId: Long): Flow<List<TopicEntity>> = topicDao.getSubtopicsFlow(parentId)

    override suspend fun getSubtopics(parentId: Long): List<TopicEntity> = withContext(Dispatchers.IO) {
        topicDao.getSubtopics(parentId)
    }

    override suspend fun getAllTopics(): List<TopicEntity> = withContext(Dispatchers.IO) {
        topicDao.getAllTopics()
    }

    override fun getAllTopicsFlow(): Flow<List<TopicEntity>> = topicDao.getAllTopicsFlow()

    override suspend fun getTopicPaths(): List<String> = withContext(Dispatchers.IO) {
        val all = topicDao.getAllTopics()
        val byId = all.associateBy { it.id }

        all.map { topic ->
            val chain = mutableListOf<String>()
            var current: TopicEntity? = topic
            val guard = mutableSetOf<Long>()
            while (current != null && guard.add(current.id)) {
                chain.add(current.name)
                current = current.parentId?.let { byId[it] }
            }
            chain.reversed().joinToString(" > ")
        }.sorted()
    }

    override suspend fun getTopicById(id: Long): TopicEntity? = withContext(Dispatchers.IO) {
        topicDao.getById(id)
    }

    override suspend fun getTopicByNormalizedName(name: String): TopicEntity? = withContext(Dispatchers.IO) {
        topicDao.getByNormalizedName(TopicMatcher.normalize(name))
    }

    override suspend fun insertOrGetTopic(name: String, parentId: Long?): TopicEntity =
        withContext(Dispatchers.IO) { resolveTopic(name, parentId) }

    /**
     * Finds or creates the topic called [name] under [parentId].
     *
     * Matching is scoped to siblings on purpose: two different parents may
     * legitimately each have a "Notes" or "Ideas" child, and collapsing those
     * into one node would merge unrelated parts of the tree.
     */
    private suspend fun resolveTopic(name: String, parentId: Long?): TopicEntity {
        val siblings = if (parentId == null) topicDao.getRootTopics() else topicDao.getSubtopics(parentId)
        val matched = TopicMatcher.findBestMatch(name, siblings)
        val now = System.currentTimeMillis()

        if (matched != null) {
            val touched = matched.copy(updatedAt = now)
            topicDao.update(touched)
            return touched
        }

        val parent = parentId?.let { topicDao.getById(it) }
        val newTopic = TopicEntity(
            name = name,
            normalizedName = TopicMatcher.normalize(name),
            parentId = parentId,
            depth = (parent?.depth ?: -1) + 1,
            createdAt = now,
            updatedAt = now
        )
        return newTopic.copy(id = topicDao.insert(newTopic))
    }

    override suspend fun updateTopic(topic: TopicEntity) = withContext(Dispatchers.IO) {
        topicDao.update(topic)
    }

    override fun getRecentNotesFlow(limit: Int): Flow<List<NoteEntity>> = noteDao.getRecentNotesFlow(limit)

    override suspend fun getRecentNotes(limit: Int): List<NoteEntity> = withContext(Dispatchers.IO) {
        noteDao.getRecentNotes(limit)
    }

    override fun getNotesForTopicFlow(topicId: Long): Flow<List<NoteEntity>> = noteDao.getNotesForTopicFlow(topicId)

    override suspend fun getNotesForTopic(topicId: Long): List<NoteEntity> = withContext(Dispatchers.IO) {
        noteDao.getNotesForTopic(topicId)
    }

    override suspend fun getNoteById(id: Long): NoteEntity? = withContext(Dispatchers.IO) {
        noteDao.getById(id)
    }

    override fun getNoteByIdFlow(id: Long): Flow<NoteEntity?> = noteDao.getByIdFlow(id)

    override suspend fun getNoteDetail(noteId: Long): NoteDetail? = withContext(Dispatchers.IO) {
        val note = noteDao.getById(noteId) ?: return@withContext null
        val topic = topicDao.getById(note.topicId)
            ?: TopicEntity(id = note.topicId, name = TopicResolver.INBOX, normalizedName = "inbox")
        val subtopic = note.subtopicId?.let { topicDao.getById(it) }

        // "Related" is derived at read time from what this memory actually
        // mentions, rather than from a stored association list that would go
        // stale as the tree grows.
        val relatedNotes = noteDao.getRelatedNotes(noteId, limit = 5)
        val relatedTopics = relatedTopicsFor(note, relatedNotes)

        NoteDetail(
            note = note,
            topic = topic,
            subtopic = subtopic,
            topicPath = topicPathFor(note),
            tasks = taskDao.getTasksForNote(noteId),
            entities = entityRefDao.getEntitiesForNote(noteId),
            entries = memoryEntryDao.getEntriesForNote(noteId),
            relatedTopics = relatedTopics,
            relatedNotes = relatedNotes
        )
    }

    /**
     * Topics worth surfacing beside a memory: where its neighbours live, plus
     * its own siblings. Capped and de-duplicated so the section stays readable.
     */
    private suspend fun relatedTopicsFor(
        note: NoteEntity,
        relatedNotes: List<NoteEntity>
    ): List<TopicEntity> {
        val ids = LinkedHashSet<Long>()
        for (neighbour in relatedNotes) {
            neighbour.subtopicId?.let { ids.add(it) }
            ids.add(neighbour.topicId)
        }

        val ownTopic = topicDao.getById(note.subtopicId ?: note.topicId)
        if (ownTopic != null) {
            topicDao.getSiblings(ownTopic.parentId, ownTopic.id).forEach { ids.add(it.id) }
        }

        ids.remove(note.topicId)
        note.subtopicId?.let { ids.remove(it) }

        return ids.mapNotNull { topicDao.getById(it) }.take(6)
    }

    override suspend fun saveNote(
        knowledge: ExtractedKnowledge,
        rawTranscript: String,
        cleanTranscript: String,
        source: String,
        sourceUrl: String?
    ): NoteEntity = withContext(Dispatchers.IO) {
        // One memory is one unit of work. Without a transaction a failure part
        // way through leaves a note with no tasks, or topics with no note.
        db.withTransaction {
            // A provider that could see the topic tree has already chosen a
            // placement; otherwise resolve one from the extracted keyphrases.
            val proposedPath = knowledge.topicPath
                ?: TopicResolver.resolve(knowledge.keyphrases, topicDao.getAllTopics()).path

            // Walk the path root-first, creating only what's missing. Passing the
            // real parent at each step is what keeps subtopics *underneath* their
            // parent instead of duplicating them as new root topics.
            var parentId: Long? = null
            var rootTopic: TopicEntity? = null
            var leafTopic: TopicEntity? = null

            val usablePath = proposedPath
                .filter { TopicResolver.isUsableTopicName(it) }
                .ifEmpty { listOf(TopicResolver.INBOX) }

            for (segment in usablePath) {
                val topic = resolveTopic(segment, parentId)
                if (rootTopic == null) rootTopic = topic
                leafTopic = topic
                parentId = topic.id
            }

            val root = rootTopic!!
            val leaf = leafTopic!!
            val now = System.currentTimeMillis()

            // Does this continue something already remembered here, or is it a
            // new subject? Only memories under the same topic are considered:
            // two things filed apart are, by construction, not the same thing.
            val siblings = noteDao.getNotesForTopic(leaf.id).map {
                MemoryMerger.Candidate(noteId = it.id, title = it.title)
            }

            val note = when (val decision = MemoryMerger.decide(knowledge.title, siblings)) {
                is MemoryMerger.Decision.Continue -> continueMemory(
                    noteId = decision.noteId,
                    title = decision.title,
                    knowledge = knowledge,
                    cleanTranscript = cleanTranscript,
                    source = source,
                    sourceUrl = sourceUrl,
                    now = now
                )

                MemoryMerger.Decision.StartNew -> startMemory(
                    rootId = root.id,
                    leafId = if (leaf.id != root.id) leaf.id else null,
                    knowledge = knowledge,
                    rawTranscript = rawTranscript,
                    cleanTranscript = cleanTranscript,
                    source = source,
                    sourceUrl = sourceUrl,
                    now = now
                )
            }
            val noteId = note.id

            if (knowledge.tasks.isNotEmpty()) {
                taskDao.insertAll(
                    knowledge.tasks.map { task ->
                        TaskEntity(
                            noteId = noteId,
                            topicId = leaf.id,
                            title = task.title,
                            deadline = task.deadline,
                            dueAt = task.dueAt,
                            isCompleted = false,
                            createdAt = now
                        )
                    }
                )
            }

            if (knowledge.entities.isNotEmpty()) {
                entityRefDao.insertAll(
                    knowledge.entities.map { entity ->
                        EntityRefEntity(
                            noteId = noteId,
                            topicId = leaf.id,
                            name = entity.name,
                            type = entity.type,
                            detail = entity.detail
                        )
                    }
                )
            }

            note
        }
    }

    /**
     * Creates a memory and its first history entry.
     */
    private suspend fun startMemory(
        rootId: Long,
        leafId: Long?,
        knowledge: ExtractedKnowledge,
        rawTranscript: String,
        cleanTranscript: String,
        source: String,
        sourceUrl: String?,
        now: Long
    ): NoteEntity {
        val note = NoteEntity(
            topicId = rootId,
            subtopicId = leafId,
            title = knowledge.title,
            summary = knowledge.summary,
            rawTranscript = rawTranscript,
            cleanTranscript = cleanTranscript,
            idea = knowledge.idea,
            decision = knowledge.decision,
            isCorrection = knowledge.isCorrection,
            createdAt = now,
            updatedAt = now
        )
        val id = noteDao.insert(note)
        memoryEntryDao.insert(
            MemoryEntryEntity(
                noteId = id,
                summary = knowledge.summary,
                transcript = cleanTranscript,
                idea = knowledge.idea,
                decision = knowledge.decision,
                source = source,
                sourceUrl = sourceUrl,
                createdAt = now
            )
        )
        return note.copy(id = id)
    }

    /**
     * Appends to an existing memory.
     *
     * The note's summary becomes the newest thing said, because that is what the
     * user means by "what do I know about this now". Everything previous stays
     * intact as history — appending must never lose what was there before, which
     * is the entire reason entries exist rather than an overwritten column.
     *
     * `idea` and `decision` are only overwritten when this entry actually
     * carries one; a later factual update should not erase an idea recorded
     * earlier.
     */
    private suspend fun continueMemory(
        noteId: Long,
        title: String,
        knowledge: ExtractedKnowledge,
        cleanTranscript: String,
        source: String,
        sourceUrl: String?,
        now: Long
    ): NoteEntity {
        val existing = noteDao.getById(noteId)
            ?: error("MemoryMerger proposed note $noteId, which no longer exists")

        val updated = existing.copy(
            title = title,
            summary = knowledge.summary,
            idea = knowledge.idea ?: existing.idea,
            decision = knowledge.decision ?: existing.decision,
            updatedAt = now
        )
        noteDao.update(updated)

        memoryEntryDao.insert(
            MemoryEntryEntity(
                noteId = noteId,
                summary = knowledge.summary,
                transcript = cleanTranscript,
                idea = knowledge.idea,
                decision = knowledge.decision,
                source = source,
                sourceUrl = sourceUrl,
                createdAt = now
            )
        )
        return updated
    }

    override suspend fun getEntriesForNote(noteId: Long): List<MemoryEntryEntity> =
        withContext(Dispatchers.IO) { memoryEntryDao.getEntriesForNote(noteId) }

    override suspend fun getTimelineForTopic(topicId: Long): List<TimelineEntry> =
        withContext(Dispatchers.IO) {
            val entries = memoryEntryDao.getTimelineForTopic(topicId)
            val titles = mutableMapOf<Long, String>()
            entries.map { entry ->
                val title = titles.getOrPut(entry.noteId) {
                    noteDao.getById(entry.noteId)?.title ?: "Untitled Memory"
                }
                TimelineEntry(entry = entry, noteTitle = title)
            }
        }

    override suspend fun moveNoteToTopic(noteId: Long, newTopicId: Long) = withContext(Dispatchers.IO) {
        noteDao.updateNoteTopic(noteId, newTopicId)
    }

    override suspend fun deleteNote(noteId: Long) = withContext(Dispatchers.IO) {
        noteDao.deleteById(noteId)
    }

    override fun getTasksForNoteFlow(noteId: Long): Flow<List<TaskEntity>> = taskDao.getTasksForNoteFlow(noteId)

    override fun getPendingTasksFlow(): Flow<List<TaskEntity>> = taskDao.getPendingTasksFlow()

    override fun getAllTasksFlow(): Flow<List<TaskEntity>> = taskDao.getAllTasksFlow()

    override suspend fun setTaskCompleted(taskId: Long, isCompleted: Boolean) = withContext(Dispatchers.IO) {
        taskDao.setTaskCompleted(taskId, isCompleted)
    }

    override fun getRelatedTopicsFlow(topicId: Long): Flow<List<TopicEntity>> =
        relationshipDao.getRelatedTopicsFlow(topicId)

    override suspend fun addTopicRelationship(fromTopicId: Long, toTopicId: Long, type: String): Unit =
        withContext(Dispatchers.IO) {
            relationshipDao.insert(
                TopicRelationshipEntity(
                    fromTopicId = fromTopicId,
                    toTopicId = toTopicId,
                    relationshipType = type
                )
            )
            Unit
        }

    override suspend fun getTopicSummary(topicId: Long): String = withContext(Dispatchers.IO) {
        val topic = topicDao.getById(topicId) ?: return@withContext ""
        val notes = noteDao.getNotesForTopic(topicId)
        val subtopics = topicDao.getSubtopics(topicId)

        if (notes.isEmpty() && subtopics.isEmpty()) {
            return@withContext "Nothing recorded under ${topic.name} yet."
        }

        val countText = "${notes.size} ${if (notes.size == 1) "memory" else "memories"}"
        val subtopicText = if (subtopics.isNotEmpty()) {
            " across ${subtopics.size} ${if (subtopics.size == 1) "subtopic" else "subtopics"}"
        } else ""

        "${topic.name} holds $countText$subtopicText."
    }

    override suspend fun search(query: String): SearchResult = withContext(Dispatchers.IO) {
        val raw = query.trim()
        if (raw.length < 2) return@withContext SearchResult()

        val escaped = escapeForLike(raw)

        val hits = noteDao.searchNotes(escaped).map { note ->
            SearchHit(
                note = note,
                topicPath = topicPathFor(note),
                snippet = buildSnippet(note, raw)
            )
        }

        SearchResult(
            notes = hits,
            topics = topicDao.searchTopics(escaped),
            tasks = taskDao.searchTasks(escaped)
        )
    }

    /**
     * Makes LIKE metacharacters literal. Without this, a query of "%" matches
     * every row and "_" matches any single character.
     */
    private fun escapeForLike(input: String): String =
        input.replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")

    /**
     * Breadcrumb like "Nyx · Graphics · Ring Buffer".
     *
     * Walks up from the deepest node the memory is filed under rather than
     * concatenating topic + subtopic, so a three-level placement reads in full
     * instead of losing its middle. The visited set guards against a parent
     * cycle turning this into an infinite loop.
     */
    private suspend fun topicPathFor(note: NoteEntity): String {
        val leaf = note.subtopicId ?: note.topicId
        val names = mutableListOf<String>()
        val visited = mutableSetOf<Long>()
        var cursor: Long? = leaf

        while (cursor != null && visited.add(cursor)) {
            val topic = topicDao.getById(cursor) ?: break
            names.add(topic.name)
            cursor = topic.parentId
        }

        return names.asReversed().joinToString(" · ")
    }

    /**
     * Pulls the words around the match so a result is recognisable without
     * being opened. Falls back to the summary when the hit was on the title.
     */
    private fun buildSnippet(note: NoteEntity, query: String, radius: Int = 60): String {
        val haystacks = listOf(note.summary, note.cleanTranscript, note.rawTranscript)
        val source = haystacks.firstOrNull { it.contains(query, ignoreCase = true) }
            ?: return note.summary.take(radius * 2).trim()

        val index = source.indexOf(query, ignoreCase = true)
        if (index < 0) return source.take(radius * 2).trim()

        val start = (index - radius).coerceAtLeast(0)
        val end = (index + query.length + radius).coerceAtMost(source.length)

        return buildString {
            if (start > 0) append("…")
            append(source.substring(start, end).trim())
            if (end < source.length) append("…")
        }
    }

    override suspend fun buildTopicTree(): List<TopicNode> = withContext(Dispatchers.IO) {
        // Load the whole tree once and assemble in memory. The previous shape
        // issued a query per node per level, which grows quadratically as the
        // tree fills out.
        val all = topicDao.getAllTopics()
        val childrenByParent = all.groupBy { it.parentId }

        suspend fun build(topic: TopicEntity): TopicNode {
            val notes = noteDao.getNotesForTopic(topic.id)
            return TopicNode(
                topic = topic,
                subtopics = childrenByParent[topic.id].orEmpty().map { build(it) },
                noteCount = notes.size,
                recentNotes = notes.take(3)
            )
        }

        childrenByParent[null].orEmpty().map { build(it) }
    }

    override suspend fun exportMarkdown(rootTopicId: Long?): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        sb.append("# Naomi Knowledge Export\n\n")

        val roots = if (rootTopicId != null) {
            listOfNotNull(topicDao.getById(rootTopicId))
        } else {
            topicDao.getRootTopics()
        }

        for (topic in roots) {
            appendTopicMarkdown(sb, topic, 1)
        }

        sb.toString()
    }

    private suspend fun appendTopicMarkdown(sb: StringBuilder, topic: TopicEntity, level: Int) {
        sb.append("${"#".repeat(level.coerceIn(1, 6))} ${topic.name}\n\n")

        for (note in noteDao.getNotesForTopic(topic.id)) {
            sb.append("${"#".repeat((level + 1).coerceIn(1, 6))} ${note.title}\n\n")
            sb.append("${note.summary}\n\n")

            note.idea?.takeIf { it.isNotBlank() }?.let { sb.append("**Idea**: $it\n\n") }
            note.decision?.takeIf { it.isNotBlank() }?.let { sb.append("**Decision**: $it\n\n") }

            val tasks = taskDao.getTasksForNote(note.id)
            if (tasks.isNotEmpty()) {
                for (task in tasks) {
                    val check = if (task.isCompleted) "[x]" else "[ ]"
                    val due = task.deadline?.takeIf { it.isNotBlank() }?.let { " *(due $it)*" } ?: ""
                    sb.append("- $check ${task.title}$due\n")
                }
                sb.append("\n")
            }
        }

        for (sub in topicDao.getSubtopics(topic.id)) {
            appendTopicMarkdown(sb, sub, level + 1)
        }
    }
}
