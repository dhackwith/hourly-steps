package com.hourlysteps.celebration

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val FireworkColors = listOf(
    Color(0xFFFFD54F), // gold
    Color(0xFFFF8A65), // coral
    Color(0xFF81D4FA), // sky
    Color(0xFFA5D6A7), // mint
    Color(0xFFCE93D8), // lilac
    Color(0xFFFFFFFF),
)

private val GoldColors = listOf(
    Color(0xFFFFD54F), // gold
    Color(0xFFFFE082), // pale gold
    Color(0xFFFFB300), // amber
    Color(0xFFFF8F00), // deep amber
    Color(0xFFFFFFFF),
)

/** The hourly celebration's mixed colours, or the daily goal's denser gold. */
enum class FireworkStyle(val colors: List<Color>, val burstsPerCycle: Int) {
    STANDARD(FireworkColors, 7),
    GOLD(GoldColors, 10),
}

private const val CYCLE_MS = 2_000L
private const val BURST_LIFE_MS = 1_600L
private const val SPARKS = 18

/**
 * Looping firework bursts drawn on a Canvas. Each cycle places its bursts at seeded-random points
 * around the edge of the round screen so the text in the middle stays readable. With [animate] off
 * (system animations disabled) a single still frame is drawn.
 */
@Composable
fun Fireworks(modifier: Modifier = Modifier, animate: Boolean = true, style: FireworkStyle = FireworkStyle.STANDARD) {
    var elapsed by remember { mutableLongStateOf(if (animate) 0L else 650L) }
    if (animate) {
        LaunchedEffect(Unit) {
            val start = withFrameMillis { it }
            while (true) {
                withFrameMillis { elapsed = it - start }
            }
        }
    }
    Canvas(modifier) {
        val cycle = elapsed / CYCLE_MS
        val inCycle = elapsed % CYCLE_MS
        // Draw the tail of the previous cycle too so bursts don't vanish at the cycle boundary.
        drawCycle(cycle, inCycle, style)
        if (cycle > 0) drawCycle(cycle - 1, inCycle + CYCLE_MS, style)
        // Soft dark glow in the middle so sparks fade out before they reach the text.
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color.Black.copy(alpha = 0.92f),
                0.6f to Color.Black.copy(alpha = 0.75f),
                1f to Color.Transparent,
                center = center,
                radius = size.minDimension * 0.36f,
            ),
            radius = size.minDimension * 0.36f,
        )
    }
}

private fun DrawScope.drawCycle(cycle: Long, msIntoCycle: Long, style: FireworkStyle) {
    val radius = size.minDimension / 2f
    val center = Offset(size.width / 2f, size.height / 2f)
    for (i in 0 until style.burstsPerCycle) {
        val rnd = Random(cycle * 101 + i)
        val startMs = (i * CYCLE_MS / style.burstsPerCycle) + rnd.nextLong(0, 200)
        val t = msIntoCycle - startMs
        if (t < 0 || t > BURST_LIFE_MS) continue

        // Burst centres sit on a ring between 50% and 78% of the radius, around the text.
        val placeAngle = rnd.nextDouble(0.0, 2 * PI)
        val placeDist = radius * rnd.nextDouble(0.50, 0.78).toFloat()
        val burstCenter = center + Offset(cos(placeAngle).toFloat() * placeDist, sin(placeAngle).toFloat() * placeDist)
        val color = style.colors[rnd.nextInt(style.colors.size)]
        val accent = style.colors[rnd.nextInt(style.colors.size)]
        val reach = radius * rnd.nextDouble(0.30, 0.42).toFloat()
        val spin = rnd.nextDouble(0.0, 2 * PI)

        val p = t.toFloat() / BURST_LIFE_MS
        val easeOut = 1f - (1f - p) * (1f - p) * (1f - p)
        // Stay bright for most of the burst, then fade quickly.
        val alpha = (1f - p * p * p).coerceIn(0f, 1f)
        val gravity = radius * 0.22f * p * p
        val sparkRadius = radius * 0.026f * (1f - 0.5f * p)

        for (s in 0 until SPARKS) {
            val a = spin + s * 2 * PI / SPARKS
            val dir = Offset(cos(a).toFloat(), sin(a).toFloat())
            val head = burstCenter + dir * (reach * easeOut) + Offset(0f, gravity)
            val tail = burstCenter + dir * (reach * easeOut * 0.45f) + Offset(0f, gravity * 0.5f)
            drawLine(color.copy(alpha = alpha * 0.6f), tail, head, strokeWidth = sparkRadius * 0.9f, cap = StrokeCap.Round)
            drawCircle(color.copy(alpha = alpha), radius = sparkRadius * 1.25f, center = head)

            // Inner ring of shorter, offset sparks in an accent colour for a fuller burst.
            val a2 = a + PI / SPARKS
            val dir2 = Offset(cos(a2).toFloat(), sin(a2).toFloat())
            val head2 = burstCenter + dir2 * (reach * 0.6f * easeOut) + Offset(0f, gravity * 0.8f)
            drawCircle(accent.copy(alpha = alpha * 0.9f), radius = sparkRadius * 0.85f, center = head2)
        }
        // Flash at ignition.
        if (p < 0.18f) {
            drawCircle(Color.White.copy(alpha = (0.18f - p) / 0.18f * 0.9f), radius = radius * 0.07f, center = burstCenter)
        }
    }
}
