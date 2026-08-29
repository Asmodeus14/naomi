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
 * The one card in the app.
 *
 * Every bordered container uses this, so radius, border weight and surface tint
 * stay identical everywhere. Previously this exact block was hand-written in
 * nine files at six different radii.
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
