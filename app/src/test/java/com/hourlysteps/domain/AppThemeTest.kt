package com.hourlysteps.domain

import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppThemeTest {

    private val black = 0xFF000000

    private fun luminance(color: Long): Double {
        fun channel(shift: Int): Double {
            val v = ((color shr shift) and 0xFF) / 255.0
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private fun contrast(a: Long, b: Long): Double {
        val (light, dark) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (light + 0.05) / (dark + 0.05)
    }

    private fun assertContrast(minimum: Double, what: String, a: Long, b: Long) {
        assertTrue("$what is only ${contrast(a, b)}:1", contrast(a, b) >= minimum)
    }

    /** Straight-line distance between two colours. Rings can differ by hue, not only brightness. */
    private fun colourDistance(a: Long, b: Long): Double {
        fun gap(shift: Int) = (((a shr shift) and 0xFF) - ((b shr shift) and 0xFF)).toDouble().pow(2)
        return kotlin.math.sqrt(gap(16) + gap(8) + gap(0))
    }

    private fun forEachTheme(check: (AppTheme) -> Unit) = AppTheme.entries.forEach(check)

    @Test
    fun `the default is light purple and violet`() {
        assertEquals(AppTheme.VIOLET, AppTheme.DEFAULT)
        assertEquals("Violet", AppTheme.DEFAULT.label)
    }

    @Test
    fun `every theme has its own name and colours`() {
        assertEquals(AppTheme.entries.size, AppTheme.entries.map { it.label }.toSet().size)
        assertEquals(AppTheme.entries.size, AppTheme.entries.map { it.today to it.slot }.toSet().size)
    }

    @Test
    fun `accent text is readable on the black background`() = forEachTheme {
        assertContrast(4.5, "${it.label} today on black", it.today, black)
        assertContrast(4.5, "${it.label} slot on black", it.slot, black)
        assertContrast(4.5, "${it.label} goal met on black", it.goalMet, black)
    }

    @Test
    fun `button text is readable`() = forEachTheme {
        assertContrast(4.5, "${it.label} button text", it.onAccent, it.today)
        assertContrast(4.5, "${it.label} selected row", it.onPrimaryContainer, it.primaryContainer)
        assertContrast(4.5, "${it.label} secondary row", it.onSecondaryContainer, it.secondaryContainer)
        assertContrast(4.5, "${it.label} Plan B row", it.onTertiaryContainer, it.tertiaryContainer)
        assertContrast(4.5, "${it.label} list row", 0xFFFFFFFF, it.track)
    }

    @Test
    fun `progress stands out from the empty ring`() = forEachTheme {
        assertContrast(3.0, "${it.label} today on track", it.today, it.track)
        assertContrast(3.0, "${it.label} slot on track", it.slot, it.track)
        assertContrast(3.0, "${it.label} goal met on track", it.goalMet, it.track)
    }

    @Test
    fun `the two rings can be told apart`() = forEachTheme {
        assertTrue("${it.label} rings match", it.today != it.slot)
        assertTrue("${it.label} rings are too alike", colourDistance(it.today, it.slot) >= 60.0)
    }

    @Test
    fun `mixing moves between two colours`() {
        assertEquals(0xFF000000, AppTheme.mix(0xFF000000, 0xFFFFFFFF, 0f))
        assertEquals(0xFFFFFFFF, AppTheme.mix(0xFF000000, 0xFFFFFFFF, 1f))
        assertEquals(0xFF7F7F7F, AppTheme.mix(0xFF000000, 0xFFFFFFFF, 0.5f))
        assertEquals(0xFF7F4020, AppTheme.mix(0xFFFF8040, 0xFF000000, 0.5f))
    }
}
