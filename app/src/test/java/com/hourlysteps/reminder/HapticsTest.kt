package com.hourlysteps.reminder

import com.hourlysteps.domain.VibrationStrength
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticsTest {

    private fun LongArray.buzzTime() = filterIndexed { i, _ -> i % 2 == 1 }.sum()

    @Test
    fun `a goal's buzz lasts longer than a reminder's`() {
        for (strength in listOf(VibrationStrength.SHORT, VibrationStrength.LONG)) {
            assertTrue(Haptics.celebrationPattern(strength)!!.buzzTime() > Haptics.reminderPattern(strength)!!.buzzTime())
        }
    }

    @Test
    fun `even the short goal buzz is over a second of buzzing`() {
        assertEquals(1480L, Haptics.celebrationPattern(VibrationStrength.SHORT)!!.buzzTime())
    }

    @Test
    fun `off means no buzz`() {
        assertNull(Haptics.celebrationPattern(VibrationStrength.OFF))
    }

    @Test
    fun `patterns start with a gap and end on a buzz`() {
        for (strength in listOf(VibrationStrength.SHORT, VibrationStrength.LONG)) {
            assertEquals(0, Haptics.celebrationPattern(strength)!!.size % 2)
        }
    }

    @Test
    fun `full strength applies to the buzzes and not the gaps`() {
        assertArrayEquals(intArrayOf(0, 255, 0, 255), Haptics.fullAmplitudes(longArrayOf(0, 100, 50, 100)))
    }
}
