package com.hourlysteps.reminder

import com.hourlysteps.domain.CheckResult
import com.hourlysteps.domain.CheckSkip
import com.hourlysteps.domain.Settings
import com.hourlysteps.domain.UserActivity
import com.hourlysteps.testing.Events
import com.hourlysteps.testing.FakeActivity
import com.hourlysteps.testing.FakeAlarms
import com.hourlysteps.testing.FakeCheckLog
import com.hourlysteps.testing.FakeReminder
import com.hourlysteps.testing.FakeSettings
import com.hourlysteps.testing.FakeSteps
import com.hourlysteps.testing.FakeStreaks
import com.hourlysteps.testing.FakeSurfaces
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderCheckTest {

    private val zone: ZoneId = ZoneId.of("America/New_York")
    private val day: LocalDate = LocalDate.of(2026, 6, 10) // a Wednesday; 7000/day, 8:00 to 20:00

    private fun at(hour: Int, minute: Int = 0, second: Int = 0) = ZonedDateTime.of(day, LocalTime.of(hour, minute, second), zone)

    private val check = at(10, 50)

    private val events = Events()
    private val settings = FakeSettings()
    // 900 before this hour and 100 in it, so 484 short of the even share of 584.
    private val steps = FakeSteps(events, inSlot = 100, total = 1000)
    private val activity = FakeActivity()
    private val alarms = FakeAlarms(events)
    private val reminder = FakeReminder(events)
    private val log = FakeCheckLog(events)
    private var onCharger = false

    private fun checkAt(now: ZonedDateTime) = ReminderCheck(
        settingsSource = settings,
        stepCounts = steps,
        activitySource = activity,
        streakTracker = FakeStreaks(events),
        notifier = reminder,
        alarms = alarms,
        checkLogDao = log,
        surfaces = FakeSurfaces(events),
        isOnCharger = { onCharger },
        clock = Clock.fixed(now.toInstant(), zone),
    )

    private val shortfall = CheckResult.GoalShortfall(need = 484)

    @Test
    fun `short of the goal shows a reminder and logs it`() = runTest {
        assertEquals(shortfall, checkAt(check).run(check))
        assertEquals(listOf<CheckResult>(shortfall), reminder.shown)
        val row = log.rows.single()
        assertTrue(row.notified)
        assertEquals(484, row.need)
        assertEquals(100, row.stepsInSlot)
        assertEquals(1000, row.todayTotal)
        assertEquals(600, row.slotStartMinute)
        assertEquals("2026-06-10", row.date)
        assertEquals("GOAL", row.mode)
        assertNull(row.skippedReason)
        assertFalse(row.isFollowUp)
    }

    @Test
    fun `reaching the goal shows no reminder and still logs`() = runTest {
        steps.inSlot = 600
        steps.total = 1500
        assertEquals(CheckResult.None, checkAt(check).run(check))
        assertFalse(events.has("show reminder"))
        val row = log.rows.single()
        assertFalse(row.notified)
        assertEquals(0, row.need)
    }

    @Test
    fun `steps the watch was holding count before deciding`() = runTest {
        steps.heldBack = 484
        assertEquals(CheckResult.None, checkAt(check).run(check))
        assertEquals(584, log.rows.single().stepsInSlot)
    }

    @Test
    fun `a check does its work in order`() = runTest {
        checkAt(check).run(check)
        assertEquals(
            listOf(
                "flush",
                "show reminder",
                "log check",
                "arm next check",
                "refresh surfaces",
                "finalize days",
                "prune steps 14",
                "prune log 2026-05-27",
            ),
            events.all,
        )
    }

    @Test
    fun `the next check is armed from now`() = runTest {
        val now = at(10, 50, 3)
        checkAt(now).run(check)
        assertEquals(now, alarms.armedAfter)
    }

    @Test
    fun `an alarm firing a hair early can't double fire`() = runTest {
        checkAt(at(10, 49, 59)).run(check)
        assertEquals(check, alarms.armedAfter)
    }

    @Test
    fun `no second nudge unless asked for`() = runTest {
        checkAt(check).run(check)
        assertTrue(alarms.followUps.isEmpty())
    }

    @Test
    fun `a second nudge is armed halfway to the end of the slot`() = runTest {
        settings.settings = Settings(secondNudge = true)
        checkAt(check).run(check)
        assertEquals(listOf(at(10, 55) to FollowUpKind.SECOND_NUDGE), alarms.followUps)
    }

    @Test
    fun `no second nudge when the goal is met`() = runTest {
        settings.settings = Settings(secondNudge = true)
        steps.inSlot = 600
        steps.total = 1500
        checkAt(check).run(check)
        assertTrue(alarms.followUps.isEmpty())
    }

    @Test
    fun `no second nudge when its time has already passed`() = runTest {
        settings.settings = Settings(secondNudge = true)
        checkAt(at(10, 56)).run(check)
        assertTrue(alarms.followUps.isEmpty())
    }

    @Test
    fun `on the charger nothing shows and the reason is logged`() = runTest {
        onCharger = true
        assertEquals(CheckResult.None, checkAt(check).run(check))
        assertEquals(CheckSkip.CHARGING, log.rows.single().skippedReason)
        assertFalse(log.rows.single().notified)
    }

    @Test
    fun `being on the charger can be allowed in settings`() = runTest {
        settings.settings = Settings(skipWhileCharging = false)
        onCharger = true
        assertEquals(shortfall, checkAt(check).run(check))
    }

    @Test
    fun `a manual check ignores the charger`() = runTest {
        onCharger = true
        assertEquals(shortfall, checkAt(check).run(check, force = true))
    }

    @Test
    fun `asleep skips the reminder`() = runTest {
        activity.activity = UserActivity.ASLEEP
        assertEquals(CheckResult.None, checkAt(check).run(check))
        assertEquals(CheckSkip.ASLEEP, log.rows.single().skippedReason)
    }

    @Test
    fun `a workout skips the reminder`() = runTest {
        activity.activity = UserActivity.EXERCISE
        assertEquals(CheckResult.None, checkAt(check).run(check))
        assertEquals(CheckSkip.WORKOUT, log.rows.single().skippedReason)
    }

    @Test
    fun `a check that runs after its slot ended is skipped as late`() = runTest {
        assertEquals(CheckResult.None, checkAt(at(11, 1)).run(check))
        assertEquals(CheckSkip.LATE, log.rows.single().skippedReason)
    }

    @Test
    fun `a paused day shows nothing but still arms the next check`() = runTest {
        settings.settings = Settings(pausedThrough = day)
        assertEquals(CheckResult.None, checkAt(check).run(check))
        assertEquals(CheckSkip.PAUSED, log.rows.single().skippedReason)
        assertTrue(events.has("arm next check"))
    }

    @Test
    fun `a check outside the window logs against the minute of day`() = runTest {
        val late = at(21, 15)
        assertEquals(CheckResult.None, checkAt(late).run(null))
        val row = log.rows.single()
        assertEquals(CheckSkip.OUTSIDE_WINDOW, row.skippedReason)
        assertEquals(21 * 60 + 15, row.slotStartMinute)
    }

    @Test
    fun `a second nudge reminds again while still short`() = runTest {
        settings.settings = Settings(secondNudge = true)
        val nudge = at(10, 55)
        checkAt(nudge).runFollowUp(nudge, FollowUpKind.SECOND_NUDGE)
        assertTrue(events.has("show reminder"))
        assertTrue(log.rows.single().isFollowUp)
        assertTrue(events.has("refresh surfaces"))
    }

    @Test
    fun `a follow-up doesn't re-arm the check chain`() = runTest {
        settings.settings = Settings(secondNudge = true)
        val nudge = at(10, 55)
        checkAt(nudge).runFollowUp(nudge, FollowUpKind.SECOND_NUDGE)
        assertFalse(events.has("arm next check"))
    }

    @Test
    fun `a second nudge turned off after it was armed does nothing`() = runTest {
        val nudge = at(10, 55)
        checkAt(nudge).runFollowUp(nudge, FollowUpKind.SECOND_NUDGE)
        assertTrue(events.all.isEmpty())
    }

    @Test
    fun `a snooze comes back even with the second nudge off`() = runTest {
        val back = at(10, 55)
        checkAt(back).runFollowUp(back, FollowUpKind.SNOOZE)
        assertTrue(events.has("show reminder"))
    }

    @Test
    fun `a follow-up clears the reminder once the goal is met`() = runTest {
        settings.settings = Settings(secondNudge = true)
        steps.inSlot = 600
        steps.total = 1500
        val nudge = at(10, 55)
        checkAt(nudge).runFollowUp(nudge, FollowUpKind.SECOND_NUDGE)
        assertTrue(events.has("cancel reminder"))
        assertFalse(events.has("show reminder"))
    }

    @Test
    fun `a main check doesn't clear a reminder when the goal is met`() = runTest {
        steps.inSlot = 600
        steps.total = 1500
        checkAt(check).run(check)
        assertFalse(events.has("cancel reminder"))
    }

    @Test
    fun `snoozing clears the reminder and brings it back in 5 minutes`() = runTest {
        checkAt(at(10, 50)).snooze()
        assertTrue(events.has("cancel reminder"))
        assertEquals(listOf(at(10, 55) to FollowUpKind.SNOOZE), alarms.followUps)
    }

    @Test
    fun `snoozing too near the end of the slot only clears the reminder`() = runTest {
        checkAt(at(10, 56)).snooze()
        assertTrue(events.has("cancel reminder"))
        assertTrue(alarms.followUps.isEmpty())
    }

    @Test
    fun `snoozing outside the window only clears the reminder`() = runTest {
        checkAt(at(21)).snooze()
        assertTrue(events.has("cancel reminder"))
        assertTrue(alarms.followUps.isEmpty())
    }
}
