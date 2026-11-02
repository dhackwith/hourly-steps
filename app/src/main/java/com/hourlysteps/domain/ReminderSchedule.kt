package com.hourlysteps.domain

import java.time.ZonedDateTime

object ReminderSchedule {

    /**
     * The next slot check strictly after [now], skipping days off and paused days. Null when
     * none is coming: every day is off, or reminders are paused until resumed. DST gaps are
     * handled by the zone conversion, which shifts a non-existent local time forward.
     */
    fun nextCheckTime(now: ZonedDateTime, settings: Settings): ZonedDateTime? {
        val paused = settings.pausedThrough
        if (paused == Settings.PAUSED_UNTIL_RESUMED) return null

        val today = now.toLocalDate()
        var day = if (paused != null && !paused.isBefore(today)) paused.plusDays(1) else today
        // Today plus a full week covers a schedule with a single active day whose window has passed.
        repeat(8) {
            settings.schedule.planFor(day.dayOfWeek)?.let { plan ->
                for (slot in Slots.forDay(plan, settings.schedule.slotMinutes, day)) {
                    val check = slot.checkAt(now.zone)
                    if (check.isAfter(now)) return check
                }
            }
            day = day.plusDays(1)
        }
        return null
    }

    /**
     * True when a check for [slot] runs after the slot has ended, e.g. the watch was off, the
     * clock jumped, or the alarm was deferred. Reminding then would describe a period that is
     * already over, so the check should be skipped.
     */
    fun isLate(slot: Slot, now: ZonedDateTime): Boolean = !now.isBefore(slot.endAt(now.zone))
}
