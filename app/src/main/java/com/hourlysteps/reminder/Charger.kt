package com.hourlysteps.reminder

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

object Charger {

    /**
     * True when any charger is connected. BatteryManager.isCharging() is not enough: a full
     * battery, or one held at a charge limit, reports "not charging" while on the dock.
     */
    fun isConnected(context: Context): Boolean {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
    }
}
