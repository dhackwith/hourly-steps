package com.hourlysteps.surfaces

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshThrottleTest {

    private val throttle = RefreshThrottle(minIntervalMs = 60_000)

    @Test
    fun `the first batch refreshes`() {
        assertTrue(throttle.allowBatch(1_000))
    }

    @Test
    fun `batches refresh at most once a minute`() {
        throttle.allowBatch(1_000)
        assertFalse(throttle.allowBatch(30_000))
        assertTrue(throttle.allowBatch(61_000))
    }

    @Test
    fun `a refresh for something else holds back the next batch too`() {
        throttle.refreshed(1_000)
        assertFalse(throttle.allowBatch(2_000))
    }

    @Test
    fun `a held back batch waits for the rest of the minute`() {
        throttle.allowBatch(1_000)
        assertEquals(31_000, throttle.waitMs(30_000))
        assertEquals(0, throttle.waitMs(61_000))
        assertEquals(0, throttle.waitMs(90_000))
    }

    @Test
    fun `nothing waits before the first refresh or after a new slot`() {
        assertEquals(0, throttle.waitMs(1_000))
        throttle.refreshed(2_000)
        throttle.openForNextBatch()
        assertEquals(0, throttle.waitMs(3_000))
    }

    @Test
    fun `the batch after a new slot refreshes however soon it comes`() {
        throttle.allowBatch(1_000)
        throttle.refreshed(2_000)
        throttle.openForNextBatch()
        assertTrue(throttle.allowBatch(3_000))
        assertFalse(throttle.allowBatch(4_000))
    }
}
