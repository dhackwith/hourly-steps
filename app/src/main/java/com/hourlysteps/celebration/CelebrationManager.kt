package com.hourlysteps.celebration

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
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
import com.hourlysteps.domain.Slots
import com.hourlysteps.domain.Streaks
import com.hourlysteps.domain.UserActivity
import com.hourlysteps.domain.VibrationStrength
import com.hourlysteps.reminder.Attention
import com.hourlysteps.reminder.Haptics
import com.hourlysteps.reminder.ReminderNotifier
import com.hourlysteps.ui.formatSteps
import java.time.Clock
import java.time.ZonedDateTime
import kotlinx.coroutines.delay

private const val TAG = "CelebrationManager"

/**
 * Detects the moment this hour's goal is reached and celebrates it once.
 *
 * Reaching the screen from the background: when the app's UI is visible the celebration
 * activity is started directly. Otherwise a high-priority notification is posted with a
 * full-screen intent, which launches the celebration when the watch screen is off (like an
 * alarm) and shows as a heads-up alert when it is on. If the full-screen intent permission is
 * not granted, the notification still alerts and tapping it opens the celebration.
 */
class CelebrationManager(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val stepsRepository: StepsRepository,
    private val activityStore: ActivityStore,
    private val streakRepository: StreakRepository,
    private val reminderNotifier: ReminderNotifier,
    private val clock: Clock,
    private val isUiVisible: () -> Boolean,
) {
    private val prefs = context.getSharedPreferences("celebrations", Context.MODE_PRIVATE)

    private val notificationManager: NotificationManager
        get() = context.getSystemService(NotificationManager::class.java)

    /** Called after each batch of new step data. */
    suspend fun onStepsRecorded() {
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
    }

    // NEW_TASK is required to start an activity from the application context; the activity is
    // noHistory and excluded from recents, which is what the WearRecents check is guarding.
    @SuppressLint("WearRecents")
    suspend fun celebrate(celebration: Celebration) {
        Log.i(TAG, "Celebrating $celebration uiVisible=${isUiVisible()}")
        reminderNotifier.cancel()
        val strength = settingsRepository.current().vibration
        reminderNotifier.syncChannels(strength)
        val pattern = Haptics.celebrationPattern(strength)

        if (isUiVisible()) {
            // The app is on screen, so it may start an activity itself. A notification would not
            // alert while the app is in the foreground on Wear OS anyway.
            context.startActivity(CelebrationActivity.intent(context, celebration).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Haptics.vibrate(context, pattern)
            return
        }
        // Wear OS ignores full-screen intents in always-on mode, so light the screen first.
        if (Attention.wakeScreenIfAsleep(context)) delay(Attention.WAKE_SETTLE_MS)
        postNotification(celebration, strength)
        Haptics.vibrate(context, pattern)
    }

    fun canUseFullScreenIntent(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            notificationManager.canUseFullScreenIntent()

    fun cancelNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun postNotification(celebration: Celebration, strength: VibrationStrength) {
        val activityIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            CelebrationActivity.intent(context, celebration),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = context.getString(
            when (celebration.kind) {
                CelebrationKind.HOURLY_GOAL -> R.string.celebration_hourly_title
                CelebrationKind.INACTIVITY_GOAL -> R.string.celebration_inactivity_title
                CelebrationKind.DAILY_GOAL -> R.string.celebration_daily_title
            },
        )
        val text = if (celebration.kind == CelebrationKind.DAILY_GOAL) {
            listOfNotNull(
                context.getString(R.string.celebration_steps_today, formatSteps(celebration.todayTotal)),
                Streaks.line(celebration.streak),
            ).joinToString(" · ")
        } else {
            context.getString(R.string.celebration_steps_this_hour, formatSteps(celebration.stepsThisHour))
        }
        val builder = NotificationCompat.Builder(context, ReminderNotifier.channelId(ReminderNotifier.CHANNEL_CELEBRATION, strength))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setTimeoutAfter(NOTIFICATION_TIMEOUT_MS)
            .setContentIntent(activityIntent)
        if (canUseFullScreenIntent()) {
            builder.setFullScreenIntent(activityIntent, true)
        }
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    companion object {
        const val NOTIFICATION_ID = 1002
        private const val REQUEST_CODE = 2
        private const val KEY_LAST_SLOT = "last_celebrated_slot"
        private const val KEY_LAST_DAY = "last_celebrated_day"
        private const val NOTIFICATION_TIMEOUT_MS = 10 * 60 * 1000L
    }
}
