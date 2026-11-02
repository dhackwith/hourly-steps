package com.hourlysteps.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Identifies one hour of one local day. [dateKey] is the ISO date used as the DB key. */
data class HourKey(val date: LocalDate, val hour: Int) {
    val dateKey: String get() = date.toString()
}

object HourBucketing {

    /**
     * Bucket for a step interval that ended at [end]. The end of an interval is exclusive, so a
     * point ending exactly on the hour boundary belongs to the hour that just finished.
     */
    fun keyForIntervalEnd(end: Instant, zone: ZoneId): HourKey =
        keyFor(end.minusMillis(1).atZone(zone))

    fun keyFor(time: ZonedDateTime): HourKey = HourKey(time.toLocalDate(), time.hour)
}
