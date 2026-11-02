package com.hourlysteps.ui.onboarding

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.hourlysteps.AppContainer
import kotlinx.coroutines.launch

@Composable
fun PermissionsScreen(container: AppContainer, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberScalingLazyListState()
    // Bumped on resume / permission result to re-read grant state.
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refresh++
        scope.launch { container.ensureArmed() }
    }

    val activityOk = remember(refresh) { container.hasActivityRecognition() }
    val notifOk = remember(refresh) { container.hasNotificationPermission() }
    val exactOk = remember(refresh) { container.reminderScheduler.canScheduleExact() }

    ScreenScaffold(scrollState = listState) { padding ->
        ScalingLazyColumn(state = listState, contentPadding = padding) {
            item { ListHeader { Text("Permissions") } }
            item {
                Text(
                    "Hourly Steps reads your steps and reminds you at :50 when you're short.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            item {
                Button(
                    onClick = {
                        val perms = buildList {
                            add(Manifest.permission.ACTIVITY_RECOGNITION)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                        launcher.launch(perms.toTypedArray())
                    },
                    enabled = !(activityOk && notifOk),
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Steps & notifications") },
                    secondaryLabel = { Text(if (activityOk && notifOk) "Granted" else "Required") },
                )
            }
            item {
                FilledTonalButton(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val packageUri = "package:${context.packageName}".toUri()
                            val intent = Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                .setData(packageUri)
                            runCatching { context.startActivity(intent) }
                                .onFailure {
                                    // Some watch builds lack this screen; fall back to app info.
                                    runCatching {
                                        context.startActivity(
                                            Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                                .setData(packageUri),
                                        )
                                    }
                                }
                        }
                    },
                    enabled = !exactOk,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("On-time reminders") },
                    secondaryLabel = { Text(if (exactOk) "Granted" else "Optional: exact :50 timing") },
                )
            }
            item {
                Button(
                    onClick = {
                        scope.launch {
                            container.ensureArmed()
                            onDone()
                        }
                    },
                    enabled = activityOk,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (exactOk) "Done" else "Continue") },
                )
            }
        }
    }
}
