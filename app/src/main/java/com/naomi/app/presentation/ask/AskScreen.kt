package com.naomi.app.presentation.ask

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naomi.app.domain.model.Answer
import com.naomi.app.domain.model.RecalledMemory
import com.naomi.app.presentation.components.NaomiEmptyState
import com.naomi.app.presentation.components.NaomiTopBar
import com.naomi.app.presentation.components.rememberDueLabel
import com.naomi.app.presentation.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Recall in the user's own words.
 *
 * Everything below is either a fixed phrase or text the user recorded. Nothing
 * on this screen is generated, and the footer says so — a recall surface that
 * looked like a chatbot would invite the assumption that it can answer things it
 * was never told, which is exactly the assumption Naomi must not create.
 */
@Composable
fun AskScreen(
    viewModel: AskViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToNoteDetail: (Long) -> Unit
) {
    val question by viewModel.question.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current

    Scaffold(
        topBar = { NaomiTopBar(title = "Ask Naomi", onBack = onNavigateBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = NaomiSpacing.lg)
        ) {
            TextField(
                value = question,
                onValueChange = viewModel::onQuestionChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = "What did I say about…",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                },
                textStyle = MaterialTheme.typography.titleMedium,
                trailingIcon = {
                    if (question.isNotBlank()) {
                        IconButton(onClick = viewModel::clear) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        keyboard?.hide()
                        viewModel.ask()
                    }
                ),
                colors = TextFieldDefaults.colors(
                    // The field is a line to write on, not a box. Containers and
                    // indicators are stripped so the question reads as the
                    // largest thing on the screen, which is what it is.
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            )

            Spacer(Modifier.height(NaomiSpacing.lg))

            when (val state = uiState) {
                is AskUiState.Idle -> AskPrompts(
                    onPick = {
                        viewModel.onQuestionChange(it)
                        viewModel.ask()
                    }
                )

                is AskUiState.Thinking -> Text(
                    text = "Looking…",
                    style = NaomiMonoLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                is AskUiState.Failed -> NaomiEmptyState(
                    title = "Couldn't look that up",
                    message = state.message
                )

                is AskUiState.Answered -> AnswerBody(
                    answer = state.answer,
                    onNavigateToNoteDetail = onNavigateToNoteDetail
                )
            }
        }
    }
}

/**
 * Shown before anything is asked. These are phrasings, not saved queries — the
 * point is to teach that whole questions work, since a search box trains people
 * to type single words.
 */
@Composable
private fun AskPrompts(onPick: (String) -> Unit) {
    val examples = listOf(
        "What did I say about work?",
        "What do I know about the project?",
        "When is the deadline?"
    )

    Column(verticalArrangement = Arrangement.spacedBy(NaomiSpacing.md)) {
        Text(
            text = "Ask in your own words.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        for (example in examples) {
            Text(
                text = example,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(example) }
                    .padding(vertical = NaomiSpacing.xs)
            )
        }
    }
}

@Composable
private fun AnswerBody(
    answer: Answer,
    onNavigateToNoteDetail: (Long) -> Unit
) {
    when (answer) {
        Answer.Idle -> Unit

        Answer.Unanswerable -> NaomiEmptyState(
            title = "What about?",
            message = "Name the thing you're after — a project, a person, a decision."
        )

        is Answer.Nothing -> NaomiEmptyState(
            title = "Nothing about \"${answer.subject}\" yet",
            message = "Naomi only knows what you've told it."
        )

        is Answer.Found -> LazyColumn(
            verticalArrangement = Arrangement.spacedBy(NaomiSpacing.lg)
        ) {
            item {
                Text(
                    text = headlineFor(answer),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    lineHeight = 26.sp
                )
            }

            if (answer.tasks.isNotEmpty()) {
                items(answer.tasks, key = { "task-${it.id}" }) { task ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val due = rememberDueLabel(task.deadline, task.dueAt)
                        if (due != null) {
                            Text(
                                text = due.text,
                                style = NaomiMonoLabel,
                                color = if (due.isOverdue) MaterialTheme.colorScheme.error
                                else LocalNaomiAccents.current.idea
                            )
                        }
                    }
                }
            }

            items(answer.memories, key = { "note-${it.note.id}" }) { memory ->
                RecalledMemoryBlock(
                    memory = memory,
                    onClick = { onNavigateToNoteDetail(memory.note.id) }
                )
            }

            item {
                Spacer(Modifier.height(NaomiSpacing.sm))
                // States where the answer came from, in the one place the user is
                // most likely to wonder. It is a fact about the app, not a boast:
                // there is no INTERNET permission, so no other answer was possible.
                Text(
                    text = "Answered from memories on this device.",
                    style = NaomiMonoLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(NaomiSpacing.xl))
            }
        }
    }
}

@Composable
private fun RecalledMemoryBlock(
    memory: RecalledMemory,
    onClick: () -> Unit
) {
    val dates = remember(memory.matches) {
        val day = SimpleDateFormat("MMM d", Locale.getDefault())
        memory.matches.map { day.format(Date(it.createdAt)) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(NaomiSpacing.xs)
    ) {
        if (memory.topicPath.isNotBlank()) {
            Text(
                text = memory.topicPath.uppercase(Locale.ROOT),
                style = NaomiMonoLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )
        }
        Text(
            text = memory.note.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground
        )

        // What was actually said, verbatim. If no single entry matched, the
        // memory's current summary stands in — still the user's own words.
        val lines = memory.matches.ifEmpty { null }
        if (lines == null) {
            Text(
                text = memory.note.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 22.sp
            )
        } else {
            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                )
                Spacer(Modifier.width(NaomiSpacing.md))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(NaomiSpacing.sm)
                ) {
                    lines.forEachIndexed { index, entry ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = dates[index],
                                style = NaomiMonoLabel,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            Text(
                                text = entry.summary,
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

/**
 * The one sentence Naomi says in its own voice. It reports counts and nothing
 * else, so it cannot be wrong about content.
 */
private fun headlineFor(answer: Answer.Found): String {
    val memories = answer.memories.size
    val moments = answer.memories.sumOf { it.matches.size }

    val subject = answer.subject.ifBlank { "that" }
    return when {
        answer.tasks.isNotEmpty() && memories == 0 ->
            "You have ${answer.tasks.size} thing${plural(answer.tasks.size)} to do about \"$subject\"."

        moments > memories ->
            "You've said $moments things about \"$subject\", across $memories " +
                "memor${if (memories == 1) "y" else "ies"}."

        memories == 1 -> "One memory about \"$subject\"."

        else -> "$memories memories about \"$subject\"."
    }
}

private fun plural(count: Int) = if (count == 1) "" else "s"
