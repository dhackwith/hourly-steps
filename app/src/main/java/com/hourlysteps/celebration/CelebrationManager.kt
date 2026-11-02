package com.hourlysteps.celebration

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import com.hourlysteps.R
import com.hourlysteps.data.settings.SettingsRepository
import com.hourlysteps.data.steps.StepsRepository
import com.hourlysteps.domain.Celebration
import com.hourlysteps.domain.CelebrationDecider
import com.hourlysteps.domain.CelebrationKind
import com.hourlysteps.domain.HourBucketing
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
        val key = hourKey(now)
        val celebration = CelebrationDecider.decide(
            settings = settingsRepository.current(),
            stepsThisHour = stepsRepository.stepsThisHour(now),
            todayTotal = stepsRepository.todayTotal(now),
            hour = now.hour,
            alreadyCelebratedThisHour = prefs.getString(KEY_LAST_HOUR, null) == key,
        ) ?: return
        prefs.edit { putString(KEY_LAST_HOUR, key) }
        celebrate(celebration)
    }

    // NEW_TASK is required to start an activity from the application context; the activity is
    // noHistory and excluded from recents, which is what the WearRecents check is guarding.
    @SuppressLint("WearRecents")
    suspend fun celebrate(celebration: Celebration) {
        Log.i(TAG, "Celebrating $celebration uiVisible=${isUiVisible()}")
        reminderNotifier.cancel()
        val pattern = Haptics.celebrationPattern(settingsRepository.current().vibration)

        if (isUiVisible()) {
            // The app is on screen, so it may start an activity itself. A notification would not
            // alert while the app is in the foreground on Wear OS anyway.
            context.startActivity(CelebrationActivity.intent(context, celebration).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Haptics.vibrate(context, pattern)
            return
        }
        // Wear OS ignores full-screen intents while the watch is in always-on (ambient) mode, and
        // only shows the alert once the screen is interactive. Light the screen first, like the
        // built-in reminders do, unless Do Not Disturb / Bedtime is filtering notifications.
        if (!powerManager.isInteractive && notificationsUnfiltered()) {
            wakeScreen()
            delay(WAKE_SETTLE_MS)
        }
        postNotification(celebration)
        Haptics.vibrate(context, pattern)
    }

    private val powerManager: PowerManager
        get() = context.getSystemService(PowerManager::class.java)

    private fun notificationsUnfiltered(): Boolean =
        notificationManager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL

    @Suppress("DEPRECATION") // No non-deprecated way for a background app to light the screen.
    private fun wakeScreen() {
        runCatching {
            powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "hourlysteps:celebrate",
            ).acquire(WAKE_HOLD_MS)
        }.onFailure { Log.w(TAG, "Could not wake the screen", it) }
    }

    fun canUseFullScreenIntent(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            notificationManager.canUseFullScreenIntent()

    fun cancelNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun postNotification(celebration: Celebration) {
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
            },
        )
        val text = context.getString(R.string.celebration_steps_this_hour, formatSteps(celebration.stepsThisHour))
        val builder = NotificationCompat.Builder(context, ReminderNotifier.CHANNEL_CELEBRATION)
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

    private fun hourKey(now: ZonedDateTime): String {
        val key = HourBucketing.keyFor(now)
        return "${key.dateKey}#${key.hour}"
    }

    companion object {
        const val NOTIFICATION_ID = 1002
        private const val REQUEST_CODE = 2
        private const val KEY_LAST_HOUR = "last_celebrated_hour"
        private const val NOTIFICATION_TIMEOUT_MS = 10 * 60 * 1000L
        private const val WAKE_HOLD_MS = 4_000L
        private const val WAKE_SETTLE_MS = 400L
    }
}
