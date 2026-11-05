package com.hourlysteps.reminder

import com.hourlysteps.testing.Events
import com.hourlysteps.testing.FakeSlotChangeAlarm
import com.hourlysteps.testing.FakeSteps
import com.hourlysteps.testing.FakeSurfaces
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SlotChangeTest {

    private val events = Events()
    private val steps = FakeSteps(events)
    private val slotChange = SlotChange(steps, FakeSurfaces(events), FakeSlotChangeAlarm(events))

    @Test
    fun `the last slot's steps are recorded before the new slot is shown`() = runTest {
        steps.heldBack = 125
        slotChange.run()
        assertEquals(
            listOf("flush and wait", "steps recorded 125", "refresh for new slot", "arm slot change"),
            events.all,
        )
    }

    @Test
    fun `a failed flush still shows the new slot and arms the next change`() = runTest {
        steps.flushFails = true
        slotChange.run()
        assertEquals(listOf("flush and wait", "refresh for new slot", "arm slot change"), events.all)
    }

    @Test
    fun `a flush that never finishes is given up on within a few seconds`() = runTest {
        steps.flushHangs = true
        slotChange.run()
        assertEquals(listOf("flush and wait", "refresh for new slot", "arm slot change"), events.all)
        assertTrue("waited ${currentTime} ms", currentTime in 1..10_000)
    }
}
