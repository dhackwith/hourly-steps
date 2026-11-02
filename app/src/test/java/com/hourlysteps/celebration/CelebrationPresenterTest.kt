package com.hourlysteps.celebration

import com.hourlysteps.domain.Celebration
import com.hourlysteps.domain.CelebrationKind
import com.hourlysteps.domain.Settings
import com.hourlysteps.domain.VibrationStrength
import com.hourlysteps.reminder.Haptics
import com.hourlysteps.testing.Events
import com.hourlysteps.testing.FakeCelebrationSurface
import com.hourlysteps.testing.FakeSettings
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CelebrationPresenterTest {

    private val events = Events()
    private val settings = FakeSettings()
    private val surface = FakeCelebrationSurface(events)
    private val hourGoal = Celebration(CelebrationKind.SLOT_GOAL, 600, 584, 1768, 7000)

    private fun presenter() = CelebrationPresenter(surface, settings)


    @Test
    fun `from the background the screen opens and leaves a silent note`() = runTest {
        presenter().celebrate(hourGoal)
        assertEquals(listOf("prepare", "wake", "open screen", "post record"), events.all)
    }

    @Test
    fun `the open screen is handed the vibration to play`() = runTest {
        settings.settings = Settings(vibration = VibrationStrength.LONG)
        presenter().celebrate(hourGoal)
        assertArrayEquals(Haptics.celebrationPattern(VibrationStrength.LONG), surface.vibration)
    }

    @Test
    fun `with the app open the screen opens without a note`() = runTest {
        surface.uiVisible = true
        presenter().celebrate(hourGoal)
        assertEquals(listOf("prepare", "wake", "open screen"), events.all)
    }

    @Test
    fun `with the app open the background permission doesn't matter`() = runTest {
        surface.uiVisible = true
        surface.canOpenInBackground = false
        presenter().celebrate(hourGoal)
        assertTrue(events.has("open screen"))
    }

    @Test
    fun `without the permission a notification alerts instead`() = runTest {
        surface.canOpenInBackground = false
        presenter().celebrate(hourGoal)
        assertEquals(listOf("prepare", "wake", "post alert", "vibrate"), events.all)
    }

    @Test
    fun `a screen that fails to open falls back to a notification`() = runTest {
        surface.screenOpens = false
        presenter().celebrate(hourGoal)
        assertEquals(listOf("prepare", "wake", "open screen", "post alert", "vibrate"), events.all)
    }

    @Test
    fun `a failed screen leaves no silent note`() = runTest {
        surface.screenOpens = false
        presenter().celebrate(hourGoal)
        assertFalse(events.has("post record"))
    }

    @Test
    fun `vibration off is passed along as no pattern`() = runTest {
        settings.settings = Settings(vibration = VibrationStrength.OFF)
        surface.canOpenInBackground = false
        presenter().celebrate(hourGoal)
        assertEquals(null, surface.vibration)
    }
}
