package com.hourlysteps.reminder

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.hourlysteps.HourlyStepsApp
import kotlinx.coroutines.launch

private const val TAG = "RescheduleReceiver"

/** Re-arms the alarm and passive monitoring after reboot, app update, or clock changes. */
class RescheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> Unit
            else -> return
        }
        val container = (context.applicationContext as HourlyStepsApp).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                container.reminderControls.ensureArmed()
                Log.i(TAG, "Re-armed after ${intent.action}")
            } catch (t: Throwable) {
                Log.e(TAG, "Re-arm failed", t)
            } finally {
                pending.finish()
            }
        }
    }
}
