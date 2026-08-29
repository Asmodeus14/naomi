package com.naomi.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.naomi.app.data.database.entities.NoteEntity
import com.naomi.app.presentation.theme.LocalNaomiAccents
import com.naomi.app.presentation.theme.NaomiMonoLabel
import com.naomi.app.presentation.theme.NaomiSpacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One memory in a list.
 *
 * Shared by Home, Topic detail and Search rather than reimplemented per screen,
 * so a memory looks the same everywhere the user meets one.
 */
@Composable
fun MemoryRow(
    note: NoteEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showIdea: Boolean = true
) {
    val accents = LocalNaomiAccents.current
    val timestamp = remember(note.createdAt) { memoryDateFormat.format(Date(note.createdAt)) }

    NaomiRow(modifier = modifier, onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // The dot carries the work the card's left edge used to do: it marks
            // where a memory starts, so the list still has a spine to scan down.
            Box(
                modifier = Modifier
                    .padding(top = 7.dp, end = NaomiSpacing.sm + NaomiSpacing.xs)
                    .size(NaomiSpacing.sm - 1.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (note.summary.isNotBlank()) {
                    Spacer(Modifier.height(NaomiSpacing.xs))
                    Text(
                        text = note.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val idea = note.idea
                if (showIdea && !idea.isNullOrBlank()) {
                    Spacer(Modifier.height(NaomiSpacing.sm))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "IDEA",
                            style = MaterialTheme.typography.labelSmall,
                            color = accents.idea,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(NaomiSpacing.sm - 2.dp))
                        Text(
                            text = idea,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(Modifier.height(NaomiSpacing.sm))
                Text(
                    text = timestamp,
                    style = NaomiMonoLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private val memoryDateFormat = SimpleDateFormat("MMM d · h:mm a", Locale.getDefault())
