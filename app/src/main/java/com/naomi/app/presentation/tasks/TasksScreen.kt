package com.naomi.app.presentation.tasks

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naomi.app.data.database.entities.TaskEntity
import com.naomi.app.presentation.components.NaomiEmptyState
import com.naomi.app.presentation.components.NaomiSectionHeader
import com.naomi.app.presentation.components.bucketOf
import com.naomi.app.presentation.components.rememberDueLabel
import com.naomi.app.presentation.theme.*
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    viewModel: TasksViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToNoteDetail: (Long) -> Unit
) {
    val allTasks by viewModel.allTasks.collectAsStateWithLifecycle()

    // Bucketing is a single grouping pass rather than one filter per section
    // (which was O(n²) via the `it !in todayTasks` membership tests), and it is
    // remembered so scrolling does not redo it on every frame.
    val sections = remember(allTasks) {
        val today = LocalDate.now()
        allTasks
            .filter { !it.isCompleted }
            .groupBy { bucketOf(it, today) }
            .toSortedMap()
    }
    val completed = remember(allTasks) { allTasks.filter { it.isCompleted } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Tasks",
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
        if (allTasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                NaomiEmptyState(
                    title = "You're caught up",
                    message = "When you mention something you need to do, Naomi pulls it out and lists it here."
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                for ((bucket, tasks) in sections) {
                    item(key = "header-${bucket.name}") {
                        NaomiSectionHeader(title = bucket.heading)
                    }
                    items(tasks, key = { it.id }) { task ->
                        TaskItemRow(
                            task = task,
                            onToggle = { viewModel.toggleTask(task.id, task.isCompleted) },
                            onClickNote = { onNavigateToNoteDetail(task.noteId) }
                        )
                    }
                }

                if (completed.isNotEmpty()) {
                    item {
                        NaomiSectionHeader(title = "COMPLETED")
                    }
                    items(completed, key = { it.id }) { task ->
                        TaskItemRow(
                            task = task,
                            onToggle = { viewModel.toggleTask(task.id, task.isCompleted) },
                            onClickNote = { onNavigateToNoteDetail(task.noteId) }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(48.dp))
                }
            }
        }
    }
}

@Composable
fun TaskItemRow(
    task: TaskEntity,
    onToggle: () -> Unit,
    onClickNote: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClickNote() },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Minimalist square checkbox
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .border(
                        1.5.dp,
                        if (task.isCompleted) LocalNaomiAccents.current.success else MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(4.dp)
                    )
                    .background(
                        if (task.isCompleted) LocalNaomiAccents.current.success.copy(alpha = 0.15f)
                        else Color.Transparent
                    )
                    .clickable { onToggle() },
                contentAlignment = Alignment.Center
            ) {
                if (task.isCompleted) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Completed",
                        tint = LocalNaomiAccents.current.success,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                )
                val due = rememberDueLabel(task.deadline, task.dueAt)
                if (due != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = due.text,
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            task.isCompleted -> MaterialTheme.colorScheme.onSurfaceVariant
                            due.isOverdue -> MaterialTheme.colorScheme.error
                            else -> LocalNaomiAccents.current.idea
                        }
                    )
                }
            }
        }
    }
}

