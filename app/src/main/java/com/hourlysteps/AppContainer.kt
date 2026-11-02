package com.hourlysteps

import android.content.Context
import androidx.room.Room
import com.hourlysteps.celebration.AndroidCelebrationSurface
import com.hourlysteps.celebration.CelebrationPresenter
import com.hourlysteps.celebration.GoalWatcher
import com.hourlysteps.celebration.PrefsCelebrationMemory
import com.hourlysteps.data.db.AppDatabase
import com.hourlysteps.data.db.MIGRATION_1_2
import com.hourlysteps.data.db.MIGRATION_2_3
import com.hourlysteps.data.db.MIGRATION_3_4
import com.hourlysteps.data.settings.SettingsRepository
import com.hourlysteps.data.steps.ActivityStore
import com.hourlysteps.data.steps.CheckHistory
import com.hourlysteps.data.steps.DayStatusProvider
import com.hourlysteps.data.steps.StepsRepository
import com.hourlysteps.data.steps.StreakRepository
import com.hourlysteps.domain.SystemZoneClock
import com.hourlysteps.reminder.Charger
import com.hourlysteps.reminder.NotificationChannels
import com.hourlysteps.reminder.ReminderCheck
import com.hourlysteps.reminder.ReminderControls
import com.hourlysteps.reminder.ReminderDismissal
import com.hourlysteps.reminder.ReminderNotifier
import com.hourlysteps.reminder.ReminderScheduler
import com.hourlysteps.surfaces.SurfaceUpdater
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Hand-wired dependency graph. One instance per process, owned by [HourlyStepsApp]. */
class AppContainer(context: Context) {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val clock: Clock = SystemZoneClock()
    val permissions = Permissions(context)
    val visibleScreens = VisibleScreens()

    val settingsRepository = SettingsRepository(context)

    private val database: AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, "hourly-steps.db")
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()

    val stepsRepository = StepsRepository(context, database.stepPointDao(), clock)
    val dayStatusProvider = DayStatusProvider(settingsRepository, stepsRepository, clock)
    val checkHistory = CheckHistory(database.checkLogDao())
    val surfaceUpdater = SurfaceUpdater(context)
    val activityStore = ActivityStore(context)
    val streakRepository = StreakRepository(database.daySummaryDao(), stepsRepository, settingsRepository, clock)
    val notificationChannels = NotificationChannels(context)
    val reminderNotifier = ReminderNotifier(context, notificationChannels, isUiVisible = { visibleScreens.anyVisible })
    val reminderDismissal = ReminderDismissal(reminderNotifier, settingsRepository, stepsRepository, clock)
    val reminderScheduler = ReminderScheduler(context, settingsRepository, clock)
    val reminderCheck = ReminderCheck(
        settingsSource = settingsRepository,
        stepCounts = stepsRepository,
        activitySource = activityStore,
        streakTracker = streakRepository,
        notifier = reminderNotifier,
        alarms = reminderScheduler,
        checkLogDao = database.checkLogDao(),
        surfaces = surfaceUpdater,
        isOnCharger = { Charger.isConnected(context) },
        clock = clock,
    )
    val reminderControls = ReminderControls(
        settingsEditor = settingsRepository,
        stepMonitoring = stepsRepository,
        notifier = reminderNotifier,
        arming = reminderScheduler,
        surfaces = surfaceUpdater,
        hasActivityRecognition = permissions::hasActivityRecognition,
        clock = clock,
    )

    val celebrationSurface = AndroidCelebrationSurface(context, reminderNotifier, notificationChannels, isUiVisible = { visibleScreens.anyVisible })
    val celebrationPresenter = CelebrationPresenter(celebrationSurface, settingsRepository)
    val goalWatcher = GoalWatcher(
        presenter = celebrationPresenter,
        memory = PrefsCelebrationMemory(context),
        stepCheckAlarm = reminderScheduler,
        settingsSource = settingsRepository,
        stepCounts = stepsRepository,
        activitySource = activityStore,
        streakTracker = streakRepository,
        clock = clock,
    )
}
