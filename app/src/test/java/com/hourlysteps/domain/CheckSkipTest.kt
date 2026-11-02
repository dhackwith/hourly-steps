package com.hourlysteps.domain

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CheckSkipTest {

    private val zone: ZoneId = ZoneId.of("America/New_York")
    private val day: LocalDate = LocalDate.of(2026, 6, 10) // a Wednesday
    private val settings = Settings()
    private val allOff = settings.copy(
        schedule = Schedule(assignments = DayOfWeek.entries.associateWith { DayAssignment.OFF }),
    )

    private fun at(hour: Int, minute: Int = 50) = ZonedDateTime.of(day, LocalTime.of(hour, minute), zone)

    private fun reason(
        s: Settings = settings,
        reference: ZonedDateTime = at(10),
        now: ZonedDateTime = reference,
        charger: Boolean = false,
        activity: UserActivity = UserActivity.PASSIVE,
        lateSensitive: Boolean = true,
        force: Boolean = false,
    ) = CheckSkip.reason(s, reference, now, charger, activity, lateSensitive, force)

    @Test
    fun `an ordinary check goes ahead`() {
        assertNull(reason())
    }

    @Test
    fun `unknown activity does not skip`() {
        assertNull(reason(activity = UserActivity.UNKNOWN))
    }

    @Test
    fun `sleeping skips`() {
        assertEquals(CheckSkip.ASLEEP, reason(activity = UserActivity.ASLEEP))
    }

    @Test
    fun `a workout skips`() {
        assertEquals(CheckSkip.WORKOUT, reason(activity = UserActivity.EXERCISE))
    }

    @Test
    fun `charging skips`() {
        assertEquals(CheckSkip.CHARGING, reason(charger = true))
    }

    @Test
    fun `each skip can be turned off`() {
        assertNull(reason(s = settings.copy(skipWhileAsleep = false), activity = UserActivity.ASLEEP))
        assertNull(reason(s = settings.copy(skipDuringWorkouts = false), activity = UserActivity.EXERCISE))
        assertNull(reason(s = settings.copy(skipWhileCharging = false), charger = true))
    }

    @Test
    fun `a late check is skipped`() {
        assertEquals(CheckSkip.LATE, reason(reference = at(10), now = at(11, 0)))
    }

    @Test
    fun `a manual check is never late and ignores circumstances`() {
        assertNull(
            reason(
                reference = at(10),
                now = at(11, 0),
                charger = true,
                activity = UserActivity.ASLEEP,
                lateSensitive = false,
                force = true,
            ),
        )
    }

    @Test
    fun `a manual check still respects pauses and days off`() {
        assertEquals(CheckSkip.PAUSED, reason(s = settings.copy(pausedThrough = day), force = true, lateSensitive = false))
        assertEquals(CheckSkip.DAY_OFF, reason(s = allOff, force = true, lateSensitive = false))
    }

    @Test
    fun `outside the window is skipped`() {
        assertEquals(CheckSkip.OUTSIDE_WINDOW, reason(reference = at(21)))
    }

    @Test
    fun `reasons follow a fixed order`() {
        val paused = settings.copy(pausedThrough = day)
        assertEquals(CheckSkip.PAUSED, reason(s = paused.copy(schedule = allOff.schedule)))
        assertEquals(CheckSkip.DAY_OFF, reason(s = allOff, activity = UserActivity.ASLEEP))
        assertEquals(
            CheckSkip.LATE,
            reason(reference = at(10), now = at(11, 0), activity = UserActivity.ASLEEP, charger = true),
        )
        assertEquals(CheckSkip.ASLEEP, reason(activity = UserActivity.ASLEEP, charger = true))
        assertEquals(CheckSkip.WORKOUT, reason(activity = UserActivity.EXERCISE, charger = true))
    }

    private val startMillis = at(10).toInstant().toEpochMilli()

    private fun after(hours: Long) = startMillis + Duration.ofHours(hours).toMillis()

    @Test
    fun `a recent workout counts`() {
        assertEquals(UserActivity.EXERCISE, ActivityState(UserActivity.EXERCISE, startMillis).currentAt(after(2)))
    }

    @Test
    fun `a workout older than twelve hours no longer counts`() {
        assertEquals(UserActivity.UNKNOWN, ActivityState(UserActivity.EXERCISE, startMillis).currentAt(after(13)))
    }

    @Test
    fun `sleep older than eighteen hours no longer counts`() {
        assertEquals(UserActivity.ASLEEP, ActivityState(UserActivity.ASLEEP, startMillis).currentAt(after(10)))
        assertEquals(UserActivity.UNKNOWN, ActivityState(UserActivity.ASLEEP, startMillis).currentAt(after(19)))
    }

    @Test
    fun `being awake never goes stale`() {
        assertEquals(UserActivity.PASSIVE, ActivityState(UserActivity.PASSIVE, startMillis).currentAt(after(100)))
    }

    private val slot10 = Slots.forDay(settings.schedule.planA, 60, day).first { it.startMinute == 600 }

    @Test
    fun `second nudge is halfway between the check and the slot end`() {
        assertEquals(at(10, 55), slot10.secondNudgeAt(zone))
    }

    @Test
    fun `second nudge follows the slot length`() {
        val half = Slots.forDay(settings.schedule.planA, 30, day).first { it.startMinute == 600 }
        assertEquals(ZonedDateTime.of(day, LocalTime.of(10, 27, 30), zone), half.secondNudgeAt(zone))
        val two = Slots.forDay(settings.schedule.planA, 120, day).first { it.startMinute == 600 }
        assertEquals(at(11, 50), two.secondNudgeAt(zone))
    }

    @Test
    fun `snooze comes back five minutes later`() {
        assertEquals(at(10, 55), ReminderSchedule.snoozeUntil(slot10, at(10, 50)))
    }

    @Test
    fun `snooze never crosses the slot end`() {
        assertEquals(at(10, 59), ReminderSchedule.snoozeUntil(slot10, at(10, 54)))
        assertNull(ReminderSchedule.snoozeUntil(slot10, at(10, 55)))
        assertNull(ReminderSchedule.snoozeUntil(slot10, at(10, 59)))
    }
}
