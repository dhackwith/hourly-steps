package com.hourlysteps.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hourlysteps.HourlyStepsApp
import com.hourlysteps.domain.DayResult
import kotlinx.coroutines.launch

/**
 * Debug-only: replace the streak history, oldest day first, ending yesterday.
 *
 * adb shell am broadcast -a com.hourlysteps.debug.SET_STREAK_HISTORY --es days MET,MET,REST,MISSED,MET -p com.hourlysteps
 */
class DebugStreakReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val days = intent.getStringExtra("days")
            ?.split(",")
            ?.map { DayResult.valueOf(it.trim()) }
            ?: return
        val container = (context.applicationContext as HourlyStepsApp).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                container.streakRepository.replaceHistoryForTesting(days)
            } finally {
                pending.finish()
            }
        }
    }
}
