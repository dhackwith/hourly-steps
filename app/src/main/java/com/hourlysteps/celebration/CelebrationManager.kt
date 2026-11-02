package com.hourlysteps.celebration

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import com.hourlysteps.R
import com.hourlysteps.data.settings.SettingsRepository
import com.hourlysteps.data.steps.ActivityStore
import com.hourlysteps.data.steps.StepsRepository
import com.hourlysteps.data.steps.StreakRepository
import com.hourlysteps.domain.Celebration
import com.hourlysteps.domain.CelebrationDecider
import com.hourlysteps.domain.CelebrationKind
import com.hourlysteps.domain.SlotWording
import com.hourlysteps.domain.Slots
import com.hourlysteps.domain.Streaks
import com.hourlysteps.domain.UserActivity
import com.hourlysteps.domain.VibrationStrength
import com.hourlysteps.reminder.Attention
import com.hourlysteps.reminder.Haptics
import com.hourlysteps.reminder.ReminderNotifier
import com.hourlysteps.reminder.ReminderScheduler
import com.hourlysteps.ui.formatSteps
import java.time.Clock
import java.time.ZonedDateTime
import kotlinx.coroutines.delay

private const val TAG = "CelebrationManager"

/**
 * Detects the moment a slot's goal or the daily goal is reached and celebrates it once.
 *
 * Reaching the screen from the background: the celebration activity is started directly when
 * the app's UI is visible, or when the app holds the "display over other apps" permission, which
 * Android requires of a background app opening a screen. Otherwise a high-priority notification
 * is posted with a full-screen intent, which launches the celebration when the watch screen is
 * off (like an alarm) and shows as a heads-up alert when it is on. If the full-screen intent
 * permission is not granted, the notification still alerts and tapping it opens the celebration.
 *
 * A screen opened from the background is gone in seconds and may go unseen, so it leaves a silent
 * notification behind.
 */
class CelebrationManager(
    private val context: Context,
    private val reminderScheduler: ReminderScheduler,
    private val settingsRepository: SettingsRepository,
    private val stepsRepository: StepsRepository,
    private val activityStore: ActivityStore,
    private val streakRepository: StreakRepository,
    private val reminderNotifier: ReminderNotifier,
    private val clock: Clock,
    private val isUiVisible: () -> Boolean,
) {
    private val prefs = context.getSharedPreferences("celebrations", Context.MODE_PRIVATE)

    @Volatile
    private var screenOpened = false

    private val notificationManager: NotificationManager
        get() = context.getSystemService(NotificationManager::class.java)

    /**
     * Called after each batch of new step data. Close to a goal it also arranges to ask for steps
     * again soon, because batches can arrive minutes apart. A look that finds no new steps ends
     * that, so standing still near a goal costs nothing.
     */
    suspend fun onStepsRecorded(hadNewSteps: Boolean = true) {
        val now = ZonedDateTime.now(clock)
        val date = now.toLocalDate()
        val settings = settingsRepository.current()
        if (settings.isPaused(date)) return
        // Celebrate during a workout, but never wake someone up to do it.
        if (settings.skipWhileAsleep && activityStore.currentAt(now.toInstant().toEpochMilli()) == UserActivity.ASLEEP) return
        val plan = settings.schedule.planFor(date.dayOfWeek) ?: return
        val slot = Slots.at(settings.schedule, now)
        val total = stepsRepository.dayTotal(date)
        val inSlot = slot?.let { stepsRepository.stepsInSlot(it) } ?: 0

        val slotKey = slot?.let { "$date#${it.startMinute}" }
        val hourly = slot?.let {
            CelebrationDecider.decide(
                settings = settings,
                plan = plan,
                slot = it,
                stepsInSlot = inSlot,
                todayTotal = total,
                alreadyCelebratedThisSlot = prefs.getString(KEY_LAST_SLOT, null) == slotKey,
            )
        }
        val daily = CelebrationDecider.decideDaily(
            settings = settings,
            plan = plan,
            stepsInSlot = inSlot,
            todayTotal = total,
            alreadyCelebratedToday = prefs.getString(KEY_LAST_DAY, null) == date.toString(),
        )

        // When both goals fall in the same batch, the daily moment is the one to show.
        if (daily != null) {
            prefs.edit {
                putString(KEY_LAST_DAY, date.toString())
                if (hourly != null) putString(KEY_LAST_SLOT, slotKey)
            }
            streakRepository.finalizePastDays()
            celebrate(daily.copy(streak = streakRepository.current().current))
        } else if (hourly != null) {
            prefs.edit { putString(KEY_LAST_SLOT, slotKey) }
            celebrate(hourly)
        }

        val left = CelebrationDecider.stepsToNext(
            settings = settings,
            plan = plan,
            slot = slot,
            stepsInSlot = inSlot,
            todayTotal = total,
            alreadyCelebratedThisSlot = prefs.getString(KEY_LAST_SLOT, null) == slotKey,
            alreadyCelebratedToday = prefs.getString(KEY_LAST_DAY, null) == date.toString(),
        )
        if (hadNewSteps && left != null && left <= CelebrationDecider.CLOSE_STEPS) {
            Log.i(TAG, "$left steps from a goal; asking for steps again soon")
            reminderScheduler.scheduleStepCheck()
        }
    }

    suspend fun celebrate(celebration: Celebration) {
        Log.i(TAG, "Celebrating $celebration uiVisible=${isUiVisible()}")
        reminderNotifier.cancel()
        val strength = settingsRepository.current().vibration
        reminderNotifier.syncChannels(strength)
        val pattern = Haptics.celebrationPattern(strength)

        val inBackground = !isUiVisible()
        // A notification would not alert while the app is in the foreground on Wear OS anyway.
        val canOpen = !inBackground || canOpenFromBackground()
        // Wear OS ignores full-screen intents in always-on mode, so light the screen first.
        if (Attention.wakeScreenIfAsleep(context)) delay(Attention.WAKE_SETTLE_MS)
        if (canOpen && openScreen(celebration, pattern)) {
            if (inBackground) postRecord(celebration)
            return
        }
        postNotification(celebration, strength)
        Haptics.vibrate(context, pattern)
    }

    /** Do Not Disturb is left to filter the notification; an opened screen would ignore it. */
    private fun canOpenFromBackground(): Boolean =
        Settings.canDrawOverlays(context) && Attention.interruptionsAllowed(context)

    /** @return false if the screen didn't open. Android blocks a background start without saying so. */
    // NEW_TASK is required to start an activity from the application context; the activity is
    // noHistory and excluded from recents, which is what the WearRecents check is guarding.
    @SuppressLint("WearRecents")
    private suspend fun openScreen(celebration: Celebration, pattern: LongArray?): Boolean {
        screenOpened = false
        context.startActivity(CelebrationActivity.intent(context, celebration, pattern).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        delay(OPEN_WAIT_MS)
        return screenOpened
    }

    /** Called by the celebration screen as it opens. */
    fun onScreenOpened() {
        screenOpened = true
    }

    fun canUseFullScreenIntent(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            notificationManager.canUseFullScreenIntent()

    fun cancelNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun postNotification(celebration: Celebration, strength: VibrationStrength) {
        val builder = builder(celebration, ReminderNotifier.channelId(ReminderNotifier.CHANNEL_CELEBRATION, strength))
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setTimeoutAfter(NOTIFICATION_TIMEOUT_MS)
        if (canUseFullScreenIntent()) {
            builder.setFullScreenIntent(activityIntent(celebration), true)
        }
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    /** No pop-up and no buzz: just something to find if the screen went unseen. */
    private fun postRecord(celebration: Celebration) {
        val builder = builder(celebration, ReminderNotifier.CHANNEL_CELEBRATION_RECORD)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setTimeoutAfter(RECORD_TIMEOUT_MS)
        notificationManager.notify(RECORD_NOTIFICATION_ID, builder.build())
    }

    private fun activityIntent(celebration: Celebration): PendingIntent =
        PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            CelebrationActivity.intent(context, celebration),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun builder(celebration: Celebration, channelId: String): NotificationCompat.Builder {
        val title = when (celebration.kind) {
            CelebrationKind.SLOT_GOAL -> context.getString(R.string.celebration_slot_title, SlotWording.goalName(celebration.slotMinutes))
            CelebrationKind.INACTIVITY_GOAL -> context.getString(R.string.celebration_inactivity_title)
            CelebrationKind.DAILY_GOAL -> context.getString(R.string.celebration_daily_title)
        }
        val text = if (celebration.kind == CelebrationKind.DAILY_GOAL) {
            listOfNotNull(
                context.getString(R.string.celebration_steps_today, formatSteps(celebration.todayTotal)),
                Streaks.line(celebration.streak),
            ).joinToString(" · ")
        } else {
            context.getString(R.string.celebration_steps_in_slot, formatSteps(celebration.stepsInSlot), SlotWording.current(celebration.slotMinutes))
        }
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(activityIntent(celebration))
    }

    companion object {
        const val NOTIFICATION_ID = 1002
        private const val RECORD_NOTIFICATION_ID = 1003
        private const val REQUEST_CODE = 2
        private const val KEY_LAST_SLOT = "last_celebrated_slot"
        private const val KEY_LAST_DAY = "last_celebrated_day"
        private const val NOTIFICATION_TIMEOUT_MS = 10 * 60 * 1000L
        private const val RECORD_TIMEOUT_MS = 60 * 60 * 1000L
        private const val OPEN_WAIT_MS = 1_500L
    }
}
