package com.hourlysteps.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.hourlysteps.AppContainer
import com.hourlysteps.HourlyStepsApp
import java.time.Instant
import java.time.ZonedDateTime
import kotlinx.coroutines.launch

private const val TAG = "ReminderReceiver"

/** Alarms for slot checks, follow-ups, step checks and slot changes, and the buttons on a reminder notification. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action !in HANDLED_ACTIONS) return
        val container = (context.applicationContext as HourlyStepsApp).container
        val pending = goAsync()

        val wakeLock = context.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "hourlysteps:check")
        wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS)

        container.appScope.launch {
            try {
                handle(action, intent, container)
            } catch (t: Throwable) {
                Log.e(TAG, "Handling $action failed", t)
                if (action == ACTION_CHECK) runCatching { container.reminderScheduler.ensureScheduled() }
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
                pending.finish()
            }
        }
    }

    private suspend fun handle(action: String?, intent: Intent, container: AppContainer) {
        val scheduledAt = intent.getLongExtra(EXTRA_SCHEDULED_AT, 0L)
            .takeIf { it > 0 }
            ?.let { ZonedDateTime.ofInstant(Instant.ofEpochMilli(it), container.clock.zone) }

        when (action) {
            ACTION_CHECK -> container.reminderCheck.run(scheduledAt)
            ACTION_FOLLOW_UP -> {
                val kind = intent.getStringExtra(EXTRA_FOLLOW_UP_KIND)
                    ?.let { runCatching { FollowUpKind.valueOf(it) }.getOrNull() }
                if (scheduledAt != null && kind != null) container.reminderCheck.runFollowUp(scheduledAt, kind)
            }
            ACTION_SNOOZE -> container.reminderCheck.snooze()
            ACTION_PAUSE_TODAY -> container.pauseToday()
            // New steps come back through the passive listener, which decides whether to look again.
            ACTION_CHECK_STEPS -> container.stepsRepository.flush()
            ACTION_SLOT_CHANGE -> {
                Log.i(TAG, "Slot changed; refreshing the tile and complications")
                container.surfaceUpdater.updateNow()
                container.reminderScheduler.scheduleSlotChange()
            }
        }
    }

    companion object {
        const val ACTION_CHECK = "com.hourlysteps.action.CHECK"
        const val ACTION_FOLLOW_UP = "com.hourlysteps.action.FOLLOW_UP"
        const val ACTION_SNOOZE = "com.hourlysteps.action.SNOOZE"
        const val ACTION_PAUSE_TODAY = "com.hourlysteps.action.PAUSE_TODAY"
        const val ACTION_CHECK_STEPS = "com.hourlysteps.action.CHECK_STEPS"
        const val ACTION_SLOT_CHANGE = "com.hourlysteps.action.SLOT_CHANGE"
        const val EXTRA_SCHEDULED_AT = "scheduled_at"
        const val EXTRA_FOLLOW_UP_KIND = "follow_up_kind"
        private val HANDLED_ACTIONS = setOf(ACTION_CHECK, ACTION_FOLLOW_UP, ACTION_SNOOZE, ACTION_PAUSE_TODAY, ACTION_CHECK_STEPS, ACTION_SLOT_CHANGE)
        private const val WAKE_LOCK_TIMEOUT_MS = 30_000L
    }
}
