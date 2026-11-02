package com.hourlysteps.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderScheduleTest {

    private val zone: ZoneId = ZoneId.of("America/New_York")
    private val defaults = Settings() // 8:50 AM through 7:50 PM, every day
    private val day: LocalDate = LocalDate.of(2026, 6, 10) // a Wednesday

    private fun at(date: LocalDate, hour: Int, minute: Int, z: ZoneId = zone) =
        ZonedDateTime.of(date, LocalTime.of(hour, minute), z)

    private fun window(first: Int, last: Int) = defaults.copy(
        schedule = defaults.schedule.copy(planA = DayPlan(startMinute = first * 60, endMinute = (last + 1) * 60)),
    )

    private fun off(vararg days: DayOfWeek): Settings {
        val assignments = DayOfWeek.entries.associateWith {
            if (it in days) DayAssignment.OFF else DayAssignment.PLAN_A
        }
        return defaults.copy(schedule = defaults.schedule.copy(assignments = assignments))
    }

    @Test
    fun `before the window goes to the first slot today`() {
        assertEquals(at(day, 8, 50), ReminderSchedule.nextCheckTime(at(day, 6, 0), defaults))
    }

    @Test
    fun `mid hour goes to this hour's slot`() {
        assertEquals(at(day, 10, 50), ReminderSchedule.nextCheckTime(at(day, 10, 20), defaults))
    }

    @Test
    fun `exactly at a check goes to the next one`() {
        assertEquals(at(day, 11, 50), ReminderSchedule.nextCheckTime(at(day, 10, 50), defaults))
    }

    @Test
    fun `just after a check goes to the next one`() {
        assertEquals(at(day, 11, 50), ReminderSchedule.nextCheckTime(at(day, 10, 50).plusSeconds(1), defaults))
    }

    @Test
    fun `after the last check rolls over to tomorrow's first`() {
        assertEquals(at(day.plusDays(1), 8, 50), ReminderSchedule.nextCheckTime(at(day, 19, 50), defaults))
        assertEquals(at(day.plusDays(1), 8, 50), ReminderSchedule.nextCheckTime(at(day, 23, 0), defaults))
    }

    @Test
    fun `30 minute slots check at 25 and 55`() {
        val s = defaults.copy(schedule = defaults.schedule.copy(slotMinutes = 30))
        assertEquals(at(day, 10, 25), ReminderSchedule.nextCheckTime(at(day, 10, 0), s))
        assertEquals(at(day, 10, 55), ReminderSchedule.nextCheckTime(at(day, 10, 25), s))
    }

    @Test
    fun `120 minute slots check 20 minutes before the even hour`() {
        val s = defaults.copy(schedule = defaults.schedule.copy(slotMinutes = 120))
        assertEquals(at(day, 9, 40), ReminderSchedule.nextCheckTime(at(day, 8, 0), s))
        assertEquals(at(day, 11, 40), ReminderSchedule.nextCheckTime(at(day, 9, 40), s))
    }

    @Test
    fun `days off are skipped`() {
        // Wednesday evening, Thursday and Friday off: next check is Saturday morning.
        val s = off(DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        assertEquals(at(day.plusDays(3), 8, 50), ReminderSchedule.nextCheckTime(at(day, 21, 0), s))
    }

    @Test
    fun `today being off moves to the next active day`() {
        assertEquals(at(day.plusDays(1), 8, 50), ReminderSchedule.nextCheckTime(at(day, 10, 0), off(DayOfWeek.WEDNESDAY)))
    }

    @Test
    fun `a single active day whose window has passed comes round next week`() {
        val onlyWednesday = off(*(DayOfWeek.entries - DayOfWeek.WEDNESDAY).toTypedArray())
        assertEquals(at(day.plusDays(7), 8, 50), ReminderSchedule.nextCheckTime(at(day, 21, 0), onlyWednesday))
    }

    @Test
    fun `every day off has no next check`() {
        assertNull(ReminderSchedule.nextCheckTime(at(day, 10, 0), off(*DayOfWeek.entries.toTypedArray())))
    }

    @Test
    fun `pausing today moves the next check to tomorrow`() {
        assertEquals(
            at(day.plusDays(1), 8, 50),
            ReminderSchedule.nextCheckTime(at(day, 10, 0), defaults.copy(pausedThrough = day)),
        )
    }

    @Test
    fun `a pause spanning several days resumes the day after`() {
        assertEquals(
            at(day.plusDays(7), 8, 50),
            ReminderSchedule.nextCheckTime(at(day, 10, 0), defaults.copy(pausedThrough = day.plusDays(6))),
        )
    }

    @Test
    fun `a pause ending on a day before days off skips those too`() {
        // Paused through Friday; Saturday and Sunday are off: next check is Monday.
        val s = off(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY).copy(pausedThrough = day.plusDays(2))
        assertEquals(at(day.plusDays(5), 8, 50), ReminderSchedule.nextCheckTime(at(day, 10, 0), s))
    }

    @Test
    fun `a pause that already ended is ignored`() {
        assertEquals(
            at(day, 10, 50),
            ReminderSchedule.nextCheckTime(at(day, 10, 0), defaults.copy(pausedThrough = day.minusDays(1))),
        )
    }

    @Test
    fun `pausing until resumed has no next check`() {
        assertNull(ReminderSchedule.nextCheckTime(at(day, 10, 0), defaults.copy(pausedThrough = Settings.PAUSED_UNTIL_RESUMED)))
    }

    @Test
    fun `isPaused covers the last paused day and nothing after it`() {
        val s = defaults.copy(pausedThrough = day)
        assertTrue(s.isPaused(day))
        assertTrue(s.isPaused(day.minusDays(1)))
        assertEquals(false, s.isPaused(day.plusDays(1)))
        assertEquals(false, defaults.isPaused(day))
    }

    @Test
    fun `plans on different days use their own windows`() {
        val assignments = DayOfWeek.entries.associateWith {
            if (it == DayOfWeek.THURSDAY) DayAssignment.PLAN_B else DayAssignment.PLAN_A
        }
        val s = defaults.copy(
            schedule = Schedule(
                mode = ScheduleMode.TWO_PLANS,
                planB = DayPlan(startMinute = 11 * 60, endMinute = 13 * 60),
                assignments = assignments,
            ),
        )
        assertEquals(at(day.plusDays(1), 11, 50), ReminderSchedule.nextCheckTime(at(day, 21, 0), s))
    }

    @Test
    fun `spring forward skips the missing hour`() {
        // US DST starts 2026-03-08 at 02:00 -> 03:00. Window 1..5 so the gap is inside it.
        val dst = LocalDate.of(2026, 3, 8)
        // 02:50 does not exist; the zone conversion shifts it to 03:50, which is the next real check.
        assertEquals(at(dst, 3, 50), ReminderSchedule.nextCheckTime(at(dst, 1, 50), window(1, 5)))
    }

    @Test
    fun `30 minute slots survive spring forward`() {
        val dst = LocalDate.of(2026, 3, 8)
        val s = window(1, 5).let { it.copy(schedule = it.schedule.copy(slotMinutes = 30)) }
        val next = ReminderSchedule.nextCheckTime(at(dst, 1, 55), s)!!
        assertTrue(next.isAfter(at(dst, 1, 55)))
        assertEquals(3, next.hour)
    }

    @Test
    fun `fall back still advances past the repeated hour`() {
        // US DST ends 2026-11-01 at 02:00 -> 01:00 (01:xx happens twice).
        val dst = LocalDate.of(2026, 11, 1)
        val firstOneFifty = at(dst, 1, 50) // the conversion picks the earlier offset (EDT)
        val next = ReminderSchedule.nextCheckTime(firstOneFifty, window(0, 5))
        // The repeated 01:50 (EST) is skipped; the two 1 o'clock hours merge into one slot.
        assertEquals(at(dst, 2, 50), next)
    }

    @Test
    fun `single slot window alternates days`() {
        val s = window(12, 12)
        assertEquals(at(day, 12, 50), ReminderSchedule.nextCheckTime(at(day, 12, 0), s))
        assertEquals(at(day.plusDays(1), 12, 50), ReminderSchedule.nextCheckTime(at(day, 12, 50), s))
    }

    private fun slotAt(hour: Int) = Slots.forDay(defaults.schedule.planA, 60, day).first { it.startMinute == hour * 60 }

    @Test
    fun `check running within its slot is not late`() {
        assertEquals(false, ReminderSchedule.isLate(slotAt(10), at(day, 10, 50)))
        assertEquals(false, ReminderSchedule.isLate(slotAt(10), at(day, 10, 59)))
        // Inexact fallback alarms fire up to ten minutes early.
        assertEquals(false, ReminderSchedule.isLate(slotAt(10), at(day, 10, 40)))
    }

    @Test
    fun `check running after its slot ended is late`() {
        assertEquals(true, ReminderSchedule.isLate(slotAt(10), at(day, 11, 0)))
        assertEquals(true, ReminderSchedule.isLate(slotAt(10), at(day, 11, 5)))
        assertEquals(true, ReminderSchedule.isLate(slotAt(19), at(day.plusDays(1), 8, 0)))
    }
}
