package com.naomi.app.presentation.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.naomi.app.domain.model.AppThemeMode

private val StitchDarkColorScheme = darkColorScheme(
    primary = StitchDarkPrimary,
    onPrimary = StitchDarkOnPrimary,
    primaryContainer = StitchDarkPrimaryContainer,
    onPrimaryContainer = StitchDarkOnPrimaryContainer,
    secondary = StitchDarkSecondary,
    onSecondary = StitchDarkOnPrimary,
    secondaryContainer = StitchDarkSurfaceContainerLow,
    onSecondaryContainer = StitchDarkOnSurfaceVariant,
    tertiary = StitchDarkTertiary,
    onTertiary = StitchDarkOnPrimary,
    background = StitchDarkBackground,
    onBackground = StitchDarkOnBackground,
    surface = StitchDarkSurface,
    onSurface = StitchDarkOnSurface,
    surfaceVariant = StitchDarkSurfaceContainerLow,
    onSurfaceVariant = StitchDarkOnSurfaceVariant,
    outline = StitchDarkOutline,
    outlineVariant = StitchDarkOutlineVariant
)

private val StitchLightColorScheme = lightColorScheme(
    primary = StitchLightPrimary,
    onPrimary = StitchLightOnPrimary,
    primaryContainer = StitchLightPrimaryContainer,
    onPrimaryContainer = StitchLightOnPrimaryContainer,
    secondary = StitchLightSecondary,
    onSecondary = StitchLightOnPrimary,
    secondaryContainer = StitchLightSurfaceContainerLow,
    onSecondaryContainer = StitchLightOnSurfaceVariant,
    tertiary = StitchLightTertiary,
    onTertiary = StitchLightOnPrimary,
    background = StitchLightBackground,
    onBackground = StitchLightOnBackground,
    surface = StitchLightSurface,
    onSurface = StitchLightOnSurface,
    surfaceVariant = StitchLightSurfaceContainerLow,
    onSurfaceVariant = StitchLightOnSurfaceVariant,
    outline = StitchLightOutline,
    outlineVariant = StitchLightOutlineVariant
)

@Composable
fun NaomiTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    darkTheme: Boolean = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    },
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) StitchDarkColorScheme else StitchLightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.background.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalNaomiAccents provides if (darkTheme) DarkAccents else LightAccents
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = NaomiTypography,
            content = content
        )
    }
}

/** Convenience accessor mirroring `MaterialTheme.colorScheme`. */
object NaomiTheme {
    val accents: NaomiAccents
        @Composable get() = LocalNaomiAccents.current
}

