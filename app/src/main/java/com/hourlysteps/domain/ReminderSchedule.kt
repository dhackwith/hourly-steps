package com.hourlysteps.domain

import java.time.LocalTime
import java.time.ZonedDateTime

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
}
