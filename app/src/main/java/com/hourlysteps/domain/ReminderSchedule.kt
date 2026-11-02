package com.hourlysteps.domain

import java.time.ZonedDateTime

object ReminderSchedule {

    /**
     * The next slot check strictly after [now], skipping days off and paused days. Null when
     * none is coming: every day is off, or reminders are paused until resumed. DST gaps are
     * handled by the zone conversion, which shifts a non-existent local time forward.
     */
    fun nextCheckTime(now: ZonedDateTime, settings: Settings): ZonedDateTime? =
        firstAfter(now, settings) { listOf(it.checkAt(now.zone)) }

    /**
     * The next moment a slot starts or ends strictly after [now], when what the tile and
     * complications show goes out of date whether or not any steps arrive. Skips the same days as
     * [nextCheckTime].
     */
    fun nextSlotChange(now: ZonedDateTime, settings: Settings): ZonedDateTime? =
        firstAfter(now, settings) { listOf(it.startAt(now.zone), it.endAt(now.zone)) }

    private fun firstAfter(now: ZonedDateTime, settings: Settings, times: (Slot) -> List<ZonedDateTime>): ZonedDateTime? {
        val paused = settings.pausedThrough
        if (paused == Settings.PAUSED_UNTIL_RESUMED) return null

        val today = now.toLocalDate()
        var day = if (paused != null && !paused.isBefore(today)) paused.plusDays(1) else today
        // Today plus a full week covers a schedule with a single active day whose window has passed.
        repeat(8) {
            settings.schedule.planFor(day.dayOfWeek)?.let { plan ->
                for (slot in Slots.forDay(plan, settings.schedule.slotMinutes, day)) {
                    times(slot).firstOrNull { it.isAfter(now) }?.let { return it }
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

    /** When a snoozed reminder should come back, or null if the slot would be over by then. */
    fun snoozeUntil(slot: Slot, now: ZonedDateTime): ZonedDateTime? =
        now.plusMinutes(SNOOZE_MINUTES).takeIf { it.isBefore(slot.endAt(now.zone)) }

    const val SNOOZE_MINUTES = 5L
}
