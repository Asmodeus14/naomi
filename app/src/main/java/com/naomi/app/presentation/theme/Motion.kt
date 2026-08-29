package com.naomi.app.presentation.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing

/**
 * Animation timings.
 *
 * Naomi breathes rather than flashes. Movement is slow enough to read as calm
 * and small enough that it never competes with what the user is reading, so
 * durations skew long and distances stay short.
 */
object NaomiMotion {

    /** 120ms — press feedback, ripples. Must feel instant. */
    const val INSTANT = 120

    /** 220ms — hover, selection, small state changes. */
    const val QUICK = 220

    /** 320ms — screen transitions, expand/collapse. */
    const val STANDARD = 320

    /** 600ms — content settling in, staggered entrances. */
    const val GENTLE = 600

    /** 2400ms — one full idle breath of the orb. */
    const val BREATH = 2400

    /** 900ms — one listening pulse; faster than idle, still unhurried. */
    const val PULSE = 900

    /**
     * Minimum time a processing stage stays on screen once entered.
     *
     * This is anti-flicker, not simulated work: local extraction can finish in
     * a few milliseconds, and a label that appears and vanishes inside one frame
     * reads as a glitch. A stage is never shown before its work starts and never
     * outlives the pipeline — it is only held long enough to be legible.
     */
    const val STAGE_MIN_VISIBLE_MS = 450L

    /** Delay between staggered items in a list entrance. */
    const val STAGGER_MS = 70L

    /** Standard easing for entrances and exits. */
    val easing: Easing = FastOutSlowInEasing

    /** Symmetric ease for looping animations, so the loop has no seam. */
    val breathing: Easing = CubicBezierEasing(0.4f, 0f, 0.6f, 1f)
}
