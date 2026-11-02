package com.hourlysteps.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hourlysteps.HourlyStepsApp
import com.hourlysteps.domain.AppTheme
import kotlinx.coroutines.launch

/**
 * Debug-only: switch theme without going through Settings, for comparing them on screen.
 *
 * adb shell am broadcast -a com.hourlysteps.debug.SET_THEME --es theme OCEAN -p com.hourlysteps
 */
class DebugThemeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val theme = intent.getStringExtra("theme")?.let { name -> AppTheme.entries.find { it.name == name } } ?: return
        val container = (context.applicationContext as HourlyStepsApp).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                container.setTheme(theme)
            } finally {
                pending.finish()
            }
        }
    }
}
