package com.hourlysteps.domain

import java.time.Duration
import java.time.ZonedDateTime

enum class UserActivity { UNKNOWN, PASSIVE, EXERCISE, ASLEEP }

/** The last activity Health Services reported, and when it began. */
data class ActivityState(val activity: UserActivity, val changedAtMillis: Long) {

    /**
     * What to believe now. The state only changes when Health Services says so, and a missed
     * "stopped" message must not silence reminders for good, so an implausibly long workout or
     * sleep counts as unknown.
     */
    fun currentAt(nowMillis: Long): UserActivity {
        val age = Duration.ofMillis(nowMillis - changedAtMillis)
        return when {
            activity == UserActivity.EXERCISE && age > MAX_WORKOUT -> UserActivity.UNKNOWN
            activity == UserActivity.ASLEEP && age > MAX_SLEEP -> UserActivity.UNKNOWN
            else -> activity
        }
    }

    private companion object {
        val MAX_WORKOUT: Duration = Duration.ofHours(12)
        val MAX_SLEEP: Duration = Duration.ofHours(18)
    }
}

/** Why a check should not remind, most fundamental reason first. */
object CheckSkip {
    const val PAUSED = "paused"
    const val DAY_OFF = "day off"
    const val OUTSIDE_WINDOW = "outside window"
    const val LATE = "late"
    const val ASLEEP = "asleep"
    const val WORKOUT = "workout"
    const val CHARGING = "charging"

    /**
     * @param lateSensitive false for a manual check, which runs whenever it is asked to.
     * @param force true for a manual check, which ignores the wearer's current circumstances.
     * @return the reason to skip, or null to go ahead.
     */
    fun reason(
        settings: Settings,
        reference: ZonedDateTime,
        now: ZonedDateTime,
        onCharger: Boolean,
        activity: UserActivity,
        lateSensitive: Boolean = true,
        force: Boolean = false,
    ): String? {
        val date = reference.toLocalDate()
        val slot = Slots.at(settings.schedule, reference)
        return when {
            settings.isPaused(date) -> PAUSED
            settings.schedule.planFor(date.dayOfWeek) == null -> DAY_OFF
            slot == null -> OUTSIDE_WINDOW
            lateSensitive && ReminderSchedule.isLate(slot, now) -> LATE
            force -> null
            settings.skipWhileAsleep && activity == UserActivity.ASLEEP -> ASLEEP
            settings.skipDuringWorkouts && activity == UserActivity.EXERCISE -> WORKOUT
            settings.skipWhileCharging && onCharger -> CHARGING
            else -> null
        }
    }
}
