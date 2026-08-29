package com.naomi.app.presentation.ambient

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naomi.app.presentation.components.NaomiOrb
import com.naomi.app.presentation.components.NaomiPill
import com.naomi.app.presentation.components.NaomiRow
import com.naomi.app.presentation.components.NaomiSectionHeader
import com.naomi.app.presentation.components.OrbVisualState
import com.naomi.app.presentation.theme.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmbientScreen(
    viewModel: AmbientViewModel,
    onNavigateBack: () -> Unit
) {
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()

    val hours = sessionState.durationSeconds / 3600
    val minutes = (sessionState.durationSeconds % 3600) / 60
    val seconds = sessionState.durationSeconds % 60
    val timeFormatted = String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Ambient Mode",
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
        // One LazyColumn for the whole screen. A LazyColumn nested inside a
        // fixed-height Column has no scroll of its own to give, so on a short
        // device the header pushed the session list off the bottom with no way
        // to reach it.
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = NaomiSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(NaomiSpacing.sm)
        ) {
            item {
                Spacer(modifier = Modifier.height(NaomiSpacing.md))

                Text(
                    text = if (sessionState.isActive) "CONTINUOUS LISTENING" else "AMBIENT MEMORY",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (sessionState.isActive) LocalNaomiAccents.current.listening else MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.4.sp
                )
            }

            item {
                Text(
                    text = "Continuous capture for meetings, lectures, and brainstorming.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = NaomiSpacing.md),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            item {
                Spacer(modifier = Modifier.height(NaomiSpacing.md))
                Text(
                    text = timeFormatted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            if (sessionState.isActive && !sessionState.currentTopic.isNullOrBlank()) {
                item {
                    // A chip is one of the few things that still earns an edge:
                    // it is a single floating token, not a row in a list. Reusing
                    // NaomiPill rather than rebuilding it keeps that judgement in
                    // one place.
                    NaomiPill(text = "Topic: ${sessionState.currentTopic}")
                }
            }

            item {
                Spacer(modifier = Modifier.height(NaomiSpacing.md))
                NaomiOrb(
                    state = if (sessionState.isActive) OrbVisualState.LISTENING else OrbVisualState.IDLE,
                    size = 104.dp,
                    onClick = { viewModel.toggleSession() }
                )
            }

            item {
                Text(
                    text = if (sessionState.isActive) "Tap to pause session" else "Tap to start continuous capture",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (sessionState.recentNotes.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(NaomiSpacing.lg))
                    NaomiSectionHeader(
                        title = "CAPTURED IN THIS SESSION (${sessionState.processedSegmentsCount})"
                    )
                }

                items(sessionState.recentNotes) { noteTitle ->
                    NaomiRow {
                        Text(
                            text = noteTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(NaomiSpacing.xl))
            }
        }
    }
}

