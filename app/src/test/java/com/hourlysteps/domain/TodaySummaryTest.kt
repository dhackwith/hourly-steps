package com.hourlysteps.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TodaySummaryTest {

    private val zone: ZoneId = ZoneId.of("America/New_York")
    private val day: LocalDate = LocalDate.of(2026, 6, 10) // a Wednesday
    private val settings = Settings() // 7000/day, 8:00 to 20:00, slot goal 584

    private fun at(hour: Int, minute: Int = 0): ZonedDateTime = ZonedDateTime.of(day, LocalTime.of(hour, minute), zone)
    private fun millis(hour: Int, minute: Int = 0) = at(hour, minute).toInstant().toEpochMilli()
    private fun point(hour: Int, minute: Int, steps: Int) = StepPoint(millis(hour, minute), steps)
    private fun check(hour: Int, skipped: String? = null, notified: Boolean = false) =
        CheckRecord(millis(hour, 50), hour * 60, 0, notified, skipped)

    private fun summary(
        now: ZonedDateTime,
        points: List<StepPoint> = emptyList(),
        checks: List<CheckRecord> = emptyList(),
        s: Settings = settings,
    ) = TodaySummary.compute(now, s, points, checks)

    private fun TodaySummary.row(hour: Int) = rows.first { it.slot.startMinute == hour * 60 }

    @Test
    fun `there is one row per slot`() {
        assertEquals(12, summary(at(10)).slotsTotal)
    }

    @Test
    fun `slots later than now are upcoming`() {
        assertEquals(SlotOutcome.UPCOMING, summary(at(10)).row(11).outcome)
    }

    @Test
    fun `the current slot is in progress until it reaches its goal`() {
        assertEquals(SlotOutcome.IN_PROGRESS, summary(at(10, 20), listOf(point(10, 10, 100))).row(10).outcome)
        assertEquals(SlotOutcome.HIT, summary(at(10, 20), listOf(point(10, 10, 584))).row(10).outcome)
    }

    @Test
    fun `a finished slot that reached its goal is a hit`() {
        val s = summary(at(11), listOf(point(10, 30, 600)), listOf(check(10)))
        assertEquals(SlotOutcome.HIT, s.row(10).outcome)
        assertEquals(600, s.row(10).steps)
        assertEquals(1, s.slotsHit)
    }

    @Test
    fun `a finished slot short of its goal after a check is short`() {
        val s = summary(at(11), listOf(point(10, 30, 100)), listOf(check(10, notified = true)))
        assertEquals(SlotOutcome.SHORT, s.row(10).outcome)
    }

    @Test
    fun `a slot short of its goal with a skipped check shows why`() {
        val row = summary(at(11), listOf(point(10, 30, 100)), listOf(check(10, skipped = "charging"))).row(10)
        assertEquals(SlotOutcome.SKIPPED, row.outcome)
        assertEquals("charging", row.reason)
    }

    @Test
    fun `a finished slot with no check at all says so`() {
        val row = summary(at(11), listOf(point(10, 30, 100))).row(10)
        assertEquals(SlotOutcome.SKIPPED, row.outcome)
        assertEquals(TodaySummary.REASON_NOT_CHECKED, row.reason)
    }

    @Test
    fun `reaching the goal counts even if the check was skipped`() {
        val row = summary(at(11), listOf(point(10, 30, 600)), listOf(check(10, skipped = "charging"))).row(10)
        assertEquals(SlotOutcome.HIT, row.outcome)
    }

    @Test
    fun `a point ending on a boundary belongs to the earlier slot`() {
        val s = summary(at(12), listOf(point(11, 0, 50)))
        assertEquals(50, s.row(10).steps)
        assertEquals(0, s.row(11).steps)
    }

    @Test
    fun `slots after the daily goal was met are marked and don't count as short`() {
        val s = summary(at(12), listOf(point(9, 30, 7000), point(10, 30, 10)), listOf(check(10)))
        assertEquals(SlotOutcome.HIT, s.row(9).outcome)
        assertEquals(SlotOutcome.GOAL_ALREADY_MET, s.row(10).outcome)
        assertEquals(SlotOutcome.GOAL_ALREADY_MET, s.row(15).outcome)
    }

    @Test
    fun `a paused day marks unreached slots as paused`() {
        val s = summary(at(10, 20), listOf(point(9, 30, 100)), s = settings.copy(pausedThrough = day))
        assertEquals(DayStatusKind.PAUSED, s.kind)
        assertEquals(TodaySummary.REASON_PAUSED, s.row(9).reason)
        assertEquals(TodaySummary.REASON_PAUSED, s.row(15).reason)
    }

    @Test
    fun `a day off has no rows but still has a total`() {
        val off = settings.copy(
            schedule = Schedule(assignments = DayOfWeek.entries.associateWith { DayAssignment.OFF }),
        )
        val s = summary(at(10), listOf(point(9, 30, 123)), s = off)
        assertEquals(DayStatusKind.DAY_OFF, s.kind)
        assertEquals(0, s.slotsTotal)
        assertEquals(123, s.todayTotal)
        assertNull(s.dailyGoal)
    }

    @Test
    fun `the last check is the most recent one`() {
        val s = summary(at(12), checks = listOf(check(9), check(11, notified = true), check(10)))
        assertEquals(11 * 60, s.lastCheck?.slotStartMinute)
    }

    @Test
    fun `the latest check for a slot decides its row`() {
        val first = CheckRecord(millis(10, 50), 600, 0, false, "charging")
        val second = CheckRecord(millis(10, 51), 600, 0, true, null)
        assertEquals(SlotOutcome.SHORT, summary(at(11), listOf(point(10, 30, 10)), listOf(first, second)).row(10).outcome)
    }

    @Test
    fun `a follow-up doesn't change a slot's outcome but is the last check`() {
        val main = check(10, notified = true)
        val followUp = CheckRecord(millis(10, 55), 600, 0, false, CheckSkip.WORKOUT, isFollowUp = true)
        val s = summary(at(11), listOf(point(10, 30, 10)), listOf(main, followUp))
        assertEquals(SlotOutcome.SHORT, s.row(10).outcome)
        assertEquals(followUp, s.lastCheck)
    }

    @Test
    fun `a slot with only a follow-up counts as not checked`() {
        val followUp = CheckRecord(millis(10, 55), 600, 0, true, null, isFollowUp = true)
        assertEquals(TodaySummary.REASON_NOT_CHECKED, summary(at(11), listOf(point(10, 30, 10)), listOf(followUp)).row(10).reason)
    }

    @Test
    fun `30 minute slots split the steps`() {
        val half = settings.copy(schedule = Schedule(slotMinutes = 30))
        val s = summary(at(11), listOf(point(10, 10, 300), point(10, 40, 50)), s = half)
        assertEquals(24, s.slotsTotal)
        assertEquals(292, s.rows.first().goal)
        assertEquals(300, s.rows.first { it.slot.startMinute == 600 }.steps)
        assertEquals(50, s.rows.first { it.slot.startMinute == 630 }.steps)
    }
}
