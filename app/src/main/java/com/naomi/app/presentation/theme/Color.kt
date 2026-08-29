package com.naomi.app.presentation.theme

import androidx.compose.ui.graphics.Color

/*
 * Palette, from the Stitch design system.
 *
 * Light mode is the reference palette. Dark mode is a purpose-built tonal ramp
 * rather than an inversion: surfaces step through five levels so elevation is
 * expressed by tone instead of shadow, which is what keeps a dark UI from
 * reading as "the light one with a black background".
 *
 * Nothing outside this file should name a raw colour. Use MaterialTheme
 * .colorScheme for structure and LocalNaomiAccents for the semantic accents, so
 * every surface follows the selected theme.
 */

// ---- Light ----------------------------------------------------------------

val StitchLightBackground = Color(0xFFFBF9FA)
val StitchLightSurface = Color(0xFFFBF9FA)
val StitchLightSurfaceContainerLowest = Color(0xFFFFFFFF)
val StitchLightSurfaceContainerLow = Color(0xFFF5F3F4)
val StitchLightSurfaceContainer = Color(0xFFEFEDEE)
val StitchLightSurfaceContainerHigh = Color(0xFFEAE7E9)
val StitchLightSurfaceContainerHighest = Color(0xFFE4E2E3)
val StitchLightOnBackground = Color(0xFF1B1C1D)
val StitchLightOnSurface = Color(0xFF1B1C1D)
val StitchLightOnSurfaceVariant = Color(0xFF43474C)
val StitchLightPrimary = Color(0xFF4E5E6D) // Slate accent
val StitchLightOnPrimary = Color(0xFFFFFFFF)
val StitchLightPrimaryContainer = Color(0xFF667686)
val StitchLightOnPrimaryContainer = Color(0xFFFDFCFF)
val StitchLightSecondary = Color(0xFF595F66)
val StitchLightTertiary = Color(0xFF705740)
val StitchLightOutline = Color(0xFF74777C)
val StitchLightOutlineVariant = Color(0xFFC4C7CC)

// ---- Dark -----------------------------------------------------------------

val StitchDarkBackground = Color(0xFF111318)
val StitchDarkSurface = Color(0xFF111318)
val StitchDarkSurfaceContainerLowest = Color(0xFF090A0D)
val StitchDarkSurfaceContainerLow = Color(0xFF181A20)
val StitchDarkSurfaceContainer = Color(0xFF20232B)
val StitchDarkSurfaceContainerHigh = Color(0xFF282B35)
val StitchDarkSurfaceContainerHighest = Color(0xFF303440)
val StitchDarkOnBackground = Color(0xFFEDEDF0)
val StitchDarkOnSurface = Color(0xFFEDEDF0)
val StitchDarkOnSurfaceVariant = Color(0xFF9AA0AB)
val StitchDarkPrimary = Color(0xFFB8C8DA) // Light slate
val StitchDarkOnPrimary = Color(0xFF111318)
val StitchDarkPrimaryContainer = Color(0xFF394857)
val StitchDarkOnPrimaryContainer = Color(0xFFD4E4F6)
val StitchDarkSecondary = Color(0xFFC1C7CF)
val StitchDarkTertiary = Color(0xFFE2C0A4)
val StitchDarkOutline = Color(0xFF8A8D94)
val StitchDarkOutlineVariant = Color(0xFF2A2E38)

// ---- Semantic accents -----------------------------------------------------
//
// Each has a light and a dark value. The dark variants are lifted in lightness
// and dropped in saturation so they carry the same meaning without glowing
// against a near-black surface.

val AccentListeningLight = Color(0xFFC5433F)
val AccentListeningDark = Color(0xFFEB6B67)

val AccentSuccessLight = Color(0xFF2F855A)
val AccentSuccessDark = Color(0xFF68C48F)

val AccentIdeaLight = Color(0xFF9A6B12)
val AccentIdeaDark = Color(0xFFE5B65C)

val AccentTaskLight = Color(0xFF2B6CB0)
val AccentTaskDark = Color(0xFF7BB1E8)

val AccentTopicLight = Color(0xFF6B46C1)
val AccentTopicDark = Color(0xFFB794F4)
