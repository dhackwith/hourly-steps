package com.hourlysteps.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.hourlysteps.R
import com.hourlysteps.data.settings.SettingsRepository
import com.hourlysteps.data.steps.StepsRepository
import com.hourlysteps.domain.CheckDecider
import com.hourlysteps.domain.CheckResult
import com.hourlysteps.domain.Settings
import com.hourlysteps.domain.VibrationStrength
import com.hourlysteps.ui.MainActivity
import java.time.Clock
import java.time.Duration
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

class ReminderNotifier(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val stepsRepository: StepsRepository,
    private val clock: Clock,
) {
    private val notificationManager: NotificationManager
        get() = context.getSystemService(NotificationManager::class.java)

    fun createChannels() {
        val goal = NotificationChannel(
            CHANNEL_GOAL,
            context.getString(R.string.channel_goal_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_goal_description)
            enableVibration(true)
        }
        val inactivity = NotificationChannel(
            CHANNEL_INACTIVITY,
            context.getString(R.string.channel_inactivity_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_inactivity_description)
            enableVibration(true)
        }
        notificationManager.createNotificationChannels(listOf(goal, inactivity))
    }

    fun show(result: CheckResult, settings: Settings, now: ZonedDateTime) {
        val (channel, text, subText) = when (result) {
            CheckResult.None -> return
            is CheckResult.GoalShortfall -> Triple(
                CHANNEL_GOAL,
                context.resources.getQuantityString(R.plurals.notif_more_steps, result.need, result.need),
                result.catchUpPerHour?.let { context.getString(R.string.notif_catch_up, it) },
            )
            is CheckResult.InactivityShortfall -> Triple(
                CHANNEL_INACTIVITY,
                context.resources.getQuantityString(R.plurals.notif_more_steps, result.need, result.need),
                null,
            )
        }

        val topOfNextHour = now.truncatedTo(ChronoUnit.HOURS).plusHours(1)
        val timeout = Duration.between(now, topOfNextHour).toMillis().coerceAtLeast(60_000L)

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, channel)
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
        notificationManager.notify(NOTIFICATION_ID, builder.build())
        vibrate(settings.vibration)
    }

    fun cancel() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    fun isShowing(): Boolean =
        notificationManager.activeNotifications.any { it.id == NOTIFICATION_ID }

    /** Called after new steps arrive: drop the reminder if this hour's threshold is now met. */
    suspend fun dismissIfSatisfied() {
        if (!isShowing()) return
        val now = ZonedDateTime.now(clock)
        val settings = settingsRepository.current()
        val result = CheckDecider.decide(
            settings = settings,
            stepsThisHour = stepsRepository.stepsThisHour(now),
            todayTotal = stepsRepository.todayTotal(now),
            hour = now.hour,
        )
        if (result == CheckResult.None) cancel()
    }

    private fun vibrate(strength: VibrationStrength) {
        val pattern = when (strength) {
            VibrationStrength.OFF -> return
            VibrationStrength.SHORT -> longArrayOf(0, 200, 100, 200)
            VibrationStrength.LONG -> longArrayOf(0, 400, 150, 400, 150, 400)
        }
        val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    companion object {
        const val CHANNEL_GOAL = "hourly_goal"
        const val CHANNEL_INACTIVITY = "inactivity"
        const val NOTIFICATION_ID = 1001
    }
}
