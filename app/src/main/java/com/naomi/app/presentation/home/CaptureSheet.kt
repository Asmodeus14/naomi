package com.naomi.app.presentation.home

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naomi.app.presentation.components.NaomiOrb
import com.naomi.app.presentation.components.OrbVisualState
import com.naomi.app.presentation.components.WaveformVisualizer
import com.naomi.app.presentation.theme.LocalNaomiAccents
import com.naomi.app.presentation.theme.NaomiMonoLabel
import com.naomi.app.presentation.theme.NaomiMotion
import com.naomi.app.presentation.theme.NaomiSpacing

/**
 * The capture surface: listening, remembering, and the outcome.
 *
 * Every label here is driven by [CaptureUiState], which the ViewModel advances
 * from the pipeline's own emissions. An earlier version advanced these stages on
 * a hardcoded 900ms timer regardless of what was happening underneath, which
 * made the words on screen decoration rather than information.
 */
@Composable
fun CaptureSheet(
    state: CaptureUiState,
    audioRms: Float,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    AnimatedVisibility(
        visible = state !is CaptureUiState.Idle,
        enter = fadeIn(tween(NaomiMotion.STANDARD)),
        exit = fadeOut(tween(NaomiMotion.QUICK))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NaomiSpacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (state) {
                    is CaptureUiState.Listening -> Listening(state, audioRms, onStop)
                    is CaptureUiState.Processing -> Processing(state)
                    is CaptureUiState.Failed -> Failed(state, onRetry, onDismiss)
                    is CaptureUiState.Saved -> Saved()
                    CaptureUiState.Idle -> Unit
                }
            }
        }
    }
}

@Composable
private fun Listening(
    state: CaptureUiState.Listening,
    audioRms: Float,
    onStop: () -> Unit
) {
    NaomiOrb(state = OrbVisualState.LISTENING, size = 96.dp, onClick = onStop)

    Spacer(Modifier.height(NaomiSpacing.xl))
    Text(
        text = formatDuration(state.elapsedSeconds),
        style = NaomiMonoLabel,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(Modifier.height(NaomiSpacing.md))
    Text(
        text = "LISTENING",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.6.sp,
        color = LocalNaomiAccents.current.listening
    )

    Spacer(Modifier.height(NaomiSpacing.lg))
    WaveformVisualizer(
        amplitude = audioRms,
        isListening = true,
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
    )

    // Live transcript, shown only once there is something to show.
    AnimatedVisibility(visible = state.partialTranscript.isNotBlank()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(NaomiSpacing.xl))
            Text(
                text = state.partialTranscript,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 4
            )
        }
    }

    Spacer(Modifier.height(NaomiSpacing.xl))
    Text(
        text = "Tap to finish",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline
    )
}

@Composable
private fun Processing(state: CaptureUiState.Processing) {
    NaomiOrb(state = OrbVisualState.PROCESSING, size = 96.dp)

    Spacer(Modifier.height(NaomiSpacing.xl))
    Text(
        text = "Remembering...",
        style = MaterialTheme.typography.displaySmall,
        color = MaterialTheme.colorScheme.onBackground
    )

    Spacer(Modifier.height(NaomiSpacing.sm))
    // Only the step that is genuinely running is named — no checklist of
    // stages implying work that hasn't started.
    Crossfade(
        targetState = state.step,
        animationSpec = tween(NaomiMotion.STANDARD),
        label = "processing_step"
    ) { step ->
        Text(
            text = when (step) {
                CaptureUiState.Processing.Step.TRANSCRIBING -> "Transcribing"
                CaptureUiState.Processing.Step.UNDERSTANDING -> "Understanding"
                CaptureUiState.Processing.Step.ORGANIZING -> "Organizing"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun Saved() {
    NaomiOrb(state = OrbVisualState.SUCCESS, size = 96.dp)
    Spacer(Modifier.height(NaomiSpacing.xl))
    Text(
        text = "Remembered",
        style = MaterialTheme.typography.displaySmall,
        color = MaterialTheme.colorScheme.onBackground
    )
}

@Composable
private fun Failed(
    state: CaptureUiState.Failed,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    NaomiOrb(state = OrbVisualState.ERROR, size = 96.dp)

    Spacer(Modifier.height(NaomiSpacing.xl))
    Text(
        text = state.reason.message,
        style = MaterialTheme.typography.displaySmall,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(NaomiSpacing.sm))
    Text(
        text = state.reason.recovery,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(NaomiSpacing.xl))
    Row(horizontalArrangement = Arrangement.spacedBy(NaomiSpacing.md)) {
        TextButton(onClick = onDismiss) {
            Text("Not now", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(onClick = { onDismiss(); onRetry() }) {
            Text("Try again", fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun formatDuration(seconds: Long): String =
    "%02d:%02d".format(seconds / 60, seconds % 60)
