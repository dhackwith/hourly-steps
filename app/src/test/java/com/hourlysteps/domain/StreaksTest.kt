package com.hourlysteps.domain

import com.hourlysteps.domain.DayResult.MET
import com.hourlysteps.domain.DayResult.MISSED
import com.hourlysteps.domain.DayResult.REST
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreaksTest {

    private val today: LocalDate = LocalDate.of(2026, 6, 10) // a Wednesday
    private val settings = Settings() // 7000/day

    /** Records ending yesterday, oldest first. */
    private fun days(vararg results: DayResult): List<DayRecord> =
        results.mapIndexed { i, r -> DayRecord(today.minusDays((results.size - i).toLong()), r) }

    private fun current(vararg results: DayResult, todayResult: DayResult? = null) =
        Streaks.compute(days(*results), today, todayResult).current

    @Test
    fun `no history is no streak`() {
        assertEquals(0, current())
    }

    @Test
    fun `consecutive met days count`() {
        assertEquals(3, current(MET, MET, MET))
    }

    @Test
    fun `today counts once its goal is met`() {
        assertEquals(4, current(MET, MET, MET, todayResult = MET))
    }

    @Test
    fun `an open today does not break the streak`() {
        assertEquals(2, current(MET, MET, todayResult = null))
    }

    @Test
    fun `a missed day resets to zero`() {
        assertEquals(0, current(MET, MET, MISSED))
        assertEquals(2, current(MET, MISSED, MET, MET))
    }

    @Test
    fun `rest days are skipped over`() {
        assertEquals(3, current(MET, REST, MET, REST, MET))
    }

    @Test
    fun `a rest day today keeps the streak without adding to it`() {
        assertEquals(2, current(MET, MET, todayResult = REST))
    }

    @Test
    fun `a rest day does not rescue a missed day`() {
        assertEquals(0, current(MET, MISSED, REST))
    }

    @Test
    fun `only rest days is no streak`() {
        assertEquals(0, current(REST, REST))
    }

    @Test
    fun `the longest streak survives a reset`() {
        val info = Streaks.compute(days(MET, MET, MET, MET, MISSED, MET), today, null)
        assertEquals(1, info.current)
        assertEquals(4, info.longest)
    }

    @Test
    fun `the longest streak includes today`() {
        val info = Streaks.compute(days(MET, MET), today, MET)
        assertEquals(3, info.longest)
    }

    @Test
    fun `records are read by date, not in the order given`() {
        val shuffled = days(MET, MISSED, MET, MET).shuffled(java.util.Random(1))
        assertEquals(2, Streaks.compute(shuffled, today, null).current)
    }

    @Test
    fun `records dated today or later are ignored`() {
        // Possible if the clock was moved back.
        val future = listOf(DayRecord(today, MISSED), DayRecord(today.plusDays(1), MISSED))
        assertEquals(2, Streaks.compute(days(MET, MET) + future, today, null).current)
    }

    @Test
    fun `the week strip is the last seven days ending today`() {
        val week = Streaks.compute(days(MET, MISSED, REST), today, MET).week
        assertEquals(7, week.size)
        assertEquals(today.minusDays(6), week.first().date)
        assertEquals(today, week.last().date)
        assertTrue(week.last().isToday)
        assertFalse(week.first().isToday)
        assertEquals(listOf(null, null, null, MET, MISSED, REST, MET), week.map { it.result })
    }

    @Test
    fun `an open today has no result in the strip`() {
        assertNull(Streaks.compute(days(MET), today, null).week.last().result)
    }

    @Test
    fun `reaching the goal makes a finished day met`() {
        assertEquals(MET, Streaks.finalResult(settings, today, 7000))
        assertEquals(MISSED, Streaks.finalResult(settings, today, 6999))
    }

    @Test
    fun `a day off is a rest day even with plenty of steps`() {
        val off = settings.copy(schedule = Schedule(assignments = DayOfWeek.entries.associateWith { DayAssignment.OFF }))
        assertEquals(REST, Streaks.finalResult(off, today, 9000))
        assertEquals(REST, Streaks.finalResult(off, today, 0))
    }

    @Test
    fun `a paused day is a rest day even with plenty of steps`() {
        val paused = settings.copy(pausedThrough = today)
        assertEquals(REST, Streaks.finalResult(paused, today, 9000))
    }

    @Test
    fun `each plan judges by its own goal`() {
        val schedule = Schedule(
            mode = ScheduleMode.TWO_PLANS,
            planB = DayPlan(dailyGoal = 3000),
            assignments = DayOfWeek.entries.associateWith {
                if (it == DayOfWeek.WEDNESDAY) DayAssignment.PLAN_B else DayAssignment.PLAN_A
            },
        )
        assertEquals(MET, Streaks.finalResult(settings.copy(schedule = schedule), today, 3000))
    }

    @Test
    fun `today is not a miss until the day is over`() {
        assertNull(Streaks.todayResult(settings, today, 100))
        assertEquals(MET, Streaks.todayResult(settings, today, 7000))
    }

    @Test
    fun `recording starts the day after the last record`() {
        val last = today.minusDays(3)
        assertEquals(today.minusDays(2), Streaks.firstUnrecordedDay(last, null, today))
    }

    @Test
    fun `with no records, recording starts at the oldest steps`() {
        assertEquals(today.minusDays(5), Streaks.firstUnrecordedDay(null, today.minusDays(5), today))
    }

    @Test
    fun `nothing needs recording when up to date or with no history`() {
        assertNull(Streaks.firstUnrecordedDay(today.minusDays(1), null, today))
        assertNull(Streaks.firstUnrecordedDay(null, null, today))
        assertNull(Streaks.firstUnrecordedDay(null, today, today))
    }

    @Test
    fun `the streak line reads naturally`() {
        assertNull(Streaks.line(0))
        assertEquals("Streak started", Streaks.line(1))
        assertEquals("3 days in a row", Streaks.line(3))
    }

    private val plan = settings.schedule.planA

    private fun decideDaily(total: Int, already: Boolean = false, s: Settings = settings) =
        CelebrationDecider.decideDaily(s, plan, 100, total, already)

    @Test
    fun `reaching the daily goal celebrates`() {
        val c = decideDaily(7000)
        assertEquals(CelebrationKind.DAILY_GOAL, c?.kind)
        assertEquals(7000, c?.todayTotal)
    }

    @Test
    fun `below the daily goal does not celebrate`() {
        assertNull(decideDaily(6999))
    }

    @Test
    fun `the daily goal celebrates once a day`() {
        assertNull(decideDaily(8000, already = true))
    }

    @Test
    fun `the daily celebration can be turned off`() {
        assertNull(decideDaily(8000, s = settings.copy(celebrateDaily = false)))
    }
}
