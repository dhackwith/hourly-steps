package com.hourlysteps.data.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.hourlysteps.domain.DayAssignment
import com.hourlysteps.domain.DayPlan
import com.hourlysteps.domain.Schedule
import com.hourlysteps.domain.ScheduleMode
import com.hourlysteps.domain.Settings
import com.hourlysteps.domain.VibrationStrength
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Maps [Settings] to and from preference keys.
 *
 * Settings saved by v1 used daily_goal, first_hour and last_hour. Those are read as Plan A when
 * the new keys are absent, and removed on the next write.
 */
object SettingsCodec {

    private object Keys {
        val LEGACY_DAILY_GOAL = intPreferencesKey("daily_goal")
        val LEGACY_FIRST_HOUR = intPreferencesKey("first_hour")
        val LEGACY_LAST_HOUR = intPreferencesKey("last_hour")

        val MODE = stringPreferencesKey("schedule_mode")
        val PLAN_A_GOAL = intPreferencesKey("plan_a_goal")
        val PLAN_A_START = intPreferencesKey("plan_a_start_minute")
        val PLAN_A_END = intPreferencesKey("plan_a_end_minute")
        val PLAN_B_GOAL = intPreferencesKey("plan_b_goal")
        val PLAN_B_START = intPreferencesKey("plan_b_start_minute")
        val PLAN_B_END = intPreferencesKey("plan_b_end_minute")
        val WEEK = stringPreferencesKey("week_assignments")
        val SLOT_MINUTES = intPreferencesKey("slot_minutes")
        val PAUSED_THROUGH = stringPreferencesKey("paused_through")

        val INACTIVITY_ENABLED = booleanPreferencesKey("inactivity_enabled")
        val INACTIVITY_THRESHOLD = intPreferencesKey("inactivity_threshold")
        val VIBRATION = stringPreferencesKey("vibration")
        val SKIP_WHILE_CHARGING = booleanPreferencesKey("skip_while_charging")
        val CELEBRATE = booleanPreferencesKey("celebrate")
        val SKIP_WORKOUTS = booleanPreferencesKey("skip_during_workouts")
        val SKIP_ASLEEP = booleanPreferencesKey("skip_while_asleep")
        val SECOND_NUDGE = booleanPreferencesKey("second_nudge")
    }

    fun read(prefs: Preferences): Settings {
        val defaults = Settings()
        val defaultPlan = DayPlan()
        val planA = DayPlan(
            dailyGoal = prefs[Keys.PLAN_A_GOAL] ?: prefs[Keys.LEGACY_DAILY_GOAL] ?: defaultPlan.dailyGoal,
            startMinute = prefs[Keys.PLAN_A_START]
                ?: prefs[Keys.LEGACY_FIRST_HOUR]?.let { it * 60 }
                ?: defaultPlan.startMinute,
            endMinute = prefs[Keys.PLAN_A_END]
                ?: prefs[Keys.LEGACY_LAST_HOUR]?.let { (it + 1) * 60 }
                ?: defaultPlan.endMinute,
        )
        val planB = DayPlan(
            dailyGoal = prefs[Keys.PLAN_B_GOAL] ?: defaultPlan.dailyGoal,
            startMinute = prefs[Keys.PLAN_B_START] ?: defaultPlan.startMinute,
            endMinute = prefs[Keys.PLAN_B_END] ?: defaultPlan.endMinute,
        )
        return Settings(
            schedule = Schedule(
                mode = prefs[Keys.MODE]
                    ?.let { runCatching { ScheduleMode.valueOf(it) }.getOrNull() }
                    ?: ScheduleMode.EVERY_DAY,
                planA = planA,
                planB = planB,
                assignments = decodeWeek(prefs[Keys.WEEK]),
                slotMinutes = prefs[Keys.SLOT_MINUTES]?.takeIf { it in Settings.SLOT_MINUTE_OPTIONS }
                    ?: Settings.DEFAULT_SLOT_MINUTES,
            ),
            pausedThrough = prefs[Keys.PAUSED_THROUGH]?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            inactivityEnabled = prefs[Keys.INACTIVITY_ENABLED] ?: defaults.inactivityEnabled,
            inactivityThreshold = prefs[Keys.INACTIVITY_THRESHOLD] ?: defaults.inactivityThreshold,
            vibration = prefs[Keys.VIBRATION]
                ?.let { runCatching { VibrationStrength.valueOf(it) }.getOrNull() }
                ?: defaults.vibration,
            skipWhileCharging = prefs[Keys.SKIP_WHILE_CHARGING] ?: defaults.skipWhileCharging,
            celebrate = prefs[Keys.CELEBRATE] ?: defaults.celebrate,
            skipDuringWorkouts = prefs[Keys.SKIP_WORKOUTS] ?: defaults.skipDuringWorkouts,
            skipWhileAsleep = prefs[Keys.SKIP_ASLEEP] ?: defaults.skipWhileAsleep,
            secondNudge = prefs[Keys.SECOND_NUDGE] ?: defaults.secondNudge,
        )
    }

    fun write(prefs: MutablePreferences, s: Settings) {
        prefs.remove(Keys.LEGACY_DAILY_GOAL)
        prefs.remove(Keys.LEGACY_FIRST_HOUR)
        prefs.remove(Keys.LEGACY_LAST_HOUR)

        val schedule = s.schedule
        prefs[Keys.MODE] = schedule.mode.name
        prefs[Keys.PLAN_A_GOAL] = schedule.planA.dailyGoal
        prefs[Keys.PLAN_A_START] = schedule.planA.startMinute
        prefs[Keys.PLAN_A_END] = schedule.planA.endMinute
        prefs[Keys.PLAN_B_GOAL] = schedule.planB.dailyGoal
        prefs[Keys.PLAN_B_START] = schedule.planB.startMinute
        prefs[Keys.PLAN_B_END] = schedule.planB.endMinute
        prefs[Keys.WEEK] = encodeWeek(schedule.assignments)
        prefs[Keys.SLOT_MINUTES] = schedule.slotMinutes
        if (s.pausedThrough == null) {
            prefs.remove(Keys.PAUSED_THROUGH)
        } else {
            prefs[Keys.PAUSED_THROUGH] = s.pausedThrough.toString()
        }

        prefs[Keys.INACTIVITY_ENABLED] = s.inactivityEnabled
        prefs[Keys.INACTIVITY_THRESHOLD] = s.inactivityThreshold
        prefs[Keys.VIBRATION] = s.vibration.name
        prefs[Keys.SKIP_WHILE_CHARGING] = s.skipWhileCharging
        prefs[Keys.CELEBRATE] = s.celebrate
        prefs[Keys.SKIP_WORKOUTS] = s.skipDuringWorkouts
        prefs[Keys.SKIP_ASLEEP] = s.skipWhileAsleep
        prefs[Keys.SECOND_NUDGE] = s.secondNudge
    }

    /** Seven characters, Monday first: A, B or O. */
    private fun encodeWeek(assignments: Map<DayOfWeek, DayAssignment>): String =
        DayOfWeek.entries.joinToString("") { day ->
            when (assignments[day] ?: DayAssignment.PLAN_A) {
                DayAssignment.PLAN_A -> "A"
                DayAssignment.PLAN_B -> "B"
                DayAssignment.OFF -> "O"
            }
        }

    private fun decodeWeek(encoded: String?): Map<DayOfWeek, DayAssignment> =
        DayOfWeek.entries.associateWith { day ->
            when (encoded?.getOrNull(day.ordinal)) {
                'B' -> DayAssignment.PLAN_B
                'O' -> DayAssignment.OFF
                else -> DayAssignment.PLAN_A
            }
        }
}
