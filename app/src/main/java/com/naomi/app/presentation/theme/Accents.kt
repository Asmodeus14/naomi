package com.naomi.app.presentation.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic colours that carry meaning rather than structure.
 *
 * These sit outside the Material colour scheme because Material has no slot for
 * "this is an idea" or "Naomi is listening". Exposing them through a
 * CompositionLocal means a screen can never accidentally reach for a fixed
 * dark-palette value and use it in light mode.
 */
@Immutable
data class NaomiAccents(
    val listening: Color,
    val success: Color,
    val idea: Color,
    val task: Color,
    val topic: Color
)

val LightAccents = NaomiAccents(
    listening = AccentListeningLight,
    success = AccentSuccessLight,
    idea = AccentIdeaLight,
    task = AccentTaskLight,
    topic = AccentTopicLight
)

val DarkAccents = NaomiAccents(
    listening = AccentListeningDark,
    success = AccentSuccessDark,
    idea = AccentIdeaDark,
    task = AccentTaskDark,
    topic = AccentTopicDark
)

val LocalNaomiAccents = staticCompositionLocalOf { LightAccents }
