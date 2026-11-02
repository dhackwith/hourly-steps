package com.hourlysteps.reminder

import android.app.NotificationManager
import android.content.Context
import android.os.PowerManager
import android.util.Log

private const val TAG = "Attention"

/** Getting the wearer's attention from the background, without overriding Do Not Disturb. */
object Attention {

    /** How long to wait after waking before posting, so the alert appears on a live screen. */
    const val WAKE_SETTLE_MS = 400L
    private const val WAKE_HOLD_MS = 4_000L

    /** False while Do Not Disturb or Bedtime mode is filtering notifications. */
    fun interruptionsAllowed(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java).currentInterruptionFilter ==
            NotificationManager.INTERRUPTION_FILTER_ALL

    /**
     * Lights the screen if it is asleep. Wear OS only shows a notification's alert on an
     * interactive screen; in always-on mode the wearer gets nothing but a dot on the watch face.
     *
     * @return true if the screen was woken, in which case wait [WAKE_SETTLE_MS] before posting.
     */
    @Suppress("DEPRECATION") // No non-deprecated way for a background app to light the screen.
    fun wakeScreenIfAsleep(context: Context): Boolean {
        val power = context.getSystemService(PowerManager::class.java)
        if (power.isInteractive || !interruptionsAllowed(context)) return false
        return runCatching {
            power.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "hourlysteps:attention",
            ).acquire(WAKE_HOLD_MS)
            true
        }.getOrElse {
            Log.w(TAG, "Could not wake the screen", it)
            false
        }
    }
}
