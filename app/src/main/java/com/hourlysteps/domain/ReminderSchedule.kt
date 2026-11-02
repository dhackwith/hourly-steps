package com.hourlysteps.domain

import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

object ReminderSchedule {

    /**
     * The next HH:50 strictly after [now] whose hour is inside the reminder window.
     * Rolls over to the next day when today's window is finished. DST gaps are handled by
     * ZonedDateTime.of, which shifts a non-existent local time forward.
     */
    fun nextCheckTime(now: ZonedDateTime, settings: Settings): ZonedDateTime {
        var day = now.toLocalDate()
        repeat(3) {
            for (hour in settings.firstHour..settings.lastHour) {
                val candidate = ZonedDateTime.of(day, LocalTime.of(hour, Settings.CHECK_MINUTE), now.zone)
                if (candidate.isAfter(now)) return candidate
            }
            day = day.plusDays(1)
        }
        error("No reminder slot found within three days; window=${settings.firstHour}..${settings.lastHour}")
    }

    /**
     * True when a check armed for [slot] runs after that slot's hour has ended, e.g. the watch
     * was off, the clock jumped, or the alarm was deferred. Reminding then would describe an
     * hour that is already over, so the check should be skipped.
     */
    fun isLate(slot: ZonedDateTime, now: ZonedDateTime): Boolean =
        !now.isBefore(slot.truncatedTo(ChronoUnit.HOURS).plusHours(1))
}
