package com.hourlysteps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.room.Room
import com.hourlysteps.celebration.CelebrationManager
import com.hourlysteps.data.db.AppDatabase
import com.hourlysteps.data.settings.SettingsRepository
import com.hourlysteps.data.steps.StepsRepository
import com.hourlysteps.reminder.ReminderCheck
import com.hourlysteps.reminder.ReminderNotifier
import com.hourlysteps.reminder.ReminderScheduler
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

private const val TAG = "AppContainer"

/** Hand-wired dependency graph. One instance per process, owned by [HourlyStepsApp]. */
class AppContainer(private val context: Context) {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val clock: Clock = Clock.systemDefaultZone()

    val settingsRepository = SettingsRepository(context)

    private val database: AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, "hourly-steps.db")
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()

    val stepsRepository = StepsRepository(context, database.hourlyStepsDao(), clock)
    val reminderNotifier = ReminderNotifier(context, settingsRepository, stepsRepository, clock)
    val reminderScheduler = ReminderScheduler(context, settingsRepository, clock)
    val reminderCheck = ReminderCheck(
        context = context,
        settingsRepository = settingsRepository,
        stepsRepository = stepsRepository,
        notifier = reminderNotifier,
        scheduler = reminderScheduler,
        checkLogDao = database.checkLogDao(),
        clock = clock,
    )

    /** True while one of the app's screens is in the foreground; set by [com.hourlysteps.ui.MainActivity]. */
    @Volatile
    var uiVisible: Boolean = false

    val celebrationManager = CelebrationManager(
        context = context,
        settingsRepository = settingsRepository,
        stepsRepository = stepsRepository,
        reminderNotifier = reminderNotifier,
        clock = clock,
        isUiVisible = { uiVisible },
    )

    fun hasActivityRecognition(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED

    /** POST_NOTIFICATIONS only exists from API 33; earlier versions grant notifications implicitly. */
    fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Idempotent "make sure everything is running": registers passive monitoring and arms the
     * next alarm when the required permission is present. Called on app foreground, reboot,
     * app update, clock changes, and after settings edits.
     */
    suspend fun ensureArmed() {
        if (!hasActivityRecognition()) {
            Log.i(TAG, "ACTIVITY_RECOGNITION not granted; reminders stay disarmed")
            reminderScheduler.cancel()
            return
        }
        runCatching { stepsRepository.startPassiveMonitoring() }
            .onFailure { Log.e(TAG, "Passive monitoring registration failed", it) }
        reminderScheduler.ensureScheduled()
    }
}
