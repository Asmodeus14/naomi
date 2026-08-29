package com.naomi.app.presentation.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The spacing scale from the Stitch design system.
 *
 * The steps are deliberately non-linear. Small increments (4/8/16) separate
 * elements inside one idea; the large jumps (48/80) separate whole ideas, and
 * doing that with generous whitespace instead of dividers or cards is what
 * keeps the app quiet.
 *
 * Use these rather than literal dp values so the rhythm stays consistent —
 * scattered magic numbers are how "six different corner radii for a card"
 * happens.
 */
object NaomiSpacing {
    /** 4dp — hairline gaps, icon-to-label. */
    val xs: Dp = 4.dp

    /** 8dp — related items in a row. */
    val sm: Dp = 8.dp

    /** 16dp — standard gap inside a group. */
    val md: Dp = 16.dp

    /** 24dp — screen gutter, gap between groups. */
    val lg: Dp = 24.dp

    /** 48dp — separates distinct sections. */
    val xl: Dp = 48.dp

    /** 80dp — separates major moments, e.g. greeting from orb. */
    val xxl: Dp = 80.dp

    /**
     * 32dp — one level of hierarchy.
     *
     * Depth in the topic tree is shown by indentation alone, never by borders
     * or background shifts.
     */
    val indent: Dp = 32.dp

    /** Standard horizontal screen gutter. */
    val gutter: Dp = lg

    /** Breathing room above the bottom edge / nav capsule. */
    val bottomSafe: Dp = 96.dp
}
