package com.naomi.app.presentation.note

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naomi.app.presentation.components.NaomiEmptyState
import com.naomi.app.presentation.components.NaomiPill
import com.naomi.app.presentation.components.rememberDueLabel
import com.naomi.app.presentation.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDetailScreen(
    noteId: Long,
    viewModel: NoteDetailViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToTopic: (Long) -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val allTopics by viewModel.allTopics.collectAsStateWithLifecycle()

    var showMoveDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var newTopicInput by rememberSaveable { mutableStateOf("") }
    var isCreatingNewTopic by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(noteId) {
        viewModel.loadNote(noteId)
    }

    fun shareNoteMarkdown() {
        val detail = (uiState as? NoteDetailUiState.Loaded)?.detail ?: return
        val note = detail.note
        val topic = detail.topicPath
        val tasksText = detail.tasks.joinToString("\n") {
            "- [${if (it.isCompleted) "x" else " "}] ${it.title}${if (!it.deadline.isNullOrBlank()) " (Due: ${it.deadline})" else ""}"
        }

        val text = buildString {
            append("# $topic — ${note.title}\n\n")
            append("${note.summary}\n\n")
            if (!note.idea.isNullOrBlank()) {
                append("**Idea**: ${note.idea}\n\n")
            }
            if (!note.decision.isNullOrBlank()) {
                append("**Decision**: ${note.decision}\n\n")
            }
            if (tasksText.isNotBlank()) {
                append("### Tasks\n$tasksText\n\n")
            }
            append("— Remembered by Naomi")
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, note.title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Export Memory"))
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
                actions = {
                    // Nothing here can act on a memory that has not loaded, so
                    // the actions appear with it rather than being tappable
                    // no-ops over a spinner.
                    if (uiState is NoteDetailUiState.Loaded) {
                        IconButton(onClick = { showMoveDialog = true }) {
                            Icon(
                                imageVector = Icons.Outlined.DriveFileMove,
                                contentDescription = "Move Topic",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { shareNoteMarkdown() }) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = "Export",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        val currentDetail = (uiState as? NoteDetailUiState.Loaded)?.detail
        if (currentDetail == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = NaomiSpacing.lg),
                contentAlignment = Alignment.Center
            ) {
                // A deleted memory reached through a stale widget link used to
                // spin here indefinitely. Every non-loaded state now says what
                // happened and leaves a way out.
                when (uiState) {
                    is NoteDetailUiState.Loading ->
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)

                    is NoteDetailUiState.NotFound -> NaomiEmptyState(
                        title = "This memory is gone",
                        message = "It was deleted, or the link that brought you here is out of date."
                    )

                    is NoteDetailUiState.Failed -> NaomiEmptyState(
                        title = "Couldn't open this memory",
                        message = (uiState as NoteDetailUiState.Failed).message
                    )

                    is NoteDetailUiState.Loaded -> Unit
                }
            }
        } else {
            val dateFormatted = remember(currentDetail.note.createdAt) {
                val sdf = SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault())
                sdf.format(Date(currentDetail.note.createdAt))
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Where this memory is filed. This was previously a decorative
                // "MEMORY CONTEXT ACCESSED" label — invented telemetry that
                // reported nothing. The full ancestry is the real thing worth
                // showing here, and it is the one place the hierarchy is legible.
                item {
                    Spacer(modifier = Modifier.height(NaomiSpacing.xs))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(NaomiSpacing.sm)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Text(
                            text = currentDetail.topicPath.uppercase(Locale.ROOT),
                            style = NaomiMonoLabel,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.sp
                        )
                    }
                }

                item {
                    Text(
                        text = currentDetail.note.title,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground,
                        lineHeight = 38.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = dateFormatted,
                        style = NaomiMonoLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                // Summary / Thought Body
                item {
                    Text(
                        text = currentDetail.note.summary,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        lineHeight = 26.sp
                    )
                }

                // Indented Structural Section (Idea, Task, Related) with left guide line
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        // Vertical Guide Line
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        )

                        Spacer(modifier = Modifier.width(20.dp))

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            // IDEA Section
                            if (!currentDetail.note.idea.isNullOrBlank()) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Lightbulb,
                                            contentDescription = "Idea",
                                            tint = LocalNaomiAccents.current.idea,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "IDEA",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = LocalNaomiAccents.current.idea,
                                            letterSpacing = 1.2.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = currentDetail.note.idea,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        lineHeight = 22.sp
                                    )
                                }
                            }

                            // DECISION Section
                            if (!currentDetail.note.decision.isNullOrBlank()) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "DECISION",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = LocalNaomiAccents.current.success,
                                            letterSpacing = 1.2.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = currentDetail.note.decision,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        lineHeight = 22.sp
                                    )
                                }
                            }

                            // TASK Section
                            if (currentDetail.tasks.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.ListAlt,
                                            contentDescription = "Task",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "TASK",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            letterSpacing = 1.2.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        for (task in currentDetail.tasks) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { viewModel.toggleTask(task.id, task.isCompleted) }
                                                    .padding(vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
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
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (task.isCompleted) {
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = "Done",
                                                            tint = LocalNaomiAccents.current.success,
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.width(12.dp))

                                                Column(
                                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    Text(
                                                        text = task.title,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                                                    )
                                                    // The extractor resolves "tomorrow" to an instant; showing
                                                    // only the spoken phrase would keep saying "Tomorrow"
                                                    // forever, so the resolved date is shown beside it.
                                                    val due = rememberDueLabel(task.deadline, task.dueAt)
                                                    if (due != null) {
                                                        Text(
                                                            text = due.text,
                                                            style = NaomiMonoLabel,
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
                                }
                            }

                            // RELATED Section
                            if (currentDetail.relatedTopics.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Link,
                                            contentDescription = "Related",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "RELATED",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            letterSpacing = 1.2.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        for (related in currentDetail.relatedTopics) {
                                            NaomiPill(
                                                text = related.name,
                                                onClick = { onNavigateToTopic(related.id) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(48.dp))
                }
            }
        }

        // Move to Topic Dialog
        if (showMoveDialog) {
            AlertDialog(
                onDismissRequest = {
                    showMoveDialog = false
                    isCreatingNewTopic = false
                },
                // M3's defaults tint the dialog with the seed colour, which is
                // pink here and belongs to no part of Naomi's palette.
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                shape = NaomiShapes.large,
                title = { Text("Move to Topic", style = MaterialTheme.typography.titleLarge) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (isCreatingNewTopic) {
                            OutlinedTextField(
                                value = newTopicInput,
                                onValueChange = { newTopicInput = it },
                                placeholder = { Text("New Topic Name") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        } else {
                            Text("Select a topic to organize this memory under:", style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                                items(allTopics) { topic ->
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.moveTopic(noteId, topic.id)
                                                showMoveDialog = false
                                            }
                                            .padding(vertical = 8.dp, horizontal = 4.dp),
                                        color = Color.Transparent
                                    ) {
                                        Text(topic.name, style = MaterialTheme.typography.bodyLarge)
                                    }
                                }
                            }
                            TextButton(
                                onClick = { isCreatingNewTopic = true },
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Text("+ Create new topic")
                            }
                        }
                    }
                },
                confirmButton = {
                    if (isCreatingNewTopic) {
                        TextButton(
                            onClick = {
                                if (newTopicInput.isNotBlank()) {
                                    viewModel.createTopicAndMove(noteId, newTopicInput)
                                    showMoveDialog = false
                                    isCreatingNewTopic = false
                                    newTopicInput = ""
                                }
                            }
                        ) {
                            Text("Create & Move")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showMoveDialog = false
                        isCreatingNewTopic = false
                    }) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
        }

        // Delete Confirmation Dialog
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                shape = NaomiShapes.large,
                title = { Text("Delete Memory") },
                text = { Text("Are you sure you want to delete this memory? This action cannot be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                            viewModel.deleteNote(noteId) {
                                onNavigateBack()
                            }
                        }
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
        }
    }
}

