package com.hourlysteps.reminder

import com.hourlysteps.VisibleScreens
import com.hourlysteps.domain.AppTheme
import com.hourlysteps.domain.Settings
import com.hourlysteps.testing.Events
import com.hourlysteps.testing.FakeArming
import com.hourlysteps.testing.FakeReminder
import com.hourlysteps.testing.FakeSettings
import com.hourlysteps.testing.FakeStepMonitoring
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

class ReminderControlsTest {

    private val zone: ZoneId = ZoneId.of("America/New_York")
    private val day: LocalDate = LocalDate.of(2026, 6, 10)

    private val events = Events()
    private val settings = FakeSettings()
    private val monitoring = FakeStepMonitoring(events)
    private var permitted = true

    private val controls = ReminderControls(
        settingsEditor = settings,
        stepMonitoring = monitoring,
        notifier = FakeReminder(events),
        arming = FakeArming(events),
        surfaces = FakeSurfaces(events),
        hasActivityRecognition = { permitted },
        clock = Clock.fixed(ZonedDateTime.of(day, LocalTime.of(10, 20), zone).toInstant(), zone),
    )

    @Test
    fun `arming starts step monitoring and the next check`() = runTest {
        controls.ensureArmed()
        assertEquals(listOf("refresh surfaces", "start monitoring", "arm next check"), events.all)
    }

    @Test
    fun `without the steps permission reminders stay off`() = runTest {
        permitted = false
        controls.ensureArmed()
        assertEquals(listOf("refresh surfaces", "cancel alarms"), events.all)
    }

    @Test
    fun `a failure to start monitoring doesn't stop the next check`() = runTest {
        monitoring.fails = true
        controls.ensureArmed()
        assertTrue(events.has("arm next check"))
    }

    @Test
    fun `pausing saves the date, clears the reminder and re-arms`() = runTest {
        controls.setPausedThrough(day)
        assertEquals(day, settings.settings.pausedThrough)
        assertEquals(
            listOf("cancel reminder", "cancel follow-up", "refresh surfaces", "start monitoring", "arm next check"),
            events.all,
        )
    }

    @Test
    fun `resuming re-arms without clearing anything`() = runTest {
        settings.settings = Settings(pausedThrough = day)
        controls.setPausedThrough(null)
        assertNull(settings.settings.pausedThrough)
        assertFalse(events.has("cancel reminder"))
        assertFalse(events.has("cancel follow-up"))
        assertTrue(events.has("arm next check"))
    }

    @Test
    fun `pause today pauses through today`() = runTest {
        controls.pauseToday()
        assertEquals(day, settings.settings.pausedThrough)
    }

    @Test
    fun `changing the interval saves it and clears what the old one left`() = runTest {
        controls.setSlotMinutes(30)
        assertEquals(30, settings.settings.schedule.slotMinutes)
        assertEquals(
            listOf("cancel reminder", "cancel follow-up", "refresh surfaces", "start monitoring", "arm next check"),
            events.all,
        )
    }

    @Test
    fun `changing the theme saves it and refreshes the tile only`() = runTest {
        controls.setTheme(AppTheme.OCEAN)
        assertEquals(AppTheme.OCEAN, settings.settings.theme)
        assertEquals(listOf("refresh surfaces"), events.all)
    }

    @Test
    fun `screens are visible until the last one stops`() {
        val screens = VisibleScreens()
        assertFalse(screens.anyVisible)
        screens.onScreenStarted()
        screens.onScreenStarted()
        screens.onScreenStopped()
        assertTrue(screens.anyVisible)
        screens.onScreenStopped()
        assertFalse(screens.anyVisible)
    }
}
