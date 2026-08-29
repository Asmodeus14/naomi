package com.naomi.app.presentation.hierarchy

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
import com.naomi.app.presentation.components.MemoryRow
import com.naomi.app.presentation.components.NaomiEmptyState
import com.naomi.app.presentation.components.NaomiSectionHeader
import com.naomi.app.presentation.theme.*

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

