package com.hourlysteps.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.hourlysteps.AppContainer
import com.hourlysteps.domain.AppTheme
import kotlinx.coroutines.launch

/** Tap a theme and the whole app changes straight away, so it's easy to try them all. */
@Composable
fun ThemeScreen(container: AppContainer) {
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(null)
    val loaded = settings ?: return
    val scope = rememberCoroutineScope()
    val listState = rememberScalingLazyListState()

    ScreenScaffold(scrollState = listState) { padding ->
        ScalingLazyColumn(state = listState, contentPadding = padding) {
            item { ListHeader { Text("Theme") } }
            AppTheme.entries.forEach { theme ->
                item(key = theme.name) {
                    RadioButton(
                        selected = loaded.theme == theme,
                        onSelect = { scope.launch { container.setTheme(theme) } },
                        modifier = Modifier.fillMaxWidth(),
                        icon = { ThemeSwatch(theme) },
                        label = { Text(theme.label) },
                        secondaryLabel = { Text(theme.description) },
                    )
                }
            }
        }
    }
}

/** A small pair of rings in the theme's own colours. */
@Composable
private fun ThemeSwatch(theme: AppTheme) {
    Box(
        modifier = Modifier.size(26.dp).background(Color(theme.today), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(14.dp).background(Color(theme.slot), CircleShape))
    }
}
