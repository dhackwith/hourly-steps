package com.hourlysteps.surfaces

import com.hourlysteps.domain.CheckMode
import com.hourlysteps.domain.DayAssignment
import com.hourlysteps.domain.DayStatus
import com.hourlysteps.domain.DayStatusKind
import com.hourlysteps.domain.Schedule
import com.hourlysteps.domain.Settings
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SurfaceContentTest {

    private val zone: ZoneId = ZoneId.of("America/New_York")
    private val day: LocalDate = LocalDate.of(2026, 6, 10) // a Wednesday
    private val settings = Settings() // 7000/day, 8:00 to 20:00, slot goal 584

    private fun at(hour: Int, minute: Int = 20) = ZonedDateTime.of(day, LocalTime.of(hour, minute), zone)
    private val allOff = settings.copy(
        schedule = Schedule(assignments = DayOfWeek.entries.associateWith { DayAssignment.OFF }),
    )

    private fun status(hour: Int = 10, inSlot: Int = 292, total: Int = 3500, s: Settings = settings) =
        DayStatus.compute(at(hour), s, inSlot, total)

    @Test
    fun `active status tracks the slot and the day`() {
        val st = status()
        assertEquals(DayStatusKind.ACTIVE, st.kind)
        assertEquals(584, st.slotTarget)
        assertEquals(0.5f, st.slotProgress, 0.001f)
        assertEquals(0.5f, st.todayProgress, 0.001f)
    }

    @Test
    fun `progress is capped at full`() {
        val st = status(inSlot = 2000, total = 9000)
        assertEquals(1f, st.slotProgress, 0.001f)
        assertEquals(1f, st.todayProgress, 0.001f)
    }

    @Test
    fun `active rings show the slot number with both captions`() {
        val c = ringContent(status())
        assertEquals("292", c.value)
        assertEquals("of 584 this hour", c.valueCaption)
        assertTrue(c.captionIsSlot)
        assertEquals("Today 3,500 / 7,000", c.todayLine)
        assertNull(c.title)
        assertFalse(c.goalMet)
    }

    @Test
    fun `behind pace adds the catch up line`() {
        assertEquals("660/hr to catch up", ringContent(status(hour = 10, total = 400)).catchUpLine)
    }

    @Test
    fun `ahead of pace has no catch up line`() {
        assertNull(ringContent(status()).catchUpLine)
    }

    @Test
    fun `goal met with nothing left to do reads as finished`() {
        val c = ringContent(status(inSlot = 100, total = 7100))
        assertEquals("Goal met", c.title)
        assertEquals("7,100", c.value)
        assertEquals("today", c.valueCaption)
        assertTrue(c.goalMet)
        assertNull(c.detailLine)
    }

    @Test
    fun `goal met with inactivity reminders tracks the inactivity threshold`() {
        val st = status(inSlot = 100, total = 7100, s = settings.copy(inactivityEnabled = true))
        assertEquals(CheckMode.INACTIVITY, st.mode)
        val c = ringContent(st)
        assertEquals("Goal met · stay active", c.title)
        assertEquals("100", c.value)
        assertEquals("of 250 this hour", c.valueCaption)
        assertEquals(0.4f, c.slotProgress, 0.001f)
        assertTrue(c.goalMet)
    }

    @Test
    fun `outside the window has an empty inner ring and shows the day`() {
        val st = status(hour = 21, inSlot = 0, total = 3500)
        assertEquals(DayStatusKind.OUTSIDE_WINDOW, st.kind)
        val c = ringContent(st)
        assertEquals(0f, c.slotProgress, 0.001f)
        assertEquals(0.5f, c.todayProgress, 0.001f)
        assertEquals("3,500", c.value)
        assertEquals("of 7,000 today", c.valueCaption)
        assertFalse(c.captionIsSlot)
    }

    @Test
    fun `outside the window with the goal met is finished even with inactivity on`() {
        val st = status(hour = 21, inSlot = 0, total = 8000, s = settings.copy(inactivityEnabled = true))
        assertTrue(st.finished)
        assertEquals("Goal met", ringContent(st).title)
    }

    @Test
    fun `a day off has empty rings`() {
        val c = ringContent(status(total = 1234, s = allOff))
        assertEquals("Day off", c.title)
        assertEquals("1,234", c.value)
        assertEquals(0f, c.todayProgress, 0.001f)
        assertEquals(0f, c.slotProgress, 0.001f)
        assertFalse(c.goalMet)
    }

    @Test
    fun `paused has empty rings and says until when`() {
        val c = ringContent(status(s = settings.copy(pausedThrough = Settings.PAUSED_UNTIL_RESUMED)))
        assertEquals("Paused", c.title)
        assertEquals("until you resume", c.detailLine)
        assertEquals(0f, c.todayProgress, 0.001f)
        assertEquals(0f, c.slotProgress, 0.001f)
    }

    @Test
    fun `slot complication shows steps against the target`() {
        val c = slotComplication(status())
        assertEquals(292f, c.value, 0.001f)
        assertEquals(584f, c.max, 0.001f)
        assertEquals("292", c.shortText)
        assertEquals("of 584", c.shortTitle)
        assertEquals("292 of 584 this hour", c.longText)
    }

    @Test
    fun `slot complication value never exceeds its range`() {
        val c = slotComplication(status(inSlot = 900))
        assertEquals(584f, c.value, 0.001f)
        assertEquals("900", c.shortText)
    }

    @Test
    fun `slot complication has nothing to show outside the window`() {
        assertEquals("--", slotComplication(status(hour = 21)).shortText)
    }

    @Test
    fun `today complication shows the total against the goal`() {
        val c = todayComplication(status())
        assertEquals(3500f, c.value, 0.001f)
        assertEquals(7000f, c.max, 0.001f)
        assertEquals("3,500", c.shortText)
        assertEquals("today", c.shortTitle)
        assertEquals("3,500 of 7,000 today", c.longText)
    }

    @Test
    fun `today complication stays available outside the window`() {
        assertEquals("3,500", todayComplication(status(hour = 21)).shortText)
    }

    @Test
    fun `complications read paused and off`() {
        val paused = settings.copy(pausedThrough = day)
        assertEquals("Paused", slotComplication(status(s = paused)).shortText)
        assertEquals("Paused", todayComplication(status(s = paused)).shortText)
        assertEquals("Off", slotComplication(status(s = allOff)).shortText)
        assertEquals("Off", todayComplication(status(s = allOff)).shortText)
    }

    @Test
    fun `complication ranges are always valid`() {
        val paused = slotComplication(status(s = settings.copy(pausedThrough = day)))
        assertTrue(paused.max > 0f)
        assertTrue(paused.value <= paused.max)
    }


    private fun withInterval(slotMinutes: Int, endMinute: Int = 20 * 60): Settings = settings.copy(
        schedule = settings.schedule.copy(
            planA = settings.schedule.planA.copy(endMinute = endMinute),
            slotMinutes = slotMinutes,
        ),
    )

    @Test
    fun `the slot caption names the half hour`() {
        val c = ringContent(status(total = 400, s = withInterval(30)))
        assertEquals("of 292 this half hour", c.valueCaption)
        assertEquals("330/30 min to catch up", c.catchUpLine)
    }

    @Test
    fun `the slot caption names the two hours`() {
        val c = ringContent(status(total = 400, s = withInterval(120)))
        assertEquals("of 1,167 these two hours", c.valueCaption)
        assertEquals("1,320/2 hr to catch up", c.catchUpLine)
    }

    @Test
    fun `a short last slot says what it is`() {
        val c = ringContent(status(hour = 18, s = withInterval(120, endMinute = 19 * 60)))
        assertEquals("of 1,167 this hour", c.valueCaption)
    }

    @Test
    fun `the slot complication names the interval`() {
        assertEquals("292 of 292 this half hour", slotComplication(status(s = withInterval(30))).longText)
        assertEquals("292 of 1,167 these two hours", slotComplication(status(s = withInterval(120))).longText)
    }


    @Test
    fun `the pace check shows what's left and how the day stands`() {
        val c = paceCheckContent(status(hour = 12, inSlot = 272, total = 1720))!!
        assertEquals("312", c.left)
        assertEquals("left this hour", c.caption)
        assertEquals(272f / 584, c.slotProgress, 0.001f)
        assertEquals("Today 1,720 of 7,000", c.todayLine)
        assertEquals(1720f / 7000, c.todayProgress, 0.001f)
        assertEquals(2917f / 7000, c.paceMarker!!, 0.001f)
        assertEquals("1,197 behind pace", c.paceLine)
    }

    @Test
    fun `the pace check says when you're ahead`() {
        assertEquals("583 ahead of pace", paceCheckContent(status(hour = 12, inSlot = 100, total = 3500))!!.paceLine)
    }

    @Test
    fun `the pace check names the interval`() {
        assertEquals("left this half hour", paceCheckContent(status(inSlot = 10, s = withInterval(30)))!!.caption)
        assertEquals("left these two hours", paceCheckContent(status(inSlot = 10, s = withInterval(120)))!!.caption)
    }

    @Test
    fun `the pace check after the daily goal shows the inactivity share with no pace`() {
        val s = withInterval(30).copy(inactivityEnabled = true)
        val c = paceCheckContent(status(inSlot = 25, total = 8000, s = s))!!
        assertEquals("100", c.left)
        assertEquals("Daily goal met", c.paceLine)
        assertNull(c.paceMarker)
        assertTrue(c.goalMet)
    }

    @Test
    fun `the pace check has nothing to show once the reminder no longer applies`() {
        assertNull(paceCheckContent(status(inSlot = 584, total = 2000))) // slot goal reached
        assertNull(paceCheckContent(status(inSlot = 10, total = 7000))) // daily goal met, inactivity off
        assertNull(paceCheckContent(status(hour = 22))) // outside the window
        assertNull(paceCheckContent(status(s = allOff)))
        assertNull(paceCheckContent(status(s = settings.copy(pausedThrough = day))))
    }
}
