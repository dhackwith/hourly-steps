package com.hourlysteps.celebration

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.hourlysteps.R
import com.hourlysteps.domain.Celebration
import com.hourlysteps.domain.CelebrationKind
import com.hourlysteps.domain.SlotWording
import com.hourlysteps.domain.Streaks
import com.hourlysteps.domain.VibrationStrength
import com.hourlysteps.reminder.Attention
import com.hourlysteps.reminder.Haptics
import com.hourlysteps.reminder.ReminderNotifier
import com.hourlysteps.ui.formatSteps
import kotlinx.coroutines.delay

/**
 * The Android side of a celebration.
 *
 * Reaching the screen from the background: the celebration activity is started directly when
 * the app's UI is visible, or when the app holds the "display over other apps" permission, which
 * Android requires of a background app opening a screen. Otherwise a high-priority notification
 * is posted with a full-screen intent, which launches the celebration when the watch screen is
 * off (like an alarm) and shows as a heads-up alert when it is on. If the full-screen intent
 * permission is not granted, the notification still alerts and tapping it opens the celebration.
 */
class AndroidCelebrationSurface(
    private val context: Context,
    private val reminderNotifier: ReminderNotifier,
    private val isUiVisible: () -> Boolean,
) : CelebrationSurface {

    @Volatile
    private var screenOpened = false

    private val notificationManager: NotificationManager
        get() = context.getSystemService(NotificationManager::class.java)

    override fun isUiVisible(): Boolean = isUiVisible.invoke()

    /** Do Not Disturb is left to filter the notification; an opened screen would ignore it. */
    override fun canOpenFromBackground(): Boolean =
        Settings.canDrawOverlays(context) && Attention.interruptionsAllowed(context)

    override fun prepare(strength: VibrationStrength) {
        reminderNotifier.cancel()
        reminderNotifier.syncChannels(strength)
    }

    // Wear OS ignores full-screen intents in always-on mode, so the screen has to be lit first.
    override suspend fun wakeScreen() {
        if (Attention.wakeScreenIfAsleep(context)) delay(Attention.WAKE_SETTLE_MS)
    }

    // NEW_TASK is required to start an activity from the application context; the activity is
    // noHistory and excluded from recents, which is what the WearRecents check is guarding.
    @SuppressLint("WearRecents")
    override suspend fun openScreen(celebration: Celebration, vibration: LongArray?): Boolean {
        screenOpened = false
        context.startActivity(CelebrationActivity.intent(context, celebration, vibration).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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

    override fun postAlert(celebration: Celebration, strength: VibrationStrength) {
        val builder = builder(celebration, ReminderNotifier.channelId(ReminderNotifier.CHANNEL_CELEBRATION, strength))
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setTimeoutAfter(NOTIFICATION_TIMEOUT_MS)
        if (canUseFullScreenIntent()) {
            builder.setFullScreenIntent(activityIntent(celebration), true)
        }
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    override fun postRecord(celebration: Celebration) {
        val builder = builder(celebration, ReminderNotifier.CHANNEL_CELEBRATION_RECORD)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setTimeoutAfter(RECORD_TIMEOUT_MS)
        notificationManager.notify(RECORD_NOTIFICATION_ID, builder.build())
    }

    override fun vibrate(pattern: LongArray?) = Haptics.vibrate(context, pattern)

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
        private const val NOTIFICATION_TIMEOUT_MS = 10 * 60 * 1000L
        private const val RECORD_TIMEOUT_MS = 60 * 60 * 1000L
        private const val OPEN_WAIT_MS = 1_500L
    }
}
