package com.naomi.app.presentation.hierarchy

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naomi.app.domain.model.TimelineEntry
import com.naomi.app.presentation.components.MemoryRow
import com.naomi.app.presentation.components.NaomiEmptyState
import com.naomi.app.presentation.components.NaomiSectionHeader
import com.naomi.app.presentation.theme.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One day's worth of the topic's history, already labelled for display. */
private data class TimelineDay(
    val label: String,
    val entries: List<TimelineEntry>
)

/**
 * Buckets a newest-first timeline by calendar day in the device's zone.
 *
 * Days are labelled rather than stamped: "Today" and "Yesterday" are how people
 * actually place a recent thought, and an exact time adds nothing to a history
 * read at a glance.
 */
private fun groupByDay(timeline: List<TimelineEntry>): List<TimelineDay> {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val yesterday = today.minusDays(1)
    val monthDay = DateTimeFormatter.ofPattern("MMM d")
    val withYear = DateTimeFormatter.ofPattern("MMM d, yyyy")

    return timeline
        .groupBy { Instant.ofEpochMilli(it.entry.createdAt).atZone(zone).toLocalDate() }
        .entries
        .sortedByDescending { it.key }
        .map { (date, entries) ->
            TimelineDay(
                label = when {
                    date == today -> "Today"
                    date == yesterday -> "Yesterday"
                    date.year == today.year -> date.format(monthDay)
                    else -> date.format(withYear)
                },
                entries = entries
            )
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicDetailScreen(
    topicId: Long,
    viewModel: TopicHierarchyViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToSubtopic: (Long) -> Unit,
    onNavigateToNoteDetail: (Long) -> Unit
) {
    val detailState by viewModel.detailState.collectAsStateWithLifecycle()

    LaunchedEffect(topicId) {
        viewModel.loadTopicDetail(topicId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Naomi",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        val outer = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = NaomiSpacing.lg)

        val loaded = detailState as? TopicDetailUiState.Loaded
        if (loaded == null) {
            Box(outer, contentAlignment = Alignment.Center) {
                when (detailState) {
                    is TopicDetailUiState.Loading -> Unit

                    is TopicDetailUiState.NotFound -> NaomiEmptyState(
                        title = "This topic is gone",
                        message = "It was deleted, or merged into another topic."
                    )

                    is TopicDetailUiState.Failed -> NaomiEmptyState(
                        title = "Couldn't open this topic",
                        message = (detailState as TopicDetailUiState.Failed).message
                    )

                    is TopicDetailUiState.Loaded -> Unit
                }
            }
            return@Scaffold
        }

        val notes = loaded.notes
        val subtopics = loaded.subtopics
        val topicSummary = loaded.summary

        // The timeline earns its place only when at least one memory here has
        // been added to since it was first spoken. Otherwise it is the list of
        // memories again in a different order, which is noise, not history.
        val timelineDays = remember(loaded.timeline, notes.size) {
            if (loaded.timeline.size > notes.size) groupByDay(loaded.timeline) else emptyList()
        }

        LazyColumn(
            modifier = outer,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(NaomiSpacing.xs))
                Text(
                    text = "TOPIC",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.4.sp
                )
                Spacer(modifier = Modifier.height(NaomiSpacing.xs))
                Text(
                    text = loaded.topic.name,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // Generated Topic Summary Description
            if (topicSummary.isNotBlank()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = topicSummary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 22.sp,
                            modifier = Modifier.padding(18.dp)
                        )
                    }
                }
            }

            // Subtopics Section
            if (subtopics.isNotEmpty()) {
                item {
                    NaomiSectionHeader(title = "SUBTOPICS")
                }

                items(subtopics, key = { it.id }) { sub ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToSubtopic(sub.id) },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = sub.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "View",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }

            // Timeline Section — what has been said under this topic, in order,
            // including everything filed beneath it.
            if (timelineDays.isNotEmpty()) {
                item {
                    NaomiSectionHeader(title = "TIMELINE")
                }

                items(timelineDays, key = { it.label }) { day ->
                    Column(verticalArrangement = Arrangement.spacedBy(NaomiSpacing.sm)) {
                        Text(
                            text = day.label,
                            style = NaomiMonoLabel,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // Intrinsic height so the guide line has something to
                        // measure against; inside a LazyColumn the incoming max
                        // height is unbounded and fillMaxHeight collapses to 0.
                        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .fillMaxHeight()
                                    .background(
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                    )
                            )
                            Spacer(modifier = Modifier.width(NaomiSpacing.md))
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(NaomiSpacing.md)
                            ) {
                                day.entries.forEachIndexed { index, moment ->
                                    // The memory's name is there to say which
                                    // thing changed. Repeating it down a run of
                                    // entries about the same one says nothing,
                                    // so it appears when the subject changes.
                                    val showTitle = index == 0 ||
                                        day.entries[index - 1].noteTitle != moment.noteTitle

                                    Column(
                                        modifier = Modifier.clickable {
                                            onNavigateToNoteDetail(moment.entry.noteId)
                                        },
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        if (showTitle) {
                                            Text(
                                                text = moment.noteTitle,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = moment.entry.summary,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            lineHeight = 22.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Memories Section
            item {
                NaomiSectionHeader(title = "MEMORIES")
            }

            if (notes.isEmpty()) {
                item {
                    Text(
                        text = "No memories under this topic yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(notes, key = { it.id }) { note ->
                    MemoryRow(
                        note = note,
                        onClick = { onNavigateToNoteDetail(note.id) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}

