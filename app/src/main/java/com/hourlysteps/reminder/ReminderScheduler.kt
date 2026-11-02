package com.hourlysteps.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.hourlysteps.data.settings.SettingsRepository
import com.hourlysteps.domain.ReminderSchedule
import java.time.Clock
import java.time.ZonedDateTime

private const val TAG = "ReminderScheduler"

/** Arms the single one-shot alarm for the next slot check. */
class ReminderScheduler(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
) {
    private val alarmManager: AlarmManager
        get() = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /**
     * Computes and arms the next check strictly after [after], or cancels the alarm when no check
     * is coming (every day off, or paused until resumed). Safe to call repeatedly.
     */
    suspend fun ensureScheduled(after: ZonedDateTime = ZonedDateTime.now(clock)): ZonedDateTime? {
        val next = ReminderSchedule.nextCheckTime(after, settingsRepository.current())
        if (next == null) {
            cancel()
            Log.d(TAG, "No upcoming check; alarm cancelled")
        } else {
            schedule(next)
        }
        return next
    }

    fun cancel() {
        alarmManager.cancel(pendingIntent(0L))
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
        const val FALLBACK_WINDOW_MS = 10 * 60 * 1000L
    }
}
