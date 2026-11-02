package com.hourlysteps.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.hourlysteps.domain.DayResult
import com.hourlysteps.domain.StreakDay
import com.hourlysteps.domain.StreakInfo
import com.hourlysteps.surfaces.RingColors
import java.time.format.TextStyle

/** The current streak, the record, and the last seven days at a glance. */
@Composable
fun StreakPage(streak: StreakInfo) {
    val colors = MaterialTheme.colorScheme
    val gold = Color(RingColors.GOAL_MET)

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            streak.current.toString(),
            style = MaterialTheme.typography.displayLarge,
            color = if (streak.current > 0) gold else colors.onSurface,
        )
        Text(
            if (streak.current == 1) "day in a row" else "days in a row",
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            streak.week.forEach { DayCell(it) }
        }
        Text(
            statusLine(streak),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
        )
        if (streak.longest > 0) {
            Text(
                "Longest: ${streak.longest}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

private fun statusLine(streak: StreakInfo): String = when (streak.todayResult) {
    DayResult.MET -> "Goal met today"
    DayResult.REST -> "Rest day"
    else -> if (streak.current > 0) "Hit today's goal to keep going" else "Hit today's goal to start one"
}

@Composable
private fun DayCell(day: StreakDay) {
    val colors = MaterialTheme.colorScheme
    val gold = Color(RingColors.GOAL_MET)
    val locale = LocalConfiguration.current.locales[0]
    val letter = day.date.dayOfWeek.getDisplayName(TextStyle.NARROW, locale)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            letter,
            fontSize = 11.sp,
            color = if (day.isToday) Color(RingColors.SLOT) else colors.onSurfaceVariant,
        )
        val base = Modifier.size(22.dp)
        val cell = when (day.result) {
            DayResult.MET -> base.background(gold, CircleShape)
            DayResult.MISSED, DayResult.REST -> base.background(Color(RingColors.TRACK), CircleShape)
            null -> if (day.isToday) base.border(2.dp, Color(RingColors.SLOT), CircleShape) else base
        }
        Box(cell, contentAlignment = Alignment.Center) {
            val (mark, markColor) = when (day.result) {
                DayResult.MET -> "✓" to Color.Black
                DayResult.MISSED -> "✗" to colors.error
                DayResult.REST -> "–" to colors.onSurfaceVariant
                null -> "" to colors.onSurfaceVariant
            }
            Text(mark, fontSize = 12.sp, color = markColor)
        }
    }
}
