package com.hourlysteps.ui

import com.hourlysteps.domain.DayAssignment
import com.hourlysteps.domain.DayPlan
import com.hourlysteps.domain.Schedule
import com.hourlysteps.domain.ScheduleMode
import com.hourlysteps.domain.Settings
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

private val numberFormat: NumberFormat = NumberFormat.getIntegerInstance()
private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

fun formatSteps(value: Int): String = numberFormat.format(value)

/** "8:50 AM" style label for the :50 check of [hour]. */
fun formatSlot(hour: Int): String = LocalTime.of(hour, 50).format(timeFormat)

fun formatTime(time: LocalTime): String = time.format(timeFormat)

/** "7,000 · 8:50 AM–7:50 PM" */
fun formatPlan(plan: DayPlan): String =
    "${formatSteps(plan.dailyGoal)} · ${formatSlot(plan.firstHour)}–${formatSlot(plan.lastHour)}"

/** The time alone today, or with the weekday when it is on another day: "Mon 8:50 AM". */
fun formatNextCheck(next: ZonedDateTime, today: LocalDate, locale: Locale = Locale.getDefault()): String {
    val time = formatTime(next.toLocalTime())
    return if (next.toLocalDate() == today) time else "${dayName(next.dayOfWeek, locale)} $time"
}

/** How long a pause lasts: "today", "Mon, Nov 3", or "you resume". */
fun formatPauseEnd(through: LocalDate, today: LocalDate, locale: Locale = Locale.getDefault()): String = when {
    through == Settings.PAUSED_UNTIL_RESUMED -> "you resume"
    through == today -> "the end of today"
    else -> through.format(DateTimeFormatter.ofPattern("EEE, MMM d", locale))
}

fun dayName(day: DayOfWeek, locale: Locale = Locale.getDefault()): String =
    day.getDisplayName(TextStyle.SHORT, locale)

fun fullDayName(day: DayOfWeek, locale: Locale = Locale.getDefault()): String =
    day.getDisplayName(TextStyle.FULL, locale)

/** The seven days in the order the locale's week runs. */
fun orderedWeek(locale: Locale = Locale.getDefault()): List<DayOfWeek> {
    val first = WeekFields.of(locale).firstDayOfWeek
    return (0L until 7L).map(first::plus)
}

/**
 * One line describing the week. Every day mode: "Every day" or "Off: Sat, Sun".
 * Two plans mode: "A: Thu–Sun · B: Mon · Off: Tue, Wed".
 */
fun formatWeek(schedule: Schedule, locale: Locale = Locale.getDefault()): String {
    val week = orderedWeek(locale)
    fun daysWith(assignment: DayAssignment) = week.filter { schedule.assignmentFor(it) == assignment }
    val off = daysWith(DayAssignment.OFF)

    if (off.size == week.size) return "All days off"
    if (schedule.mode == ScheduleMode.EVERY_DAY) {
        return if (off.isEmpty()) "Every day" else "Off: ${formatDays(off, week, locale)}"
    }
    return listOf("A" to daysWith(DayAssignment.PLAN_A), "B" to daysWith(DayAssignment.PLAN_B), "Off" to off)
        .filter { (_, days) -> days.isNotEmpty() }
        .joinToString(" · ") { (label, days) -> "$label: ${formatDays(days, week, locale)}" }
}

/** Runs of three or more days collapse to a range: "Mon–Fri", "Sat, Sun". */
private fun formatDays(days: List<DayOfWeek>, week: List<DayOfWeek>, locale: Locale): String {
    if (days.size == week.size) return "Every day"
    val runs = mutableListOf<MutableList<DayOfWeek>>()
    for (day in days) {
        val last = runs.lastOrNull()
        if (last != null && week.indexOf(day) == week.indexOf(last.last()) + 1) last += day else runs += mutableListOf(day)
    }
    return runs.joinToString(", ") { run ->
        if (run.size >= 3) {
            "${dayName(run.first(), locale)}–${dayName(run.last(), locale)}"
        } else {
            run.joinToString(", ") { dayName(it, locale) }
        }
    }
}
