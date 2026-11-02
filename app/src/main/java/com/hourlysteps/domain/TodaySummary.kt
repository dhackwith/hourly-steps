package com.hourlysteps.domain

import java.time.ZoneId
import java.time.ZonedDateTime

/** A step interval, identified by when it ended. */
data class StepPoint(val endMillis: Long, val steps: Int)

/** The outcome of one slot check, as logged. */
data class CheckRecord(
    val timeMillis: Long,
    val slotStartMinute: Int,
    val stepsInSlot: Int,
    val notified: Boolean,
    val skippedReason: String?,
    val isFollowUp: Boolean = false,
)

enum class SlotOutcome {
    /** Reached the slot goal. */
    HIT,

    /** Over, and short of the slot goal. */
    SHORT,

    /** Short of the slot goal, but no reminder could apply; see [SlotRow.reason]. */
    SKIPPED,

    /** The daily goal was already met when this slot began, so its goal no longer mattered. */
    GOAL_ALREADY_MET,

    /** Running now and not yet at its goal. */
    IN_PROGRESS,
    UPCOMING,
}

data class SlotRow(
    val slot: Slot,
    val steps: Int,
    val goal: Int,
    val outcome: SlotOutcome,
    val reason: String? = null,
)

/** Today slot by slot, for answering "how am I doing?" and "why didn't it buzz?". */
data class TodaySummary(
    val kind: DayStatusKind,
    val rows: List<SlotRow>,
    val todayTotal: Int,
    val dailyGoal: Int?,
    val lastCheck: CheckRecord?,
) {
    val slotsHit: Int get() = rows.count { it.outcome == SlotOutcome.HIT }
    val slotsTotal: Int get() = rows.size

    companion object {
        const val REASON_NOT_CHECKED = "no check"
        const val REASON_PAUSED = "paused"

        fun compute(
            now: ZonedDateTime,
            settings: Settings,
            points: List<StepPoint>,
            checks: List<CheckRecord>,
        ): TodaySummary {
            val today = now.toLocalDate()
            val zone = now.zone
            val slotMinutes = settings.schedule.slotMinutes
            val plan = settings.schedule.planFor(today.dayOfWeek)
            val paused = settings.isPaused(today)
            val total = points.sumOf { it.steps }
            val nowMillis = now.toInstant().toEpochMilli()
            val lastCheckBySlot = checks.filterNot { it.isFollowUp }.sortedBy { it.timeMillis }.associateBy { it.slotStartMinute }

            val current = Slots.at(settings.schedule, now)
            val rows = plan?.let { p ->
                val slots = Slots.forDay(p, slotMinutes, today)
                val upcomingGoal = upcomingGoal(p, slots.size, current, total, current?.let { stepsIn(it, points, zone) } ?: 0)
                slots.map { slot ->
                    val start = slot.startAt(zone).toInstant().toEpochMilli()
                    val end = slot.endAt(zone).toInstant().toEpochMilli()
                    val steps = stepsIn(slot, points, zone)
                    val totalAtStart = points.filter { it.endMillis <= start }.sumOf { it.steps }
                    val goal = if (nowMillis < start) upcomingGoal else GoalCalculator.slotGoalFromRemaining(p.dailyGoal, slot, totalAtStart)
                    val check = lastCheckBySlot[slot.startMinute]
                    when {
                        steps >= goal && totalAtStart < p.dailyGoal -> SlotRow(slot, steps, goal, SlotOutcome.HIT)
                        totalAtStart >= p.dailyGoal -> SlotRow(slot, steps, goal, SlotOutcome.GOAL_ALREADY_MET)
                        paused -> SlotRow(slot, steps, goal, SlotOutcome.SKIPPED, REASON_PAUSED)
                        nowMillis < start -> SlotRow(slot, steps, goal, SlotOutcome.UPCOMING)
                        nowMillis < end -> SlotRow(slot, steps, goal, SlotOutcome.IN_PROGRESS)
                        check?.skippedReason != null -> SlotRow(slot, steps, goal, SlotOutcome.SKIPPED, check.skippedReason)
                        check == null -> SlotRow(slot, steps, goal, SlotOutcome.SKIPPED, REASON_NOT_CHECKED)
                        else -> SlotRow(slot, steps, goal, SlotOutcome.SHORT)
                    }
                }
            }.orEmpty()

            return TodaySummary(
                kind = when {
                    paused -> DayStatusKind.PAUSED
                    plan == null -> DayStatusKind.DAY_OFF
                    current == null -> DayStatusKind.OUTSIDE_WINDOW
                    else -> DayStatusKind.ACTIVE
                },
                rows = rows,
                todayTotal = total,
                dailyGoal = plan?.dailyGoal,
                lastCheck = checks.maxByOrNull { it.timeMillis },
            )
        }

        // An interval's end is exclusive, so one ending on a boundary belongs to the earlier slot.
        private fun stepsIn(slot: Slot, points: List<StepPoint>, zone: ZoneId): Int {
            val start = slot.startAt(zone).toInstant().toEpochMilli()
            val end = slot.endAt(zone).toInstant().toEpochMilli()
            return points.filter { it.endMillis in (start + 1)..end }.sumOf { it.steps }
        }

        /**
         * A forecast for the slots still to come, assuming [current] at least reaches its goal.
         * They can only share out what's left evenly, so they all show the same figure.
         */
        private fun upcomingGoal(plan: DayPlan, slotCount: Int, current: Slot?, total: Int, stepsInCurrent: Int): Int {
            val expected = current?.let {
                val totalAtStart = total - stepsInCurrent
                maxOf(total, totalAtStart + GoalCalculator.slotGoalFromRemaining(plan.dailyGoal, it, totalAtStart))
            } ?: total
            val slotsLeft = current?.let { it.count - it.index - 1 } ?: slotCount
            return GoalCalculator.ceilDiv((plan.dailyGoal - expected).coerceAtLeast(0), slotsLeft.coerceAtLeast(1)).coerceAtLeast(1)
        }
    }
}
