package com.hourlysteps.domain

import java.time.LocalDate
import java.time.ZonedDateTime

enum class DayStatusKind {
    /** Inside a slot. */
    ACTIVE,

    /** A plan applies today, but the time is before or after its window. */
    OUTSIDE_WINDOW,
    DAY_OFF,
    PAUSED,
}

/** Where the day stands right now. Home, the tile and the complications all read this. */
data class DayStatus(
    val kind: DayStatusKind,
    val today: LocalDate,
    val slot: Slot?,
    val stepsInSlot: Int,
    val todayTotal: Int,
    /** Today's daily goal, or null on a day off. */
    val dailyGoal: Int?,
    val mode: CheckMode,
    /** What this slot is measured against: the slot goal, or the inactivity threshold once the daily goal is met. */
    val slotTarget: Int,
    /** Steps to have by now to be on track; only set inside a slot while the daily goal is still open. */
    val paceTarget: Int?,
    val nextCheck: ZonedDateTime?,
    val pausedThrough: LocalDate?,
) {
    val dailyGoalMet: Boolean get() = dailyGoal != null && todayTotal >= dailyGoal

    /** Steps ahead of pace, or negative when behind. */
    val paceGap: Int? get() = paceTarget?.let { todayTotal - it }

    /** Goal met and nothing left to do today. Outside the window no checks run, so inactivity reminders don't matter. */
    val finished: Boolean
        get() = dailyGoalMet && (kind == DayStatusKind.OUTSIDE_WINDOW || (kind == DayStatusKind.ACTIVE && mode == CheckMode.OFF))

    val slotProgress: Float
        get() = if (kind != DayStatusKind.ACTIVE || slotTarget <= 0) 0f else (stepsInSlot.toFloat() / slotTarget).coerceIn(0f, 1f)

    val todayProgress: Float
        get() = when {
            dailyGoal == null || dailyGoal <= 0 -> 0f
            kind != DayStatusKind.ACTIVE && kind != DayStatusKind.OUTSIDE_WINDOW -> 0f
            else -> (todayTotal.toFloat() / dailyGoal).coerceIn(0f, 1f)
        }

    companion object {
        fun compute(now: ZonedDateTime, settings: Settings, stepsInSlot: Int, todayTotal: Int): DayStatus {
            val today = now.toLocalDate()
            val slotMinutes = settings.schedule.slotMinutes
            val plan = settings.schedule.planFor(today.dayOfWeek)
            val slot = Slots.at(settings.schedule, now)
            val kind = when {
                settings.isPaused(today) -> DayStatusKind.PAUSED
                plan == null -> DayStatusKind.DAY_OFF
                slot == null -> DayStatusKind.OUTSIDE_WINDOW
                else -> DayStatusKind.ACTIVE
            }
            val mode = plan?.let { CheckDecider.modeFor(settings, it, todayTotal) } ?: CheckMode.OFF
            val slotGoal = when {
                plan == null -> 0
                slot != null && mode == CheckMode.GOAL ->
                    GoalCalculator.slotGoalFromRemaining(plan.dailyGoal, slot, todayTotal - stepsInSlot)
                else -> GoalCalculator.slotGoal(plan, slotMinutes)
            }
            val paceTarget = if (kind == DayStatusKind.ACTIVE && mode == CheckMode.GOAL && plan != null && slot != null) {
                Pace.target(plan.dailyGoal, slot, Slots.minuteOfDay(now))
            } else {
                null
            }
            return DayStatus(
                kind = kind,
                today = today,
                slot = slot,
                stepsInSlot = stepsInSlot,
                todayTotal = todayTotal,
                dailyGoal = plan?.dailyGoal,
                mode = mode,
                slotTarget = if (mode == CheckMode.INACTIVITY) {
                    GoalCalculator.inactivityGoal(settings.inactivityThreshold, slot?.lengthMinutes ?: slotMinutes)
                } else {
                    slotGoal
                },
                paceTarget = paceTarget,
                nextCheck = ReminderSchedule.nextCheckTime(now, settings),
                pausedThrough = settings.pausedThrough,
            )
        }
    }
}
