package com.hourlysteps.celebration

import com.hourlysteps.domain.Celebration
import com.hourlysteps.domain.VibrationStrength

/** Which slot and day have already been celebrated, so each is celebrated once. */
interface CelebrationMemory {
    fun lastSlot(): String?
    fun lastDay(): String?

    /** Saves whichever are given, together. */
    fun remember(slot: String? = null, day: String? = null)
}

/** How a celebration reaches the wearer. */
interface CelebrationSurface {
    fun isUiVisible(): Boolean

    /** Whether a screen may open from the background right now. */
    fun canOpenFromBackground(): Boolean

    /** Clears any reminder and makes sure the notification channels match [strength]. */
    fun prepare(strength: VibrationStrength)

    /** Lights the screen if it's asleep and waits for it to be ready. */
    suspend fun wakeScreen()

    /** @return false if the screen didn't open. Android blocks a background start without saying so. */
    suspend fun openScreen(celebration: Celebration, vibration: LongArray?): Boolean

    /** No pop-up and no buzz: just something to find if the screen went unseen. */
    fun postRecord(celebration: Celebration)

    /** The fallback when no screen can open: a notification that alerts. */
    fun postAlert(celebration: Celebration, strength: VibrationStrength)

    fun vibrate(pattern: LongArray?)
}
