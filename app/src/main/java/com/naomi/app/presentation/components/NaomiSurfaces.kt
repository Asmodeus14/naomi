package com.naomi.app.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naomi.app.presentation.theme.NaomiShapes
import com.naomi.app.presentation.theme.NaomiSpacing

/**
 * A tappable row of content, held together by space rather than by a box.
 *
 * This replaces the bordered card that used to wrap every list item. Eighteen
 * of them meant the eye had to cross a boundary to reach each memory, and a
 * screen of six memories read as six objects competing rather than one list to
 * scan. Space, a consistent left edge and type weight do the same grouping work
 * without drawing anything.
 *
 * Separation between rows belongs to the list, not to the row — a row that
 * padded itself generously ended up double-spaced inside a list that was already
 * spacing its children, and the result read as disconnected fragments rather
 * than a list. So the row claims only the height it needs to stay tappable:
 * `heightIn` guarantees the 48dp target the vanished border used to imply, and
 * the small padding keeps text off that boundary.
 */
@Composable
fun NaomiRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 48.dp)
            .padding(vertical = NaomiSpacing.xs),
        verticalArrangement = Arrangement.Center,
        content = content
    )
}

/**
 * A hairline between rows.
 *
 * Used only where the rows are discrete choices — settings, mostly — because
 * there the user is scanning for a boundary. A list of memories gets whitespace
 * instead: they are one continuous thing, and ruling between them says otherwise.
 */
@Composable
fun NaomiRowDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        thickness = NaomiShapes.hairline,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    )
}

/**
 * A bordered container, kept for the one case that earns it.
 *
 * Everything else that used this is now [NaomiRow]. What survives is content
 * that *is* a surface — the theme preview, which shows the user what the app's
 * background looks like and therefore needs an edge to be a sample of anything.
 */
@Composable
fun NaomiCard(
    modifier: Modifier = Modifier,
    shape: Shape = NaomiShapes.medium,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        ),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(
            NaomiShapes.hairline,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        ),
        content = { Column(content = content) }
    )
}

/**
 * The one top bar. Flat, no elevation, title on the left, back arrow when the
 * screen is a detail view.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NaomiTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

/**
 * An uppercase section label with a rule running to the edge, and an optional
 * trailing action. The rule is the signature detail from the design system.
 */
@Composable
fun NaomiSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.2.sp
        )
        Spacer(Modifier.width(NaomiSpacing.sm + NaomiSpacing.xs))
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = NaomiShapes.hairline,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
        if (actionText != null && onActionClick != null) {
            Spacer(Modifier.width(NaomiSpacing.sm + NaomiSpacing.xs))
            Text(
                text = actionText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onActionClick)
            )
        }
    }
}

/** A thin bordered chip. Used for related topics — deliberately not a card. */
@Composable
fun NaomiPill(
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        ),
        shape = NaomiShapes.pill,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(
            NaomiShapes.hairline,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = NaomiSpacing.md, vertical = 6.dp)
        )
    }
}
