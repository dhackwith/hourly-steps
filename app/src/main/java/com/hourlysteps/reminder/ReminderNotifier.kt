package com.hourlysteps.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.hourlysteps.R
import com.hourlysteps.data.settings.SettingsRepository
import com.hourlysteps.data.steps.StepsRepository
import com.hourlysteps.domain.CheckDecider
import com.hourlysteps.domain.CheckResult
import com.hourlysteps.domain.Settings
import com.hourlysteps.domain.Slot
import com.hourlysteps.domain.Slots
import com.hourlysteps.domain.VibrationStrength
import com.hourlysteps.ui.MainActivity
import com.hourlysteps.ui.formatSteps
import java.time.Clock
import java.time.Duration
import java.time.ZonedDateTime
import kotlinx.coroutines.delay

class ReminderNotifier(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val stepsRepository: StepsRepository,
    private val clock: Clock,
) {
    private val notificationManager: NotificationManager
        get() = context.getSystemService(NotificationManager::class.java)

    /**
     * Makes the channels match the vibration setting and removes any others.
     *
     * Wear OS will not let a background app vibrate for a notification, only the channel's own
     * pattern plays, and it shows the pop-up alert only for a channel that vibrates. A channel's
     * pattern cannot change after creation, so the strength is part of the channel's id.
     */
    fun syncChannels(strength: VibrationStrength) {
        val wanted = listOf(
            channel(CHANNEL_GOAL, strength, Haptics.reminderPattern(strength), R.string.channel_goal_name, R.string.channel_goal_description),
            channel(CHANNEL_INACTIVITY, strength, Haptics.reminderPattern(strength), R.string.channel_inactivity_name, R.string.channel_inactivity_description),
            channel(CHANNEL_CELEBRATION, strength, Haptics.celebrationPattern(strength), R.string.channel_celebration_name, R.string.channel_celebration_description),
        )
        val wantedIds = wanted.map { it.id }.toSet()
        notificationManager.notificationChannels
            .filter { it.id !in wantedIds }
            .forEach { notificationManager.deleteNotificationChannel(it.id) }
        notificationManager.createNotificationChannels(wanted)
    }

    private fun channel(base: String, strength: VibrationStrength, pattern: LongArray?, name: Int, description: Int) =
        NotificationChannel(channelId(base, strength), context.getString(name), NotificationManager.IMPORTANCE_HIGH).apply {
            this.description = context.getString(description)
            enableVibration(true)
            vibrationPattern = pattern ?: IMPERCEPTIBLE_PATTERN
            setSound(null, null)
        }

    suspend fun show(result: CheckResult, settings: Settings, slot: Slot?, now: ZonedDateTime) {
        syncChannels(settings.vibration)
        val (baseChannel, text, subText) = when (result) {
            CheckResult.None -> return
            is CheckResult.GoalShortfall -> Triple(
                CHANNEL_GOAL,
                moreStepsText(result.need),
                result.catchUpPerSlot?.let { context.getString(R.string.notif_catch_up, formatSteps(it)) },
            )
            is CheckResult.InactivityShortfall -> Triple(
                CHANNEL_INACTIVITY,
                moreStepsText(result.need),
                null,
            )
        }

        val timeout = slot?.let { Duration.between(now, it.endAt(now.zone)).toMillis() }
            ?.coerceAtLeast(MIN_TIMEOUT_MS)
            ?: DEFAULT_TIMEOUT_MS

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, channelId(baseChannel, settings.vibration))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.notif_title))
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setTimeoutAfter(timeout)
            .setContentIntent(contentIntent)
        if (subText != null) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText("$text\n$subText"))
        }

        if (Attention.wakeScreenIfAsleep(context)) delay(Attention.WAKE_SETTLE_MS)
        notificationManager.notify(NOTIFICATION_ID, builder.build())
        Haptics.vibrate(context, Haptics.reminderPattern(settings.vibration))
    }

    private fun moreStepsText(need: Int): String =
        context.resources.getQuantityString(R.plurals.notif_more_steps, need, formatSteps(need))

    fun cancel() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    fun isShowing(): Boolean =
        notificationManager.activeNotifications.any { it.id == NOTIFICATION_ID }

    /** Called after new steps arrive: drop the reminder if this slot's threshold is now met. */
    suspend fun dismissIfSatisfied() {
        if (!isShowing()) return
        val now = ZonedDateTime.now(clock)
        val settings = settingsRepository.current()
        val date = now.toLocalDate()
        val plan = settings.schedule.planFor(date.dayOfWeek)
        val slot = Slots.at(settings.schedule, now)
        if (plan == null || slot == null) {
            cancel()
            return
        }
        val result = CheckDecider.decide(
            settings = settings,
            plan = plan,
            slot = slot,
            stepsInSlot = stepsRepository.stepsInSlot(slot),
            todayTotal = stepsRepository.dayTotal(date),
        )
        if (result == CheckResult.None) cancel()
    }

    companion object {
        const val CHANNEL_GOAL = "goal_reminders"
        const val CHANNEL_INACTIVITY = "inactivity_reminders"
        const val CHANNEL_CELEBRATION = "goal_celebrations"
        const val NOTIFICATION_ID = 1001

        fun channelId(base: String, strength: VibrationStrength): String = "${base}_${strength.name.lowercase()}"

        /** Too short to feel, but enough for Wear OS to treat the notification as alerting. */
        private val IMPERCEPTIBLE_PATTERN = longArrayOf(0, 1)
        private const val MIN_TIMEOUT_MS = 60_000L
        private const val DEFAULT_TIMEOUT_MS = 10 * 60_000L
    }
}
