package com.naomi.app.presentation.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naomi.app.ai.speech.Transcript
import com.naomi.app.data.database.entities.MemoryEntryEntity
import com.naomi.app.presentation.components.*
import com.naomi.app.presentation.theme.NaomiShapes
import com.naomi.app.presentation.theme.NaomiSpacing
import java.util.Calendar

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToTasks: () -> Unit,
    onNavigateToTopics: () -> Unit,
    onNavigateToAmbient: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToAsk: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToNoteDetail: (Long) -> Unit
) {
    val context = LocalContext.current
    val recentNotes by viewModel.recentNotes.collectAsStateWithLifecycle()
    val captureState by viewModel.captureState.collectAsStateWithLifecycle()
    val audioRms by viewModel.audioRms.collectAsStateWithLifecycle()

    // rememberSaveable so a rotation mid-sentence doesn't discard what was typed.
    var showTextInput by rememberSaveable { mutableStateOf(false) }
    var typedThought by rememberSaveable { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.startCapture() }

    // Asked for only once something is actually waiting to be delivered. A
    // reminder cannot be shown without this on Android 13+, and it was never
    // requested, so reminders were silently dropped.
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Declining is a real answer; the task is still saved and visible. */ }

    LaunchedEffect(Unit) {
        viewModel.reminderScheduled.collect {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@collect
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun requestCapture() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.startCapture()
        else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    // Once a memory is saved, show it. The sheet holds the success state briefly
    // so the user sees what Naomi understood before the screen changes.
    LaunchedEffect(captureState) {
        val saved = captureState as? CaptureUiState.Saved ?: return@LaunchedEffect
        viewModel.acknowledge()
        onNavigateToNoteDetail(saved.note.id)
    }

    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..22 -> "Good evening"
            else -> "Good night"
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NaomiSpacing.gutter, vertical = NaomiSpacing.sm + 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Naomi",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Row {
                    IconButton(onNavigateToSearch) {
                        Icon(Icons.Outlined.Search, "Search", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onNavigateToTasks) {
                        Icon(Icons.Outlined.CheckCircle, "Tasks", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onNavigateToAmbient) {
                        Icon(Icons.Outlined.GraphicEq, "Ambient mode", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onNavigateToSettings) {
                        Icon(Icons.Outlined.Settings, "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = NaomiSpacing.gutter),
                verticalArrangement = Arrangement.spacedBy(NaomiSpacing.md + NaomiSpacing.xs)
            ) {
                item {
                    Spacer(Modifier.height(NaomiSpacing.sm))
                    Text(
                        text = greeting,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(NaomiSpacing.xs))
                    Text(
                        text = "What would you\nlike to remember?",
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        lineHeight = 44.sp
                    )
                    Spacer(Modifier.height(NaomiSpacing.xl - NaomiSpacing.md))
                }

                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // The hero orb reflects what Naomi is actually doing
                        // rather than sitting permanently idle.
                        NaomiOrb(
                            state = captureState.toOrbState(),
                            size = 116.dp,
                            onClick = ::requestCapture
                        )
                        Spacer(Modifier.height(NaomiSpacing.md + 2.dp))
                        Text(
                            text = "TAP TO SPEAK",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.6.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(NaomiSpacing.sm))
                        Text(
                            text = "or type it instead",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable { showTextInput = true }
                        )

                        // The other half of the promise. Speaking puts things in;
                        // this is how they come back out, and it needs to be
                        // visible from the first screen or nobody finds it.
                        Spacer(Modifier.height(NaomiSpacing.lg))
                        Text(
                            text = "Ask what you've already told me →",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable(onClick = onNavigateToAsk)
                                .padding(vertical = NaomiSpacing.xs)
                        )
                    }
                    Spacer(Modifier.height(NaomiSpacing.md + NaomiSpacing.xs))
                }

                item {
                    NaomiSectionHeader(
                        title = "Recent",
                        actionText = if (recentNotes.isNotEmpty()) "Topics →" else null,
                        onActionClick = onNavigateToTopics
                    )
                }

                if (recentNotes.isEmpty()) {
                    item {
                        NaomiEmptyState(
                            title = "Nothing here yet.",
                            message = "Say something worth remembering. Naomi will file it for you."
                        )
                    }
                } else {
                    items(recentNotes, key = { it.id }) { note ->
                        MemoryRow(note = note, onClick = { onNavigateToNoteDetail(note.id) })
                    }
                }

                item { Spacer(Modifier.height(NaomiSpacing.xl)) }
            }

            CaptureSheet(
                state = captureState,
                audioRms = audioRms,
                onStop = viewModel::stopCapture,
                onDismiss = viewModel::cancelCapture,
                onRetry = ::requestCapture
            )

            if (showTextInput) {
                AlertDialog(
                    onDismissRequest = { showTextInput = false },
                    shape = NaomiShapes.large,
                    // M3's default dialog container is derived from the seed
                    // colour and lands on pink, which appears nowhere else here.
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    title = { Text("Remember a thought", style = MaterialTheme.typography.titleLarge) },
                    text = {
                        OutlinedTextField(
                            value = typedThought,
                            onValueChange = { typedThought = it },
                            placeholder = { Text("What should Naomi remember?") },
                            modifier = Modifier.fillMaxWidth().height(130.dp),
                            maxLines = 5,
                            shape = NaomiShapes.medium
                        )
                    },
                    confirmButton = {
                        TextButton(
                            enabled = typedThought.isNotBlank(),
                            onClick = {
                                val text = typedThought
                                typedThought = ""
                                showTextInput = false
                                // Typed, not heard. Recorded as such so nothing
                                // downstream treats these as words a recogniser
                                // guessed at and might have got wrong.
                                viewModel.processThought(
                                    Transcript.of(text),
                                    source = MemoryEntryEntity.SOURCE_TYPED
                                )
                            }
                        ) { Text("Remember", fontWeight = FontWeight.SemiBold) }
                    },
                    dismissButton = {
                        TextButton({ showTextInput = false }) {
                            Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                )
            }
        }
    }
}

private fun CaptureUiState.toOrbState(): OrbVisualState = when (this) {
    is CaptureUiState.Idle -> OrbVisualState.IDLE
    is CaptureUiState.Listening -> OrbVisualState.LISTENING
    is CaptureUiState.Processing -> OrbVisualState.PROCESSING
    is CaptureUiState.Saved -> OrbVisualState.SUCCESS
    is CaptureUiState.Failed -> OrbVisualState.ERROR
}
