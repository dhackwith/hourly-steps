package com.hourlysteps.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class HourBucketingTest {

    private val zone: ZoneId = ZoneId.of("America/Los_Angeles")
    private val day: LocalDate = LocalDate.of(2026, 6, 10)

    private fun instant(hour: Int, minute: Int, second: Int = 0) =
        ZonedDateTime.of(day, LocalTime.of(hour, minute, second), zone).toInstant()

    @Test
    fun `interval ending mid hour lands in that hour`() {
        assertEquals(HourKey(day, 10), HourBucketing.keyForIntervalEnd(instant(10, 30), zone))
    }

    @Test
    fun `interval ending exactly on the boundary lands in the hour that just finished`() {
        assertEquals(HourKey(day, 9), HourBucketing.keyForIntervalEnd(instant(10, 0), zone))
    }

    @Test
    fun `interval ending one second into the hour lands in the new hour`() {
        assertEquals(HourKey(day, 10), HourBucketing.keyForIntervalEnd(instant(10, 0, 1), zone))
    }

    @Test
    fun `midnight boundary lands on the previous day`() {
        assertEquals(HourKey(day.minusDays(1), 23), HourBucketing.keyForIntervalEnd(instant(0, 0), zone))
    }

    @Test
    fun `date key is ISO formatted`() {
        assertEquals("2026-06-10", HourKey(day, 3).dateKey)
    }
}
