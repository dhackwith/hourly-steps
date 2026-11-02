package com.hourlysteps.reminder

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.hourlysteps.domain.VibrationStrength

/** Vibration patterns, kept distinct so the wrist can tell a nudge from a celebration. */
object Haptics {

    /** Two firm buzzes: "time to move". */
    fun reminderPattern(strength: VibrationStrength): LongArray? = when (strength) {
        VibrationStrength.OFF -> null
        VibrationStrength.SHORT -> longArrayOf(0, 200, 100, 200)
        VibrationStrength.LONG -> longArrayOf(0, 400, 150, 400, 150, 400)
    }

    /** Quick "da-da-daaa" flourish: "goal reached". */
    fun celebrationPattern(strength: VibrationStrength): LongArray? = when (strength) {
        VibrationStrength.OFF -> null
        VibrationStrength.SHORT -> longArrayOf(0, 60, 70, 60, 70, 260)
        VibrationStrength.LONG -> longArrayOf(0, 80, 80, 80, 80, 80, 80, 450)
    }

    fun vibrate(context: Context, pattern: LongArray?) {
        if (pattern == null) return
        val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }
}
