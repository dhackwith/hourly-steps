package com.hourlysteps.reminder

import com.hourlysteps.domain.DayAssignment
import com.hourlysteps.domain.Schedule
import com.hourlysteps.domain.Settings
import com.hourlysteps.testing.Events
import com.hourlysteps.testing.FakeReminder
import com.hourlysteps.testing.FakeSettings
import com.hourlysteps.testing.FakeSteps
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderDismissalTest {

    private val zone: ZoneId = ZoneId.of("America/New_York")
    private val day: LocalDate = LocalDate.of(2026, 6, 10) // a Wednesday; 7000/day, 8:00 to 20:00

    private val events = Events()
    private val reminder = FakeReminder(events).apply { showing = true }
    private val settings = FakeSettings()
    // 1,168 before this hour, exactly on track, so this hour's goal is 584.
    private val steps = FakeSteps(events)

    private fun walked(inSlot: Int) {
        steps.inSlot = inSlot
        steps.total = 1168 + inSlot
    }

    private fun dismissal(hour: Int = 10, minute: Int = 52) = ReminderDismissal(
        reminder = reminder,
        settingsSource = settings,
        stepCounts = steps,
        clock = Clock.fixed(ZonedDateTime.of(day, LocalTime.of(hour, minute), zone).toInstant(), zone),
    )

    @Test
    fun `a reminder stays while still short`() = runTest {
        walked(583)
        dismissal().dismissIfSatisfied()
        assertFalse(events.has("cancel reminder"))
    }

    @Test
    fun `a reminder goes once the goal is reached`() = runTest {
        walked(584)
        dismissal().dismissIfSatisfied()
        assertTrue(events.has("cancel reminder"))
    }

    @Test
    fun `nothing happens when no reminder is showing`() = runTest {
        reminder.showing = false
        walked(584)
        dismissal().dismissIfSatisfied()
        assertFalse(events.has("cancel reminder"))
    }

    @Test
    fun `a reminder left over after the window goes`() = runTest {
        walked(0)
        dismissal(hour = 21).dismissIfSatisfied()
        assertTrue(events.has("cancel reminder"))
    }

    @Test
    fun `a reminder left over on a day off goes`() = runTest {
        settings.settings = Settings(schedule = Schedule(assignments = DayOfWeek.entries.associateWith { DayAssignment.OFF }))
        walked(0)
        dismissal().dismissIfSatisfied()
        assertTrue(events.has("cancel reminder"))
    }

    @Test
    fun `reaching the daily goal with inactivity reminders off clears a reminder`() = runTest {
        steps.inSlot = 100
        steps.total = 7000
        dismissal().dismissIfSatisfied()
        assertTrue(events.has("cancel reminder"))
    }

    @Test
    fun `inactivity reminders hold it to the smaller threshold`() = runTest {
        settings.settings = Settings(inactivityEnabled = true, inactivityThreshold = 250)
        steps.inSlot = 249
        steps.total = 7000
        dismissal().dismissIfSatisfied()
        assertFalse(events.has("cancel reminder"))
    }
}
