package com.hourlysteps.celebration

import com.hourlysteps.domain.Celebration
import com.hourlysteps.domain.CelebrationKind
import com.hourlysteps.domain.Settings
import com.hourlysteps.domain.UserActivity
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalWatcherTest {

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

    private fun manager(hour: Int = 10, minute: Int = 20) = GoalWatcher(
        presenter = CelebrationPresenter(surface, settings),
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

    // --- Stretch goals

    private fun stretchGoals(vararg goals: Int) {
        settings.settings = Settings(stretchGoals = goals.toList())
    }

    @Test
    fun `reaching a stretch goal celebrates it once`() = runTest {
        stretchGoals(3000, 1500)
        memory.remember(day = day.toString())
        steps.inSlot = 0
        steps.total = 10000
        manager().onStepsRecorded()
        manager().onStepsRecorded()
        assertEquals(1, surface.celebrations.size)
        val celebration = surface.celebrations.single()
        assertEquals(CelebrationKind.STRETCH_GOAL, celebration.kind)
        assertEquals(1, celebration.stretchLevel)
        assertEquals(2, celebration.stretchCount)
        assertEquals(10000, celebration.target)
    }

    @Test
    fun `each stretch goal gets its own celebration`() = runTest {
        stretchGoals(3000, 1500)
        memory.remember(day = day.toString())
        steps.total = 10000
        manager().onStepsRecorded()
        steps.total = 11500
        manager().onStepsRecorded()
        assertEquals(listOf(1, 2), surface.celebrations.map { it.stretchLevel })
    }

    @Test
    fun `yesterday's stretch goals don't count against today`() = runTest {
        stretchGoals(3000)
        memory.remember(day = day.toString(), stretch = "2026-06-09#1")
        steps.total = 10000
        manager().onStepsRecorded()
        assertEquals(1, surface.celebrations.size)
    }

    @Test
    fun `the daily goal comes first when a batch reaches both`() = runTest {
        stretchGoals(500)
        steps.total = 7600
        manager().onStepsRecorded()
        manager().onStepsRecorded()
        assertEquals(listOf(CelebrationKind.DAILY_GOAL, CelebrationKind.STRETCH_GOAL), surface.celebrations.map { it.kind })
    }

    @Test
    fun `stretch goals follow the daily celebration setting`() = runTest {
        settings.settings = Settings(stretchGoals = listOf(3000), celebrateDaily = false)
        steps.total = 10000
        manager().onStepsRecorded()
        assertTrue(surface.celebrations.isEmpty())
    }

    @Test
    fun `close to a stretch goal it asks for steps again soon`() = runTest {
        stretchGoals(3000)
        memory.remember(day = day.toString())
        steps.total = 9700
        manager().onStepsRecorded()
        assertTrue(events.has("step check"))
    }

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
}
