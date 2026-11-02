package com.hourlysteps.celebration

import com.hourlysteps.domain.Celebration
import com.hourlysteps.domain.CelebrationKind
import com.hourlysteps.domain.Settings
import com.hourlysteps.domain.UserActivity
import com.hourlysteps.domain.VibrationStrength
import com.hourlysteps.reminder.Haptics
import com.hourlysteps.testing.Events
import com.hourlysteps.testing.FakeActivity
import com.hourlysteps.testing.FakeCelebrationSurface
import com.hourlysteps.testing.FakeMemory
import com.hourlysteps.testing.FakeSettings
import com.hourlysteps.testing.FakeStepCheckAlarm
import com.hourlysteps.testing.FakeSteps
import com.hourlysteps.testing.FakeStreaks
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CelebrationManagerTest {

    private val zone: ZoneId = ZoneId.of("America/New_York")
    private val day: LocalDate = LocalDate.of(2026, 6, 10) // a Wednesday; 7000/day, 8:00 to 20:00
    private val slotKey = "2026-06-10#600"

    private val events = Events()
    private val settings = FakeSettings()
    private val steps = FakeSteps(events)
    private val activity = FakeActivity()
    private val streaks = FakeStreaks(events)
    private val surface = FakeCelebrationSurface(events)
    private val memory = FakeMemory()

    private fun manager(hour: Int = 10, minute: Int = 20) = CelebrationManager(
        surface = surface,
        memory = memory,
        stepCheckAlarm = FakeStepCheckAlarm(events),
        settingsSource = settings,
        stepCounts = steps,
        activitySource = activity,
        streakTracker = streaks,
        clock = Clock.fixed(ZonedDateTime.of(day, LocalTime.of(hour, minute), zone).toInstant(), zone),
    )

    /** 1,168 before this hour, which is exactly on track, so this hour's goal is 584. */
    private fun walked(inSlot: Int) {
        steps.inSlot = inSlot
        steps.total = 1168 + inSlot
    }

    private val hourGoal = Celebration(CelebrationKind.SLOT_GOAL, 600, 584, 1768, 7000)

    // --- Noticing a goal

    @Test
    fun `reaching the hour's goal celebrates it`() = runTest {
        walked(600)
        manager().onStepsRecorded()
        assertEquals(listOf(hourGoal), surface.celebrations)
    }

    @Test
    fun `short of the hour's goal doesn't celebrate`() = runTest {
        walked(583)
        manager().onStepsRecorded()
        assertTrue(surface.celebrations.isEmpty())
    }

    @Test
    fun `an hour is only celebrated once`() = runTest {
        walked(600)
        manager().onStepsRecorded()
        walked(900)
        manager().onStepsRecorded()
        assertEquals(1, surface.celebrations.size)
    }

    @Test
    fun `the next hour can be celebrated too`() = runTest {
        walked(600)
        manager(hour = 10).onStepsRecorded()
        manager(hour = 11).onStepsRecorded()
        assertEquals(2, surface.celebrations.size)
    }

    @Test
    fun `what was celebrated is remembered`() = runTest {
        walked(600)
        manager().onStepsRecorded()
        assertEquals(slotKey, memory.lastSlot())
        assertEquals(null, memory.lastDay())
    }

    @Test
    fun `a paused day doesn't celebrate`() = runTest {
        settings.settings = Settings(pausedThrough = day)
        walked(600)
        manager().onStepsRecorded()
        assertTrue(surface.celebrations.isEmpty())
    }

    @Test
    fun `asleep doesn't celebrate`() = runTest {
        activity.activity = UserActivity.ASLEEP
        walked(600)
        manager().onStepsRecorded()
        assertTrue(surface.celebrations.isEmpty())
    }

    @Test
    fun `asleep celebrates when the skip is off`() = runTest {
        settings.settings = Settings(skipWhileAsleep = false)
        activity.activity = UserActivity.ASLEEP
        walked(600)
        manager().onStepsRecorded()
        assertEquals(1, surface.celebrations.size)
    }

    @Test
    fun `a workout still celebrates`() = runTest {
        activity.activity = UserActivity.EXERCISE
        walked(600)
        manager().onStepsRecorded()
        assertEquals(1, surface.celebrations.size)
    }

    @Test
    fun `the daily goal wins when both land in the same batch`() = runTest {
        streaks.current = 4
        steps.inSlot = 600
        steps.total = 7100
        manager().onStepsRecorded()
        val shown = surface.celebrations.single()
        assertEquals(CelebrationKind.DAILY_GOAL, shown.kind)
        assertEquals(4, shown.streak)
    }

    @Test
    fun `both goals are remembered when the daily one is shown`() = runTest {
        steps.inSlot = 600
        steps.total = 7100
        manager().onStepsRecorded()
        assertEquals(slotKey, memory.lastSlot())
        assertEquals("2026-06-10", memory.lastDay())
    }

    @Test
    fun `the streak is worked out after finishing past days`() = runTest {
        steps.inSlot = 600
        steps.total = 7100
        manager().onStepsRecorded()
        assertTrue(events.all.indexOf("finalize days") < events.all.indexOf("open screen"))
    }

    @Test
    fun `with hour celebrations off only the day is remembered`() = runTest {
        settings.settings = Settings(celebrate = false)
        steps.inSlot = 5
        steps.total = 7001
        manager().onStepsRecorded()
        assertEquals(CelebrationKind.DAILY_GOAL, surface.celebrations.single().kind)
        assertEquals(null, memory.lastSlot())
    }

    @Test
    fun `the daily goal celebrates outside the window`() = runTest {
        steps.total = 7100
        manager(hour = 21).onStepsRecorded()
        assertEquals(CelebrationKind.DAILY_GOAL, surface.celebrations.single().kind)
    }

    // --- Asking for steps near a goal

    @Test
    fun `within 400 steps of a goal it asks for steps again soon`() = runTest {
        walked(184)
        manager().onStepsRecorded()
        assertTrue(events.has("step check"))
    }

    @Test
    fun `further than 400 steps from a goal it waits for the next batch`() = runTest {
        walked(183)
        manager().onStepsRecorded()
        assertFalse(events.has("step check"))
    }

    @Test
    fun `a look that found no new steps ends the checking`() = runTest {
        walked(300)
        manager().onStepsRecorded(hadNewSteps = false)
        assertFalse(events.has("step check"))
    }

    @Test
    fun `no more checks this hour once its goal is celebrated and the day's is far off`() = runTest {
        walked(600)
        manager().onStepsRecorded()
        assertFalse(events.has("step check"))
    }

    @Test
    fun `reaching the hour's goal keeps checking if the day's is close too`() = runTest {
        steps.inSlot = 600
        steps.total = 6800
        manager().onStepsRecorded()
        assertEquals(CelebrationKind.SLOT_GOAL, surface.celebrations.single().kind)
        assertTrue(events.has("step check"))
    }

    @Test
    fun `a paused day doesn't ask for steps`() = runTest {
        settings.settings = Settings(pausedThrough = day)
        walked(300)
        manager().onStepsRecorded()
        assertFalse(events.has("step check"))
    }

    // --- Reaching the wearer

    @Test
    fun `from the background the screen opens and leaves a silent note`() = runTest {
        manager().celebrate(hourGoal)
        assertEquals(listOf("prepare", "wake", "open screen", "post record"), events.all)
    }

    @Test
    fun `the open screen is handed the vibration to play`() = runTest {
        settings.settings = Settings(vibration = VibrationStrength.LONG)
        manager().celebrate(hourGoal)
        assertArrayEquals(Haptics.celebrationPattern(VibrationStrength.LONG), surface.vibration)
    }

    @Test
    fun `with the app open the screen opens without a note`() = runTest {
        surface.uiVisible = true
        manager().celebrate(hourGoal)
        assertEquals(listOf("prepare", "wake", "open screen"), events.all)
    }

    @Test
    fun `with the app open the background permission doesn't matter`() = runTest {
        surface.uiVisible = true
        surface.canOpenInBackground = false
        manager().celebrate(hourGoal)
        assertTrue(events.has("open screen"))
    }

    @Test
    fun `without the permission a notification alerts instead`() = runTest {
        surface.canOpenInBackground = false
        manager().celebrate(hourGoal)
        assertEquals(listOf("prepare", "wake", "post alert", "vibrate"), events.all)
    }

    @Test
    fun `a screen that fails to open falls back to a notification`() = runTest {
        surface.screenOpens = false
        manager().celebrate(hourGoal)
        assertEquals(listOf("prepare", "wake", "open screen", "post alert", "vibrate"), events.all)
    }

    @Test
    fun `a failed screen leaves no silent note`() = runTest {
        surface.screenOpens = false
        manager().celebrate(hourGoal)
        assertFalse(events.has("post record"))
    }

    @Test
    fun `vibration off is passed along as no pattern`() = runTest {
        settings.settings = Settings(vibration = VibrationStrength.OFF)
        surface.canOpenInBackground = false
        manager().celebrate(hourGoal)
        assertEquals(null, surface.vibration)
    }
}
