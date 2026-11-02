package com.hourlysteps.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.hourlysteps.AppContainer
import com.hourlysteps.HourlyStepsApp
import com.hourlysteps.ui.home.HomeScreen
import com.hourlysteps.ui.home.HomeViewModel
import com.hourlysteps.ui.onboarding.PermissionsScreen
import com.hourlysteps.ui.settings.PickerTarget
import com.hourlysteps.ui.settings.SettingsScreen
import com.hourlysteps.ui.settings.ValuePickerScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val container: AppContainer get() = (application as HourlyStepsApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val startOnPermissions = !container.hasActivityRecognition()
        setContent {
            MaterialTheme {
                AppScaffold {
                    HourlyStepsNavHost(container, startOnPermissions)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        container.uiVisible = true
    }

    override fun onStop() {
        container.uiVisible = false
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // Recover from force-stop, pick up permission changes, and pull fresh steps for the UI.
        lifecycleScope.launch {
            container.ensureArmed()
            if (container.hasActivityRecognition()) container.stepsRepository.flush()
        }
    }
}

private object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val PERMISSIONS = "permissions"
    const val PICKER = "picker/{target}"
    fun picker(target: PickerTarget) = "picker/${target.name}"
}

@Composable
private fun HourlyStepsNavHost(container: AppContainer, startOnPermissions: Boolean) {
    val navController = rememberSwipeDismissableNavController()
    val factory = remember(container) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeViewModel(container) as T
        }
    }

    SwipeDismissableNavHost(
        navController = navController,
        startDestination = if (startOnPermissions) Routes.PERMISSIONS else Routes.HOME,
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                viewModel = viewModel(factory = factory),
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                container = container,
                onPick = { navController.navigate(Routes.picker(it)) },
                onOpenPermissions = { navController.navigate(Routes.PERMISSIONS) },
            )
        }
        composable(Routes.PICKER) { entry ->
            val target = entry.arguments?.getString("target")
                ?.let { runCatching { PickerTarget.valueOf(it) }.getOrNull() }
                ?: PickerTarget.DAILY_GOAL
            ValuePickerScreen(container, target, onDone = { navController.popBackStack() })
        }
        composable(Routes.PERMISSIONS) {
            PermissionsScreen(
                container = container,
                onDone = {
                    if (!navController.popBackStack()) {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.PERMISSIONS) { inclusive = true }
                        }
                    }
                },
            )
        }
    }
}
