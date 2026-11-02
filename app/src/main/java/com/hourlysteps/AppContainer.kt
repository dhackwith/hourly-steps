package com.hourlysteps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
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
import com.hourlysteps.data.steps.DayStatusProvider
import com.hourlysteps.data.steps.StepsRepository
import com.hourlysteps.data.steps.StreakRepository
import com.hourlysteps.domain.AppTheme
import com.hourlysteps.domain.CheckRecord
import com.hourlysteps.domain.SystemZoneClock
import com.hourlysteps.reminder.Charger
import com.hourlysteps.reminder.NotificationChannels
import com.hourlysteps.reminder.ReminderCheck
import com.hourlysteps.reminder.ReminderDismissal
import com.hourlysteps.reminder.ReminderNotifier
import com.hourlysteps.reminder.ReminderScheduler
import com.hourlysteps.surfaces.SurfaceUpdater
import java.time.Clock
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val TAG = "AppContainer"

/** Hand-wired dependency graph. One instance per process, owned by [HourlyStepsApp]. */
class AppContainer(private val context: Context) {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val clock: Clock = SystemZoneClock()

    val settingsRepository = SettingsRepository(context)

    private val database: AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, "hourly-steps.db")
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()

    val stepsRepository = StepsRepository(context, database.stepPointDao(), clock)
    val dayStatusProvider = DayStatusProvider(settingsRepository, stepsRepository, clock)
    val surfaceUpdater = SurfaceUpdater(context)
    val activityStore = ActivityStore(context)
    val streakRepository = StreakRepository(database.daySummaryDao(), stepsRepository, settingsRepository, clock)
    val notificationChannels = NotificationChannels(context)
    val reminderNotifier = ReminderNotifier(context, notificationChannels, isUiVisible = { uiVisible })
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

    /** Today's check outcomes, for the Today page. */
    fun observeDayChecks(date: LocalDate): Flow<List<CheckRecord>> =
        database.checkLogDao().observeForDate(date.toString()).map { rows ->
            rows.map { CheckRecord(it.timeMillis, it.slotStartMinute, it.stepsInSlot, it.notified, it.skippedReason, it.isFollowUp) }
        }

    private val startedScreens = AtomicInteger(0)

    /** True while one of the app's screens is in the foreground. */
    val uiVisible: Boolean get() = startedScreens.get() > 0

    /** A count rather than a flag: one screen starts before the one beneath it stops. */
    fun onScreenStarted() {
        startedScreens.incrementAndGet()
    }

    fun onScreenStopped() {
        startedScreens.decrementAndGet()
    }

    val celebrationSurface = AndroidCelebrationSurface(context, reminderNotifier, notificationChannels, isUiVisible = { uiVisible })
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
        surfaceUpdater.updateNow()
        if (!hasActivityRecognition()) {
            Log.i(TAG, "ACTIVITY_RECOGNITION not granted; reminders stay disarmed")
            reminderScheduler.cancel()
            return
        }
        runCatching { stepsRepository.startPassiveMonitoring() }
            .onFailure { Log.e(TAG, "Passive monitoring registration failed", it) }
        reminderScheduler.ensureScheduled()
    }

    /** Pauses through [through] inclusive; a null date resumes. Clears any reminder on screen. */
    suspend fun setPausedThrough(through: LocalDate?) {
        settingsRepository.update { it.copy(pausedThrough = through) }
        if (through != null) {
            reminderNotifier.cancel()
            reminderScheduler.cancelFollowUp()
        }
        ensureArmed()
    }

    /** Changes how often reminders come. A reminder or follow-up from the old interval no longer applies. */
    suspend fun setSlotMinutes(minutes: Int) {
        settingsRepository.update { it.copy(schedule = it.schedule.copy(slotMinutes = minutes)) }
        reminderNotifier.cancel()
        reminderScheduler.cancelFollowUp()
        ensureArmed()
    }

    /** Changes the colours. The tile holds its own copy of them, so it needs telling. */
    suspend fun setTheme(theme: AppTheme) {
        settingsRepository.update { it.copy(theme = theme) }
        surfaceUpdater.updateNow()
    }

    /** The "Pause today" button on a reminder. */
    suspend fun pauseToday() = setPausedThrough(ZonedDateTime.now(clock).toLocalDate())
}
