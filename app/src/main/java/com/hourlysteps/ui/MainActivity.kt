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
import androidx.navigation.NavBackStackEntry
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.hourlysteps.AppContainer
import com.hourlysteps.HourlyStepsApp
import com.hourlysteps.domain.PlanId
import com.hourlysteps.ui.home.HomeScreen
import com.hourlysteps.ui.home.HomeViewModel
import com.hourlysteps.ui.onboarding.PermissionsScreen
import com.hourlysteps.ui.settings.PauseScreen
import com.hourlysteps.ui.settings.PickerTarget
import com.hourlysteps.ui.settings.PlanEditorScreen
import com.hourlysteps.ui.settings.SettingsScreen
import com.hourlysteps.ui.settings.ThemeScreen
import com.hourlysteps.ui.settings.ValuePickerScreen
import com.hourlysteps.ui.settings.WeekEditorScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val container: AppContainer get() = (application as HourlyStepsApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val startOnPermissions = !container.permissions.hasActivityRecognition()
        setContent {
            HourlyStepsTheme(container.settingsRepository) {
                AppScaffold {
                    HourlyStepsNavHost(container, startOnPermissions)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        container.visibleScreens.onScreenStarted()
    }

    override fun onStop() {
        container.visibleScreens.onScreenStopped()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // Recover from force-stop, pick up permission changes, and pull fresh steps for the UI.
        lifecycleScope.launch {
            container.reminderControls.ensureArmed()
            if (container.permissions.hasActivityRecognition()) container.stepsRepository.flush()
        }
    }
}

private object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val PERMISSIONS = "permissions"
    const val WEEK = "week"
    const val PAUSE = "pause"
    const val THEME = "theme"
    const val PLAN = "plan/{plan}"
    const val PICKER = "picker/{target}/{plan}"
    fun plan(id: PlanId) = "plan/${id.name}"
    fun picker(target: PickerTarget, id: PlanId = PlanId.A) = "picker/${target.name}/${id.name}"
}

private fun NavBackStackEntry.planArg(): PlanId =
    arguments?.getString("plan")?.let { runCatching { PlanId.valueOf(it) }.getOrNull() } ?: PlanId.A

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
                onOpenPlan = { navController.navigate(Routes.plan(it)) },
                onOpenWeek = { navController.navigate(Routes.WEEK) },
                onOpenPause = { navController.navigate(Routes.PAUSE) },
                onOpenTheme = { navController.navigate(Routes.THEME) },
                onPickInactivity = { navController.navigate(Routes.picker(PickerTarget.INACTIVITY_THRESHOLD)) },
                onOpenPermissions = { navController.navigate(Routes.PERMISSIONS) },
            )
        }
        composable(Routes.PLAN) { entry ->
            val planId = entry.planArg()
            PlanEditorScreen(
                settingsRepository = container.settingsRepository,
                planId = planId,
                onPick = { navController.navigate(Routes.picker(it, planId)) },
            )
        }
        composable(Routes.WEEK) { WeekEditorScreen(container.settingsRepository, container.reminderControls) }
        composable(Routes.PAUSE) { PauseScreen(container.settingsRepository, container.reminderControls, container.clock, onDone = { navController.popBackStack() }) }
        composable(Routes.THEME) { ThemeScreen(container.settingsRepository, container.reminderControls) }
        composable(Routes.PICKER) { entry ->
            val target = entry.arguments?.getString("target")
                ?.let { runCatching { PickerTarget.valueOf(it) }.getOrNull() }
                ?: PickerTarget.DAILY_GOAL
            ValuePickerScreen(container.settingsRepository, container.reminderControls, target, entry.planArg(), onDone = { navController.popBackStack() })
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
