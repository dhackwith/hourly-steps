package com.hourlysteps.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.hourlysteps.HourlyStepsApp
import com.hourlysteps.domain.UserActivity

/**
 * Debug-only: pretend Health Services reported an activity, for testing the workout skip without
 * a real workout.
 *
 * adb shell am broadcast -a com.hourlysteps.debug.SET_ACTIVITY --es state EXERCISE -p com.hourlysteps
 */
class DebugActivityReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val state = intent.getStringExtra("state")
            ?.let { runCatching { UserActivity.valueOf(it) }.getOrNull() }
            ?: return
        val container = (context.applicationContext as HourlyStepsApp).container
        container.activityStore.record(state, container.clock.millis())
        Log.i("DebugActivity", "Activity set to $state")
    }
}
