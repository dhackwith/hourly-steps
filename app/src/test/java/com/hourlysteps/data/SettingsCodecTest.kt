package com.hourlysteps.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.hourlysteps.data.settings.SettingsCodec
import com.hourlysteps.domain.DayAssignment
import com.hourlysteps.domain.DayPlan
import com.hourlysteps.domain.Schedule
import com.hourlysteps.domain.ScheduleMode
import com.hourlysteps.domain.Settings
import com.hourlysteps.domain.VibrationStrength
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsCodecTest {

    private fun roundTrip(settings: Settings): Settings {
        val prefs = mutablePreferencesOf()
        SettingsCodec.write(prefs, settings)
        return SettingsCodec.read(prefs)
    }

    @Test
    fun `empty preferences read as defaults`() {
        assertEquals(Settings(), SettingsCodec.read(mutablePreferencesOf()))
    }

    @Test
    fun `v1 settings become Plan A with every day on`() {
        val legacy = mutablePreferencesOf(
            intPreferencesKey("daily_goal") to 9000,
            intPreferencesKey("first_hour") to 7,
            intPreferencesKey("last_hour") to 18,
            booleanPreferencesKey("inactivity_enabled") to true,
        )
        val read = SettingsCodec.read(legacy)
        assertEquals(DayPlan(dailyGoal = 9000, startMinute = 7 * 60, endMinute = 19 * 60), read.schedule.planA)
        assertEquals(ScheduleMode.EVERY_DAY, read.schedule.mode)
        DayOfWeek.entries.forEach { assertEquals(DayAssignment.PLAN_A, read.schedule.assignmentFor(it)) }
        assertEquals(true, read.inactivityEnabled)
    }

    @Test
    fun `writing removes the v1 keys and keeps the values`() {
        val prefs = mutablePreferencesOf(
            intPreferencesKey("daily_goal") to 9000,
            intPreferencesKey("first_hour") to 7,
            intPreferencesKey("last_hour") to 18,
        )
        SettingsCodec.write(prefs, SettingsCodec.read(prefs))
        assertNull(prefs[intPreferencesKey("daily_goal")])
        assertNull(prefs[intPreferencesKey("first_hour")])
        assertNull(prefs[intPreferencesKey("last_hour")])
        assertEquals(9000, SettingsCodec.read(prefs).schedule.planA.dailyGoal)
    }

    @Test
    fun `a full schedule round trips`() {
        val settings = Settings(
            schedule = Schedule(
                mode = ScheduleMode.TWO_PLANS,
                planA = DayPlan(10000, 6 * 60, 24 * 60),
                planB = DayPlan(4000, 10 * 60, 16 * 60),
                assignments = mapOf(
                    DayOfWeek.MONDAY to DayAssignment.PLAN_B,
                    DayOfWeek.TUESDAY to DayAssignment.OFF,
                    DayOfWeek.WEDNESDAY to DayAssignment.OFF,
                    DayOfWeek.THURSDAY to DayAssignment.PLAN_A,
                    DayOfWeek.FRIDAY to DayAssignment.PLAN_A,
                    DayOfWeek.SATURDAY to DayAssignment.PLAN_A,
                    DayOfWeek.SUNDAY to DayAssignment.PLAN_B,
                ),
                slotMinutes = 30,
            ),
            pausedThrough = LocalDate.of(2026, 11, 8),
            inactivityEnabled = true,
            inactivityThreshold = 400,
            vibration = VibrationStrength.LONG,
            skipWhileCharging = false,
            celebrate = false,
        )
        assertEquals(settings, roundTrip(settings))
    }

    @Test
    fun `pausing until resumed round trips`() {
        val settings = Settings(pausedThrough = Settings.PAUSED_UNTIL_RESUMED)
        assertEquals(Settings.PAUSED_UNTIL_RESUMED, roundTrip(settings).pausedThrough)
    }

    @Test
    fun `resuming clears the stored pause`() {
        val prefs = mutablePreferencesOf()
        SettingsCodec.write(prefs, Settings(pausedThrough = LocalDate.of(2026, 11, 8)))
        SettingsCodec.write(prefs, Settings(pausedThrough = null))
        assertNull(SettingsCodec.read(prefs).pausedThrough)
    }

    @Test
    fun `an unsupported slot length falls back to an hour`() {
        val prefs = mutablePreferencesOf(intPreferencesKey("slot_minutes") to 45)
        assertEquals(60, SettingsCodec.read(prefs).schedule.slotMinutes)
    }
}
