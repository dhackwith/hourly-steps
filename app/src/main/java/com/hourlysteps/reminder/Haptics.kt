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

    /** "Da-da-daaa" twice: "goal reached". Long enough to notice while walking with an arm down. */
    fun celebrationPattern(strength: VibrationStrength): LongArray? = when (strength) {
        VibrationStrength.OFF -> null
        VibrationStrength.SHORT -> longArrayOf(0, 120, 80, 120, 80, 500, 300, 120, 80, 120, 80, 500)
        VibrationStrength.LONG -> longArrayOf(0, 150, 100, 150, 100, 800, 300, 150, 100, 150, 100, 800)
    }

    /** The strongest setting for each buzz in [pattern], whose entries alternate gap and buzz. */
    fun fullAmplitudes(pattern: LongArray): IntArray =
        IntArray(pattern.size) { if (it % 2 == 0) 0 else 255 }

    /**
     * Plays [pattern] unless Do Not Disturb is on. This only has an effect while the app is on
     * screen, where a notification doesn't alert; in the background Android drops the request,
     * and the notification channel's own pattern is what the wearer feels.
     */
    fun vibrate(context: Context, pattern: LongArray?, fullStrength: Boolean = false) {
        if (pattern == null || !Attention.interruptionsAllowed(context)) return
        val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        val effect = if (fullStrength && vibrator.hasAmplitudeControl()) {
            VibrationEffect.createWaveform(pattern, fullAmplitudes(pattern), -1)
        } else {
            VibrationEffect.createWaveform(pattern, -1)
        }
        vibrator.vibrate(effect)
    }
}
