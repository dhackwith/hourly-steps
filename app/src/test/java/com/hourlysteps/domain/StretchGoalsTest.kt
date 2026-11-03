package com.hourlysteps.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StretchGoalsTest {

    private val goals = listOf(3000, 1500, 500)

    @Test
    fun `each goal counts on top of the one before`() {
        assertEquals(listOf(10000, 11500, 12000), StretchGoals.targets(7000, goals))
    }

    @Test
    fun `no goals means no progress`() {
        assertNull(StretchGoals.progress(7000, emptyList(), 9000))
    }

    @Test
    fun `nothing shows before the daily goal is met`() {
        assertNull(StretchGoals.progress(7000, goals, 6999))
    }

    @Test
    fun `meeting the daily goal starts the first lap`() {
        val progress = StretchGoals.progress(7000, goals, 7000)!!
        assertEquals(0, progress.reached)
        assertEquals(1, progress.level)
        assertEquals(0, progress.lapSteps)
        assertEquals(3000, progress.lapSize)
        assertEquals(10000, progress.target)
    }

    @Test
    fun `a lap measures from the goal before it`() {
        val progress = StretchGoals.progress(7000, goals, 10750)!!
        assertEquals(1, progress.reached)
        assertEquals(2, progress.level)
        assertEquals(750, progress.lapSteps)
        assertEquals(0.5f, progress.lapProgress, 0.001f)
        assertEquals(11500, progress.target)
        assertFalse(progress.allReached)
    }

    @Test
    fun `the last lap stays full once it's reached`() {
        val progress = StretchGoals.progress(7000, goals, 20000)!!
        assertEquals(3, progress.reached)
        assertEquals(3, progress.level)
        assertEquals(1f, progress.lapProgress, 0.001f)
        assertEquals(12000, progress.target)
        assertTrue(progress.allReached)
    }

    @Test
    fun `reached counts every goal passed`() {
        assertEquals(0, StretchGoals.reachedCount(7000, goals, 9999))
        assertEquals(1, StretchGoals.reachedCount(7000, goals, 10000))
        assertEquals(3, StretchGoals.reachedCount(7000, goals, 12000))
    }

    @Test
    fun `the name says which goal when there are several`() {
        assertEquals("Stretch goal", StretchGoals.name(1, 1))
        assertEquals("Stretch 2 of 3", StretchGoals.name(2, 3))
    }
}
