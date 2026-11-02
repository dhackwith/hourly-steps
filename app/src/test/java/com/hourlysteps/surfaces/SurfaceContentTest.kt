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
    private val settings = Settings() // 7000/day, 8:00 to 20:00

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
        // 3,208 before this hour, 3,792 to go across 10 hours.
        assertEquals(380, st.slotTarget)
        assertEquals(292f / 380, st.slotProgress, 0.001f)
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
        assertEquals("of 380 this hour", c.valueCaption)
        assertTrue(c.captionIsSlot)
        assertEquals("Today 3,500 / 7,000", c.todayLine)
        assertNull(c.title)
        assertFalse(c.goalMet)
    }

    @Test
    fun `behind pace is shown on the rings`() {
        assertEquals("962 behind pace", ringContent(status(hour = 10, total = 400)).paceLine)
    }

    @Test
    fun `ahead of pace is shown on the rings`() {
        assertEquals("2,138 ahead of pace", ringContent(status()).paceLine)
    }

    @Test
    fun `the rings and the reminder always agree on pace`() {
        val st = status(hour = 12, inSlot = 272, total = 1720)
        assertEquals(paceCheckContent(st)!!.paceLine, ringContent(st).paceLine)
    }

    @Test
    fun `there is no pace line once the daily goal is met`() {
        assertNull(ringContent(status(total = 7000, s = settings.copy(inactivityEnabled = true))).paceLine)
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
    fun `walking extra earlier lowers this hour's number everywhere`() {
        // 2,400 before 11 AM, 4,600 to go across 9 hours.
        val st = DayStatus.compute(at(11, 7), settings, 66, 2466)
        assertEquals(512, st.slotTarget)
        assertEquals("of 512 this hour", ringContent(st).valueCaption)
        assertEquals("66 of 512 this hour", slotComplication(st).longText)
        assertEquals("446", paceCheckContent(st)!!.left)
    }

    @Test
    fun `a slow day keeps the even share and shows the gap as pace`() {
        val st = DayStatus.compute(at(11, 7), settings, 66, 566)
        assertEquals(584, st.slotTarget)
        assertEquals("of 584 this hour", ringContent(st).valueCaption)
        assertEquals("1,253 behind pace", ringContent(st).paceLine)
        assertEquals("1,253 behind pace", paceCheckContent(st)!!.paceLine)
    }

    @Test
    fun `this hour's number holds still as steps come in`() {
        assertEquals(512, DayStatus.compute(at(11, 7), settings, 0, 2400).slotTarget)
        assertEquals(512, DayStatus.compute(at(11, 30), settings, 300, 2700).slotTarget)
    }

    @Test
    fun `the hour that reaches the daily goal keeps showing a sensible number`() {
        val st = status(inSlot = 900, total = 7000, s = settings.copy(inactivityEnabled = false))
        assertEquals(584, st.slotTarget)
    }

    @Test
    fun `the reminder count complication carries the count as its value`() {
        val c = reminderCountComplication(status())
        assertEquals(12f, c.value, 0.001f)
        assertEquals(12f, c.max, 0.001f)
        assertEquals(0f, c.min, 0.001f)
        assertEquals("12", c.shortText)
        assertEquals("reminders", c.shortTitle)
        assertEquals("12 reminders today", c.longText)
    }

    @Test
    fun `the reminder count follows the interval`() {
        assertEquals(24f, reminderCountComplication(status(s = withInterval(30))).value, 0.001f)
        assertEquals(6f, reminderCountComplication(status(s = withInterval(120))).value, 0.001f)
        assertEquals(6f, reminderCountComplication(status(hour = 18, s = withInterval(120, endMinute = 19 * 60))).value, 0.001f)
    }

    @Test
    fun `the reminder count doesn't change with the steps or the hour`() {
        val counts = listOf(
            status(hour = 8, inSlot = 0, total = 0),
            status(hour = 11, inSlot = 66, total = 2466),
            status(hour = 19, inSlot = 0, total = 6900),
            status(hour = 14, inSlot = 100, total = 9000),
            status(hour = 22, inSlot = 0, total = 1000),
            status(hour = 6, inSlot = 0, total = 0),
        )
        counts.forEach { assertEquals(12f, reminderCountComplication(it).value, 0.001f) }
    }

    @Test
    fun `a paused day still has its reminder count`() {
        val paused = settings.copy(pausedThrough = day)
        assertEquals(12f, reminderCountComplication(status(s = paused)).value, 0.001f)
    }

    @Test
    fun `a day off has no reminders`() {
        val c = reminderCountComplication(status(s = allOff))
        assertEquals(0f, c.value, 0.001f)
        assertEquals("Off", c.shortText)
    }

    @Test
    fun `slot complication shows steps against the target`() {
        val c = slotComplication(status())
        assertEquals(292f, c.value, 0.001f)
        assertEquals(380f, c.max, 0.001f)
        assertEquals("292", c.shortText)
        assertEquals("of 380", c.shortTitle)
        assertEquals("292 of 380 this hour", c.longText)
    }

    @Test
    fun `slot complication carries the true count past its target`() {
        val c = slotComplication(status(inSlot = 900))
        // 2,600 before this hour, 4,400 to go across 10 hours.
        assertEquals(440f, c.value, 0.001f)
        assertEquals(440f, c.max, 0.001f)
        assertEquals(-460f, c.min, 0.001f)
        assertEquals(900f, c.value - c.min, 0.001f)
        assertEquals("900", c.shortText)
    }

    @Test
    fun `slot complication starts its range at zero up to its target`() {
        assertEquals(0f, slotComplication(status()).min, 0.001f)
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
    fun `today complication carries the true count past the goal`() {
        val c = todayComplication(status(total = 7100))
        assertEquals(7000f, c.value, 0.001f)
        assertEquals(7000f, c.max, 0.001f)
        assertEquals(7100f, c.value - c.min, 0.001f)
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
        // The system throws on a value outside its range.
        listOf(slotComplication(status(inSlot = 900)), todayComplication(status(total = 7100))).forEach {
            assertTrue(it.value >= it.min)
            assertTrue(it.value <= it.max)
        }
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
        assertEquals("962 behind pace", c.paceLine)
    }

    @Test
    fun `the slot caption names the two hours`() {
        val c = ringContent(status(total = 400, s = withInterval(120)))
        assertEquals("of 1,167 these two hours", c.valueCaption)
        assertEquals("962 behind pace", c.paceLine)
    }

    @Test
    fun `a short last slot says what it is`() {
        val c = ringContent(status(hour = 18, s = withInterval(120, endMinute = 19 * 60)))
        assertEquals("of 1,167 this hour", c.valueCaption)
    }

    @Test
    fun `the slot complication names the interval`() {
        assertEquals("100 of 180 this half hour", slotComplication(status(inSlot = 100, s = withInterval(30))).longText)
        assertEquals("100 of 720 these two hours", slotComplication(status(inSlot = 100, s = withInterval(120))).longText)
    }


    @Test
    fun `the pace check shows what's left and how the day stands`() {
        val c = paceCheckContent(status(hour = 12, inSlot = 272, total = 1720))!!
        assertEquals("312", c.left)
        assertEquals("left this hour", c.caption)
        assertEquals(272f / 584, c.slotProgress, 0.001f)
        assertEquals("Today 1,720 of 7,000", c.todayLine)
        assertEquals(1720f / 7000, c.todayProgress, 0.001f)
        assertEquals(2528f / 7000, c.paceMarker!!, 0.001f)
        assertEquals("808 behind pace", c.paceLine)
    }

    @Test
    fun `the pace check says when you're ahead`() {
        assertEquals("972 ahead of pace", paceCheckContent(status(hour = 12, inSlot = 100, total = 3500))!!.paceLine)
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
