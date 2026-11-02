package com.hourlysteps.reminder

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.hourlysteps.R
import com.hourlysteps.data.settings.SettingsRepository
import com.hourlysteps.data.steps.StepsRepository
import com.hourlysteps.domain.CheckDecider
import com.hourlysteps.domain.CheckResult
import com.hourlysteps.domain.DayPlan
import com.hourlysteps.domain.Pace
import com.hourlysteps.domain.ReminderSchedule
import com.hourlysteps.domain.Settings
import com.hourlysteps.domain.Slot
import com.hourlysteps.domain.SlotWording
import com.hourlysteps.domain.Slots
import com.hourlysteps.domain.VibrationStrength
import com.hourlysteps.ui.formatPaceLine
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
    private val isUiVisible: () -> Boolean,
) : ReminderPresenter {
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
        val alerting = listOf(
            channel(CHANNEL_GOAL, strength, Haptics.reminderPattern(strength), R.string.channel_goal_name, R.string.channel_goal_description),
            channel(CHANNEL_INACTIVITY, strength, Haptics.reminderPattern(strength), R.string.channel_inactivity_name, R.string.channel_inactivity_description),
            channel(CHANNEL_CELEBRATION, strength, Haptics.celebrationPattern(strength), R.string.channel_celebration_name, R.string.channel_celebration_description),
        )
        val record = NotificationChannel(CHANNEL_CELEBRATION_RECORD, context.getString(R.string.channel_record_name), NotificationManager.IMPORTANCE_LOW).apply {
            description = context.getString(R.string.channel_record_description)
            enableVibration(false)
            setSound(null, null)
        }
        val wanted = alerting + record
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

    /**
     * Shows the reminder. With the app open it goes straight to the full screen. Otherwise it is a
     * notification that opens the full screen, because Wear OS ignores full-screen intents in
     * always-on mode, so the words on the pop-up still have to stand on their own.
     */
    // NEW_TASK is required to start an activity from the application context; the activity is
    // noHistory and excluded from recents, which is what the WearRecents check is guarding.
    @SuppressLint("WearRecents")
    override suspend fun show(result: CheckResult, settings: Settings, plan: DayPlan?, slot: Slot?, now: ZonedDateTime, todayTotal: Int) {
        if (result == CheckResult.None) return
        syncChannels(settings.vibration)
        if (isUiVisible()) {
            context.startActivity(ReminderActivity.intent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Haptics.vibrate(context, Haptics.reminderPattern(settings.vibration))
            return
        }
        val slotMinutes = slot?.lengthMinutes ?: settings.schedule.slotMinutes
        val (baseChannel, text, subText) = when (result) {
            CheckResult.None -> return
            is CheckResult.GoalShortfall -> Triple(
                CHANNEL_GOAL,
                moreStepsText(result.need, slotMinutes),
                if (plan != null && slot != null) {
                    context.getString(R.string.notif_pace, formatPaceLine(Pace.gap(plan.dailyGoal, slot, Slots.minuteOfDay(now), todayTotal)))
                } else {
                    null
                },
            )
            is CheckResult.InactivityShortfall -> Triple(
                CHANNEL_INACTIVITY,
                moreStepsText(result.need, slotMinutes),
                null,
            )
        }

        val timeout = slot?.let { Duration.between(now, it.endAt(now.zone)).toMillis() }
            ?.coerceAtLeast(MIN_TIMEOUT_MS)
            ?: DEFAULT_TIMEOUT_MS

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            ReminderActivity.intent(context),
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
        // Wear OS gives a tap on the pop-up's text no action, so the way in has to be a button.
        builder.addAction(R.drawable.ic_launcher_foreground, context.getString(R.string.notif_action_see_pace), contentIntent)
        // A half hour's check leaves too little time for a snoozed reminder to come back.
        if (slot != null && ReminderSchedule.snoozeUntil(slot, now) != null) {
            builder.addAction(R.drawable.ic_action_snooze, context.getString(R.string.notif_action_snooze), actionIntent(ReminderReceiver.ACTION_SNOOZE, 10))
        }
        builder.addAction(R.drawable.ic_action_pause, context.getString(R.string.notif_action_pause_today), actionIntent(ReminderReceiver.ACTION_PAUSE_TODAY, 11))
        if (canUseFullScreenIntent()) {
            builder.setFullScreenIntent(contentIntent, true)
        }
        if (subText != null) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText("$text\n$subText"))
        }

        if (Attention.wakeScreenIfAsleep(context)) delay(Attention.WAKE_SETTLE_MS)
        notificationManager.notify(NOTIFICATION_ID, builder.build())
        Haptics.vibrate(context, Haptics.reminderPattern(settings.vibration))
    }

    private fun canUseFullScreenIntent(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            notificationManager.canUseFullScreenIntent()

    private fun actionIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, ReminderReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun moreStepsText(need: Int, slotMinutes: Int): String =
        context.resources.getQuantityString(R.plurals.notif_more_steps, need, formatSteps(need), SlotWording.current(slotMinutes))

    override fun cancel() {
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
        /** Versioned because a channel's vibration can't change once it exists. */
        const val CHANNEL_CELEBRATION = "goal_celebrations_v2"
        const val CHANNEL_CELEBRATION_RECORD = "goal_record"
        const val NOTIFICATION_ID = 1001

        fun channelId(base: String, strength: VibrationStrength): String = "${base}_${strength.name.lowercase()}"

        /** Too short to feel, but enough for Wear OS to treat the notification as alerting. */
        private val IMPERCEPTIBLE_PATTERN = longArrayOf(0, 1)
        private const val MIN_TIMEOUT_MS = 60_000L
        private const val DEFAULT_TIMEOUT_MS = 10 * 60_000L
    }
}
