package com.hourlysteps.surfaces

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
    fun `the batch after a new slot refreshes however soon it comes`() {
        throttle.allowBatch(1_000)
        throttle.refreshed(2_000)
        throttle.openForNextBatch()
        assertTrue(throttle.allowBatch(3_000))
        assertFalse(throttle.allowBatch(4_000))
    }
}
