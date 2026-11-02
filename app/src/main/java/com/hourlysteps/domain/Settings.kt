package com.hourlysteps.domain

import java.time.LocalDate

enum class VibrationStrength { OFF, SHORT, LONG }

/** User configuration. The default schedule is 8:50 AM through 7:50 PM every day: 12 slots. */
data class Settings(
    val schedule: Schedule = Schedule(),
    /** Last paused day, inclusive. Null when not paused; [PAUSED_UNTIL_RESUMED] for no end date. */
    val pausedThrough: LocalDate? = null,
    val inactivityEnabled: Boolean = false,
    val inactivityThreshold: Int = DEFAULT_INACTIVITY_THRESHOLD,
    val vibration: VibrationStrength = VibrationStrength.SHORT,
    val skipWhileCharging: Boolean = true,
    /** Vibrate and show a celebration when this slot's goal is reached. */
    val celebrate: Boolean = true,
) {
    fun isPaused(date: LocalDate): Boolean = pausedThrough != null && !date.isAfter(pausedThrough)

    companion object {
        const val DEFAULT_DAILY_GOAL = 7000
        const val DEFAULT_FIRST_HOUR = 8
        const val DEFAULT_LAST_HOUR = 19
        const val DEFAULT_INACTIVITY_THRESHOLD = 250
        const val DEFAULT_SLOT_MINUTES = 60

        const val MIN_DAILY_GOAL = 1000
        const val MAX_DAILY_GOAL = 30000
        const val DAILY_GOAL_STEP = 100

        const val MIN_INACTIVITY_THRESHOLD = 50
        const val MAX_INACTIVITY_THRESHOLD = 1000
        const val INACTIVITY_THRESHOLD_STEP = 50

        val SLOT_MINUTE_OPTIONS = listOf(30, 60, 120)
        val PAUSED_UNTIL_RESUMED: LocalDate = LocalDate.MAX
    }
}
