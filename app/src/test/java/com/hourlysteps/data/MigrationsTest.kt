package com.hourlysteps.data

import com.hourlysteps.data.db.hourlyRowInterval
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class MigrationsTest {

    private val zone: ZoneId = ZoneId.of("America/Los_Angeles")

    private fun instant(text: String) = Instant.parse(text).toEpochMilli()

    @Test
    fun `an hourly row spans that local hour`() {
        val (start, end) = hourlyRowInterval("2026-06-10", 10, zone)
        assertEquals(instant("2026-06-10T17:00:00Z"), start)
        assertEquals(instant("2026-06-10T18:00:00Z"), end)
    }

    @Test
    fun `the last hour of the day ends at midnight`() {
        val (_, end) = hourlyRowInterval("2026-06-10", 23, zone)
        assertEquals(instant("2026-06-11T07:00:00Z"), end)
    }

    @Test
    fun `a row in the skipped daylight saving hour lands on the hour after the gap`() {
        // 2026-03-08 02:00 does not exist in Los Angeles. Its end, 03:00 PDT, is 10:00 UTC, so a
        // sum over the 1 AM slot's end boundary still counts it exactly once.
        val (_, end) = hourlyRowInterval("2026-03-08", 2, zone)
        assertEquals(instant("2026-03-08T10:00:00Z"), end)
    }
}
