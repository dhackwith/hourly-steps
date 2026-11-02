package com.hourlysteps.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderScheduleTest {

    private val zone: ZoneId = ZoneId.of("America/New_York")
    private val defaults = Settings() // 8..19

    private fun at(date: LocalDate, hour: Int, minute: Int, z: ZoneId = zone) =
        ZonedDateTime.of(date, LocalTime.of(hour, minute), z)

    private val day: LocalDate = LocalDate.of(2026, 6, 10)

    @Test
    fun `before the window goes to the first slot today`() {
        assertEquals(at(day, 8, 50), ReminderSchedule.nextCheckTime(at(day, 6, 0), defaults))
    }

    @Test
    fun `mid hour goes to this hour's slot`() {
        assertEquals(at(day, 10, 50), ReminderSchedule.nextCheckTime(at(day, 10, 20), defaults))
    }

    @Test
    fun `exactly at a slot goes to the next slot`() {
        assertEquals(at(day, 11, 50), ReminderSchedule.nextCheckTime(at(day, 10, 50), defaults))
    }

    @Test
    fun `just after a slot goes to the next slot`() {
        assertEquals(at(day, 11, 50), ReminderSchedule.nextCheckTime(at(day, 10, 50).plusSeconds(1), defaults))
    }

    @Test
    fun `after the last slot rolls over to tomorrow's first slot`() {
        assertEquals(at(day.plusDays(1), 8, 50), ReminderSchedule.nextCheckTime(at(day, 19, 50), defaults))
        assertEquals(at(day.plusDays(1), 8, 50), ReminderSchedule.nextCheckTime(at(day, 23, 0), defaults))
    }

    @Test
    fun `spring forward skips the missing hour`() {
        // US DST starts 2026-03-08 at 02:00 -> 03:00. Window 1..5 so the gap is inside it.
        val s = defaults.copy(firstHour = 1, lastHour = 5)
        val dst = LocalDate.of(2026, 3, 8)
        val next = ReminderSchedule.nextCheckTime(at(dst, 1, 50), s)
        // 02:50 does not exist; ZonedDateTime.of shifts it to 03:50, which is the next real slot.
        assertEquals(at(dst, 3, 50), next)
    }

    @Test
    fun `fall back still advances past the repeated hour`() {
        // US DST ends 2026-11-01 at 02:00 -> 01:00 (01:xx happens twice).
        val s = defaults.copy(firstHour = 0, lastHour = 5)
        val dst = LocalDate.of(2026, 11, 1)
        val firstOneFifty = at(dst, 1, 50) // ZonedDateTime.of picks the earlier offset (EDT)
        val next = ReminderSchedule.nextCheckTime(firstOneFifty, s)
        // The repeated 01:50 (EST) is skipped; the two 1 o'clock hours merge into one slot.
        assert(next.isAfter(firstOneFifty))
        assertEquals(at(dst, 2, 50), next)
    }

    @Test
    fun `single slot window alternates days`() {
        val s = defaults.copy(firstHour = 12, lastHour = 12)
        assertEquals(at(day, 12, 50), ReminderSchedule.nextCheckTime(at(day, 12, 0), s))
        assertEquals(at(day.plusDays(1), 12, 50), ReminderSchedule.nextCheckTime(at(day, 12, 50), s))
    }
}
