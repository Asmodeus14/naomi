package com.naomi.app.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.PriorityHigh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.naomi.app.presentation.theme.LocalNaomiAccents
import com.naomi.app.presentation.theme.NaomiMotion

/**
 * What the orb is currently saying.
 */
enum class OrbVisualState {
    IDLE,
    LISTENING,
    PROCESSING,
    SUCCESS,
    ERROR
}

/**
 * Naomi's visual identity: a small filled circle inside a halo ring.
 *
 * The orb is the one element that appears on every surface — home, capture,
 * widget — so it has to carry recognition without shouting. It breathes rather
 * than flashes: a 5% scale over about two seconds when idle, slightly faster
 * and wider while listening. Nothing glows, nothing spins fast, and it never
 * moves from where it sits.
 */
@Composable
fun NaomiOrb(
    state: OrbVisualState,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    onClick: (() -> Unit)? = null
) {
    val accents = LocalNaomiAccents.current
    val transition = rememberInfiniteTransition(label = "orb")

    val breathScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = when (state) {
            OrbVisualState.IDLE -> 1.04f
            OrbVisualState.LISTENING -> 1.10f
            OrbVisualState.PROCESSING -> 1.06f
            // Terminal states hold still: the user needs to read them, and
            // motion on an outcome reads as "still working".
            OrbVisualState.SUCCESS, OrbVisualState.ERROR -> 1f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    OrbVisualState.LISTENING -> NaomiMotion.PULSE
                    OrbVisualState.PROCESSING -> NaomiMotion.PULSE + 400
                    else -> NaomiMotion.BREATH
                },
                easing = NaomiMotion.breathing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = tween(NaomiMotion.INSTANT, easing = NaomiMotion.easing),
        label = "press"
    )

    val accent = when (state) {
        OrbVisualState.IDLE, OrbVisualState.PROCESSING -> MaterialTheme.colorScheme.primary
        OrbVisualState.LISTENING -> accents.listening
        OrbVisualState.SUCCESS -> accents.success
        OrbVisualState.ERROR -> accents.listening
    }

    val haloFill = when (state) {
        OrbVisualState.IDLE -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        else -> accent.copy(alpha = 0.12f)
    }
    val haloBorder = when (state) {
        OrbVisualState.IDLE -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
        else -> accent.copy(alpha = 0.4f)
    }

    // The clickable sits on the outer, unscaled box so the touch target stays a
    // fixed size. Putting it inside the animation makes the hit area breathe
    // along with the visual, which quietly changes where a tap lands.
    Box(
        modifier = modifier
            .size(size)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                } else Modifier
            )
            .semantics { contentDescription = state.describe() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .scale(breathScale * pressScale)
                .clip(CircleShape)
                .background(haloFill)
                .border(1.dp, haloBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            when (state) {
                OrbVisualState.IDLE, OrbVisualState.LISTENING ->
                    Box(
                        modifier = Modifier
                            .size(size * 0.45f)
                            .clip(CircleShape)
                            .background(accent)
                    )

                OrbVisualState.PROCESSING ->
                    Box(
                        modifier = Modifier
                            .size(size * 0.48f)
                            .clip(CircleShape)
                            .background(accent),
                        contentAlignment = Alignment.Center
                    ) {
                        // An icon rather than a text glyph: text would be sized
                        // in sp and grow with the user's font scale, breaking
                        // out of the circle at large accessibility settings.
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(size * 0.24f)
                        )
                    }

                OrbVisualState.SUCCESS ->
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(size * 0.42f)
                    )

                OrbVisualState.ERROR ->
                    Icon(
                        imageVector = Icons.Outlined.PriorityHigh,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(size * 0.40f)
                    )
            }
        }
    }
}

private fun OrbVisualState.describe(): String = when (this) {
    OrbVisualState.IDLE -> "Naomi, ready. Tap to speak"
    OrbVisualState.LISTENING -> "Naomi is listening. Tap to finish"
    OrbVisualState.PROCESSING -> "Naomi is remembering"
    OrbVisualState.SUCCESS -> "Remembered"
    OrbVisualState.ERROR -> "Something went wrong"
}
