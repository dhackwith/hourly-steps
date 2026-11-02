package com.hourlysteps.celebration

import android.util.Log
import com.hourlysteps.domain.Celebration
import com.hourlysteps.domain.SettingsSource
import com.hourlysteps.reminder.Haptics

private const val TAG = "CelebrationPresenter"

/**
 * Decides how a celebration reaches the wearer: the screen if it can open, otherwise a notification.
 *
 * A screen opened from the background is gone in seconds and may go unseen, so it leaves a silent
 * notification behind.
 */
class CelebrationPresenter(
    private val surface: CelebrationSurface,
    private val settingsSource: SettingsSource,
) {
    suspend fun celebrate(celebration: Celebration) {
        val inBackground = !surface.isUiVisible()
        Log.i(TAG, "Celebrating $celebration uiVisible=${!inBackground}")
        val strength = settingsSource.current().vibration
        surface.prepare(strength)
        val pattern = Haptics.celebrationPattern(strength)

        // A notification would not alert while the app is in the foreground on Wear OS anyway.
        val canOpen = !inBackground || surface.canOpenFromBackground()
        surface.wakeScreen()
        if (canOpen && surface.openScreen(celebration, pattern)) {
            if (inBackground) surface.postRecord(celebration)
            return
        }
        surface.postAlert(celebration, strength)
        surface.vibrate(pattern)
    }
}
