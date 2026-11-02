package com.hourlysteps.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.hourlysteps.HourlyStepsApp
import java.time.Instant
import java.time.ZonedDateTime
import kotlinx.coroutines.launch

private const val TAG = "ReminderReceiver"

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CHECK) return
        val app = context.applicationContext as HourlyStepsApp
        val container = app.container
        val pending = goAsync()

        val wakeLock = context.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "hourlysteps:check")
        wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS)

        val scheduledAt = intent.getLongExtra(EXTRA_SCHEDULED_AT, 0L)
            .takeIf { it > 0 }
            ?.let { ZonedDateTime.ofInstant(Instant.ofEpochMilli(it), container.clock.zone) }

        container.appScope.launch {
            try {
                container.reminderCheck.run(scheduledAt)
            } catch (t: Throwable) {
                Log.e(TAG, "Check failed", t)
                runCatching { container.reminderScheduler.ensureScheduled() }
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_CHECK = "com.hourlysteps.action.CHECK"
        const val EXTRA_SCHEDULED_AT = "scheduled_at"
        private const val WAKE_LOCK_TIMEOUT_MS = 30_000L
    }
}
