package com.hourlysteps.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

enum class ScheduleMode { EVERY_DAY, TWO_PLANS }

enum class PlanId { A, B }

/** What a day of the week does. In [ScheduleMode.EVERY_DAY] mode any non-OFF day uses Plan A. */
enum class DayAssignment { PLAN_A, PLAN_B, OFF }

/**
 * A day's goal and reminder window. The window is in minutes of the day, with the end exclusive,
 * so 480..1200 is 8:00 AM to 8:00 PM and an end of 1440 means midnight.
 */
data class DayPlan(
    val dailyGoal: Int = Settings.DEFAULT_DAILY_GOAL,
    val startMinute: Int = Settings.DEFAULT_FIRST_HOUR * 60,
    val endMinute: Int = (Settings.DEFAULT_LAST_HOUR + 1) * 60,
) {
    /** Hour of the first slot, for the hour-based window pickers. */
    val firstHour: Int get() = startMinute / 60

    /** Hour of the last slot, for the hour-based window pickers. */
    val lastHour: Int get() = (endMinute - 1) / 60

    fun withFirstHour(hour: Int): DayPlan =
        copy(startMinute = hour * 60, endMinute = maxOf(endMinute, (hour + 1) * 60))

    fun withLastHour(hour: Int): DayPlan =
        copy(endMinute = (hour + 1) * 60, startMinute = minOf(startMinute, hour * 60))
}

/** How the week is laid out. Nothing assumes Monday to Friday; each day is assigned freely. */
data class Schedule(
    val mode: ScheduleMode = ScheduleMode.EVERY_DAY,
    val planA: DayPlan = DayPlan(),
    val planB: DayPlan = DayPlan(),
    val assignments: Map<DayOfWeek, DayAssignment> = DayOfWeek.entries.associateWith { DayAssignment.PLAN_A },
    val slotMinutes: Int = Settings.DEFAULT_SLOT_MINUTES,
) {
    fun assignmentFor(day: DayOfWeek): DayAssignment {
        val stored = assignments[day] ?: DayAssignment.PLAN_A
        return when {
            stored == DayAssignment.OFF -> DayAssignment.OFF
            mode == ScheduleMode.EVERY_DAY -> DayAssignment.PLAN_A
            else -> stored
        }
    }

    /** The plan for [day], or null when it is a day off. */
    fun planFor(day: DayOfWeek): DayPlan? = when (assignmentFor(day)) {
        DayAssignment.PLAN_A -> planA
        DayAssignment.PLAN_B -> planB
        DayAssignment.OFF -> null
    }

    fun plan(id: PlanId): DayPlan = if (id == PlanId.A) planA else planB

    fun withPlan(id: PlanId, plan: DayPlan): Schedule =
        if (id == PlanId.A) copy(planA = plan) else copy(planB = plan)

    /** The next value when a day is tapped in the week editor. */
    fun cycled(day: DayOfWeek): Schedule {
        val next = when (assignmentFor(day)) {
            DayAssignment.PLAN_A -> if (mode == ScheduleMode.TWO_PLANS) DayAssignment.PLAN_B else DayAssignment.OFF
            DayAssignment.PLAN_B -> DayAssignment.OFF
            DayAssignment.OFF -> DayAssignment.PLAN_A
        }
        return copy(assignments = assignments + (day to next))
    }
}

/** One step-goal period within a day. The check fires a little before it ends. */
data class Slot(
    val date: LocalDate,
    val startMinute: Int,
    val endMinute: Int,
    val index: Int,
    val count: Int,
) {
    val lengthMinutes: Int get() = endMinute - startMinute

    /** Minutes before the slot's end that the check fires: 5 of 30, 10 of 60, 20 of 120. */
    val leadMinutes: Int get() = (lengthMinutes / 6).coerceAtLeast(1)

    val checkMinute: Int get() = endMinute - leadMinutes

    fun startAt(zone: ZoneId): ZonedDateTime = at(startMinute, zone)
    fun endAt(zone: ZoneId): ZonedDateTime = at(endMinute, zone)
    fun checkAt(zone: ZoneId): ZonedDateTime = at(checkMinute, zone)

    /** Halfway between the check and the slot's end: :55 of an hourly slot. */
    fun secondNudgeAt(zone: ZoneId): ZonedDateTime = endAt(zone).minusSeconds(leadMinutes * 30L)

    private fun at(minute: Int, zone: ZoneId): ZonedDateTime =
        date.atStartOfDay().plusMinutes(minute.toLong()).atZone(zone)
}

object Slots {

    /**
     * Start and end minutes of each slot. The slots cover the window exactly, so when the window
     * is not a multiple of the slot length the last slot is shorter.
     */
    fun boundaries(plan: DayPlan, slotMinutes: Int): List<Pair<Int, Int>> {
        if (plan.startMinute >= plan.endMinute || slotMinutes <= 0) return emptyList()
        return (plan.startMinute until plan.endMinute step slotMinutes).map { start ->
            start to minOf(start + slotMinutes, plan.endMinute)
        }
    }

    fun forDay(plan: DayPlan, slotMinutes: Int, date: LocalDate): List<Slot> {
        val bounds = boundaries(plan, slotMinutes)
        return bounds.mapIndexed { index, (start, end) -> Slot(date, start, end, index, bounds.size) }
    }

    /** When each check falls, in minutes of the day. */
    fun checkMinutes(plan: DayPlan, slotMinutes: Int): List<Int> =
        forDay(plan, slotMinutes, LocalDate.MIN).map { it.checkMinute }

    /** The slot containing [time], or null on a day off or outside the window. */
    fun at(schedule: Schedule, time: ZonedDateTime): Slot? {
        val date = time.toLocalDate()
        val plan = schedule.planFor(date.dayOfWeek) ?: return null
        val minute = minuteOfDay(time)
        return forDay(plan, schedule.slotMinutes, date).firstOrNull { minute >= it.startMinute && minute < it.endMinute }
    }

    fun minuteOfDay(time: ZonedDateTime): Int = time.hour * 60 + time.minute
}
