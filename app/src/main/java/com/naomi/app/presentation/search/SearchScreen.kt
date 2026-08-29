package com.naomi.app.presentation.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naomi.app.presentation.components.NaomiEmptyState
import com.naomi.app.presentation.components.NaomiRow
import com.naomi.app.presentation.components.NaomiSectionHeader
import com.naomi.app.presentation.components.SearchResultRow
import com.naomi.app.presentation.components.rememberDueLabel
import com.naomi.app.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToNoteDetail: (Long) -> Unit,
    onNavigateToTopic: (Long) -> Unit
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { viewModel.onQueryChange(it) },
                        placeholder = { Text("Search memories, topics, tasks...", style = MaterialTheme.typography.bodyMedium) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onQueryChange("") }) {
                                    Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        )
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp)
        ) {
            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SearchFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { viewModel.setFilter(filter) },
                        label = { Text(filter.label, style = MaterialTheme.typography.labelSmall) },
                        shape = NaomiShapes.medium
                    )
                }
            }

            when (val state = uiState) {
                is SearchUiState.Idle -> CenteredMessage(
                    title = "Search Naomi",
                    message = "Find memories, topics, ideas, and tasks."
                )

                // Deliberately silent. A spinner that appears for the ~20ms a
                // local query takes is a flicker, not feedback; the previous
                // results simply stay put until the new ones arrive.
                is SearchUiState.Searching -> Unit

                is SearchUiState.Empty -> CenteredMessage(
                    title = "Nothing found",
                    message = "No memory mentions “${query.trim()}”. Try another word."
                )

                is SearchUiState.Failed -> CenteredMessage(
                    title = "Search failed",
                    message = state.message
                )

                is SearchUiState.Results -> {
                    val searchResult = state.result
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Topics Match
                        if ((selectedFilter == SearchFilter.ALL || selectedFilter == SearchFilter.TOPICS) && searchResult.topics.isNotEmpty()) {
                            item {
                                NaomiSectionHeader(title = "TOPICS")
                            }
                            items(searchResult.topics, key = { "t_${it.id}" }) { topic ->
                                NaomiRow(onClick = { onNavigateToTopic(topic.id) }) {
                                    Text(
                                        text = topic.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        // Notes Match
                        if ((selectedFilter == SearchFilter.ALL || selectedFilter == SearchFilter.NOTES) && searchResult.notes.isNotEmpty()) {
                            item {
                                NaomiSectionHeader(title = "MEMORIES")
                            }
                            items(searchResult.notes, key = { "n_${it.note.id}" }) { hit ->
                                SearchResultRow(
                                    hit = hit,
                                    onClick = { onNavigateToNoteDetail(hit.note.id) }
                                )
                            }
                        }

                        // Tasks Match
                        if ((selectedFilter == SearchFilter.ALL || selectedFilter == SearchFilter.TASKS) && searchResult.tasks.isNotEmpty()) {
                            item {
                                NaomiSectionHeader(title = "TASKS")
                            }
                            items(searchResult.tasks, key = { "task_${it.id}" }) { task ->
                                NaomiRow(onClick = { onNavigateToNoteDetail(task.noteId) }) {
                                    Text(
                                        text = "□  ${task.title}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    val due = rememberDueLabel(task.deadline, task.dueAt)
                                    if (due != null) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = due.text,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (due.isOverdue) MaterialTheme.colorScheme.error
                                            else LocalNaomiAccents.current.idea
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(NaomiSpacing.xl))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Fills the results area with a single message.
 *
 * Search has four states that all render as centred prose; sharing one shape
 * keeps them from drifting apart in tone and spacing.
 */
@Composable
private fun ColumnScope.CenteredMessage(title: String, message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .weight(1f),
        contentAlignment = Alignment.Center
    ) {
        NaomiEmptyState(title = title, message = message)
    }
}

