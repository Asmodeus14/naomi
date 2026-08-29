package com.naomi.app.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.naomi.app.domain.model.SearchHit
import com.naomi.app.presentation.theme.NaomiSpacing

/**
 * A search result with enough context to recognise without opening.
 *
 * The breadcrumb and the surrounding words are the point: a list of bare titles
 * makes the user open results to find out whether they were the right one.
 */
@Composable
fun SearchResultRow(
    hit: SearchHit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    NaomiRow(modifier = modifier, onClick = onClick) {
        Column {
            if (hit.topicPath.isNotBlank()) {
                Text(
                    text = hit.topicPath.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.2.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(NaomiSpacing.xs))
            }

            Text(
                text = hit.note.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (hit.snippet.isNotBlank()) {
                Spacer(Modifier.height(NaomiSpacing.xs))
                Text(
                    text = hit.snippet,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
