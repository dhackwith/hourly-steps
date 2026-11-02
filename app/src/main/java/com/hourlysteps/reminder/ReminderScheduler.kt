package com.hourlysteps.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.hourlysteps.data.settings.SettingsRepository
import com.hourlysteps.domain.ReminderSchedule
import com.hourlysteps.domain.StepCheckAlarm
import java.time.Clock
import java.time.ZonedDateTime

private const val TAG = "ReminderScheduler"

/** Why a reminder is coming back: the optional second nudge, or the wearer pressed Snooze. */
enum class FollowUpKind { SECOND_NUDGE, SNOOZE }

/** Arms the one-shot alarms for the next slot check and the next slot change. */
class ReminderScheduler(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
) : ReminderAlarms, StepCheckAlarm {
    private val alarmManager: AlarmManager
        get() = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /**
     * Computes and arms the next check strictly after [after], or cancels the alarm when no check
     * is coming (every day off, or paused until resumed). Safe to call repeatedly.
     */
    override suspend fun ensureScheduled(after: ZonedDateTime): ZonedDateTime? {
        val next = ReminderSchedule.nextCheckTime(after, settingsRepository.current())
        if (next == null) {
            cancel()
            Log.d(TAG, "No upcoming check; alarm cancelled")
        } else {
            schedule(next)
        }
        scheduleSlotChange()
        return next
    }

    suspend fun ensureScheduled(): ZonedDateTime? = ensureScheduled(ZonedDateTime.now(clock))

    fun cancel() {
        alarmManager.cancel(pendingIntent(0L))
        alarmManager.cancel(slotChangeIntent())
    }

    /** Arms the refresh of the tile and complications for when the current slot changes. */
    suspend fun scheduleSlotChange() {
        val next = ReminderSchedule.nextSlotChange(ZonedDateTime.now(clock), settingsRepository.current())
        if (next == null) {
            alarmManager.cancel(slotChangeIntent())
            return
        }
        val millis = next.toInstant().toEpochMilli()
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, slotChangeIntent())
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, slotChangeIntent())
        }
        Log.d(TAG, "Slot change scheduled for $next")
    }

    private fun slotChangeIntent(): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            SLOT_CHANGE_REQUEST_CODE,
            Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_SLOT_CHANGE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /**
     * Arms a reminder to come back later in the slot, replacing any already armed. Without the
     * exact-alarm permission it may run later than asked; a follow-up that lands after its slot
     * has ended is skipped as late.
     */
    override fun scheduleFollowUp(at: ZonedDateTime, kind: FollowUpKind) {
        val millis = at.toInstant().toEpochMilli()
        val pi = followUpIntent(millis, kind)
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        }
        Log.d(TAG, "$kind scheduled for $at")
    }

    /** Without the exact-alarm permission it may run a few minutes later, which still beats waiting. */
    override fun scheduleStepCheck() {
        val millis = clock.millis() + STEP_CHECK_DELAY_MS
        val pi = PendingIntent.getBroadcast(
            context,
            STEP_CHECK_REQUEST_CODE,
            Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_CHECK_STEPS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        }
    }

    fun cancelFollowUp() {
        alarmManager.cancel(followUpIntent(0L, FollowUpKind.SNOOZE))
    }

    private fun followUpIntent(scheduledAtMillis: Long, kind: FollowUpKind): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_FOLLOW_UP)
            .putExtra(ReminderReceiver.EXTRA_SCHEDULED_AT, scheduledAtMillis)
            .putExtra(ReminderReceiver.EXTRA_FOLLOW_UP_KIND, kind.name)
        return PendingIntent.getBroadcast(
            context,
            FOLLOW_UP_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun schedule(at: ZonedDateTime) {
        val millis = at.toInstant().toEpochMilli()
        val pi = pendingIntent(millis)
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            Log.d(TAG, "Exact check scheduled for $at")
        } else {
            // Android won't honour a window under 10 minutes. Ending it at the check time means
            // the alarm always fires before the slot ends, even with short slots.
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, millis - FALLBACK_WINDOW_MS, FALLBACK_WINDOW_MS, pi)
            Log.d(TAG, "Inexact check scheduled for the 10 minutes before $at")
        }
    }

    private fun pendingIntent(scheduledAtMillis: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_CHECK)
            .putExtra(ReminderReceiver.EXTRA_SCHEDULED_AT, scheduledAtMillis)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val REQUEST_CODE = 1
        const val STEP_CHECK_REQUEST_CODE = 4
        const val SLOT_CHANGE_REQUEST_CODE = 5
        const val STEP_CHECK_DELAY_MS = 60_000L
        const val FOLLOW_UP_REQUEST_CODE = 3
        const val FALLBACK_WINDOW_MS = 10 * 60 * 1000L
    }
}
