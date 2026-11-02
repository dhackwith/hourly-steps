package com.hourlysteps.domain

enum class VibrationStrength { OFF, SHORT, LONG }

/**
 * User configuration. Hours are 0-23 and denote the slot whose check fires at HH:50.
 * The default window 8 -> 19 is 8:50 AM through 7:50 PM, i.e. 12 slots.
 */
data class Settings(
    val dailyGoal: Int = DEFAULT_DAILY_GOAL,
    val firstHour: Int = DEFAULT_FIRST_HOUR,
    val lastHour: Int = DEFAULT_LAST_HOUR,
    val inactivityEnabled: Boolean = false,
    val inactivityThreshold: Int = DEFAULT_INACTIVITY_THRESHOLD,
    val vibration: VibrationStrength = VibrationStrength.SHORT,
    val skipWhileCharging: Boolean = true,
    /** Vibrate and show a celebration when this hour's goal is reached. */
    val celebrate: Boolean = true,
) {
    companion object {
        const val DEFAULT_DAILY_GOAL = 7000
        const val DEFAULT_FIRST_HOUR = 8
        const val DEFAULT_LAST_HOUR = 19
        const val DEFAULT_INACTIVITY_THRESHOLD = 250
        const val CHECK_MINUTE = 50

        const val MIN_DAILY_GOAL = 1000
        const val MAX_DAILY_GOAL = 30000
        const val DAILY_GOAL_STEP = 100

        const val MIN_INACTIVITY_THRESHOLD = 50
        const val MAX_INACTIVITY_THRESHOLD = 1000
        const val INACTIVITY_THRESHOLD_STEP = 50
    }
}
