package com.hourlysteps.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import com.hourlysteps.data.settings.SettingsRepository
import com.hourlysteps.domain.AppTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The chosen theme, for colours Material has no slot for, such as the rings. */
val LocalAppTheme = compositionLocalOf { AppTheme.DEFAULT }

/**
 * Applies the wearer's theme, and changes live when they pick another. Nothing draws until the
 * setting has loaded, so the default theme never flashes first.
 */
@Composable
fun HourlyStepsTheme(settingsRepository: SettingsRepository, content: @Composable () -> Unit) {
    val themes = remember(settingsRepository) { settingsRepository.settings.map { it.theme }.distinctUntilChanged() }
    val theme by themes.collectAsStateWithLifecycle<AppTheme?>(null)
    val loaded = theme ?: return

    CompositionLocalProvider(LocalAppTheme provides loaded) {
        MaterialTheme(colorScheme = remember(loaded) { loaded.colorScheme() }, content = content)
    }
}

private fun AppTheme.colorScheme() = ColorScheme(
    primary = Color(today),
    primaryDim = Color(AppTheme.mix(today, 0xFF000000, 0.15f)),
    primaryContainer = Color(primaryContainer),
    onPrimary = Color(onAccent),
    onPrimaryContainer = Color(onPrimaryContainer),
    secondary = Color(slot),
    secondaryDim = Color(AppTheme.mix(slot, 0xFF000000, 0.15f)),
    secondaryContainer = Color(secondaryContainer),
    onSecondary = Color(onAccent),
    onSecondaryContainer = Color(onSecondaryContainer),
    tertiary = Color(goalMet),
    tertiaryDim = Color(AppTheme.mix(goalMet, 0xFF000000, 0.15f)),
    tertiaryContainer = Color(tertiaryContainer),
    onTertiary = Color(onAccent),
    onTertiaryContainer = Color(onTertiaryContainer),
    surfaceContainerLow = Color(surfaceLow),
    surfaceContainer = Color(track),
    surfaceContainerHigh = Color(surfaceHigh),
)
