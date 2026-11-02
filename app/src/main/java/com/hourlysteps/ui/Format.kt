package com.hourlysteps.ui

import com.hourlysteps.domain.DayAssignment
import com.hourlysteps.domain.DayPlan
import com.hourlysteps.domain.GoalCalculator
import com.hourlysteps.domain.Schedule
import com.hourlysteps.domain.ScheduleMode
import com.hourlysteps.domain.Settings
import com.hourlysteps.domain.SlotWording
import com.hourlysteps.domain.Slots
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

fun formatTime(time: LocalTime): String = time.format(timeFormat)

/** When the plan's first check falls: "8:50 AM" with hourly reminders. */
fun formatFirstCheck(plan: DayPlan, slotMinutes: Int): String = formatMinute(Slots.checkMinutes(plan, slotMinutes).first())

/** When the plan's last check falls: "7:50 PM" with hourly reminders. */
fun formatLastCheck(plan: DayPlan, slotMinutes: Int): String = formatMinute(Slots.checkMinutes(plan, slotMinutes).last())

private fun formatMinute(minute: Int): String = formatTime(LocalTime.of(minute / 60, minute % 60))

/** "7,000 · 8:50 AM–7:50 PM" */
fun formatPlan(plan: DayPlan, slotMinutes: Int): String =
    "${formatSteps(plan.dailyGoal)} · ${formatFirstCheck(plan, slotMinutes)}–${formatLastCheck(plan, slotMinutes)}"

/** "24 reminders · ≈ 292 steps per 30 min" */
fun formatPlanCaption(plan: DayPlan, slotMinutes: Int): String =
    "${formatReminderCount(plan, slotMinutes)} · ≈ ${formatSteps(GoalCalculator.slotGoal(plan, slotMinutes))} steps ${SlotWording.perSlot(slotMinutes)}"

/** "24 reminders · ≈ 292/30 min", for the narrower picker screens. */
fun formatPickerCaption(plan: DayPlan, slotMinutes: Int): String =
    "${formatReminderCount(plan, slotMinutes)} · ${formatSlotGoal(plan, slotMinutes)}"

/** "≈ 292/30 min" */
fun formatSlotGoal(plan: DayPlan, slotMinutes: Int): String =
    "≈ ${formatSteps(GoalCalculator.slotGoal(plan, slotMinutes))}${SlotWording.perSlotShort(slotMinutes)}"

private fun formatReminderCount(plan: DayPlan, slotMinutes: Int): String {
    val count = GoalCalculator.slotCount(plan, slotMinutes)
    return if (count == 1) "1 reminder" else "$count reminders"
}

/** "1,197 behind pace", "On pace" or "580 ahead of pace", from steps ahead of pace. */
fun formatPaceLine(gap: Int): String = when {
    gap < 0 -> "${formatSteps(-gap)} behind pace"
    gap > 0 -> "${formatSteps(gap)} ahead of pace"
    else -> "On pace"
}

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
