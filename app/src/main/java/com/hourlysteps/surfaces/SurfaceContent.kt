package com.hourlysteps.surfaces

import com.hourlysteps.domain.DayStatus
import com.hourlysteps.domain.CheckMode
import com.hourlysteps.domain.DayStatusKind
import com.hourlysteps.domain.Settings
import com.hourlysteps.domain.SlotWording
import com.hourlysteps.ui.formatNextCheck
import com.hourlysteps.ui.formatPaceLine
import com.hourlysteps.ui.formatPauseEnd
import com.hourlysteps.ui.formatSteps

/** The two rings and their text, shared by the Home screen and the tile so they always match. */
data class RingContent(
    /** Outer ring: today's total against the daily goal. */
    val todayProgress: Float,
    /** Inner ring: this slot against its target. */
    val slotProgress: Float,
    val goalMet: Boolean,
    val title: String?,
    val value: String,
    val valueCaption: String?,
    /** True when the caption describes the slot, so it takes the inner ring's colour. */
    val captionIsSlot: Boolean,
    val todayLine: String?,
    val paceLine: String?,
    val detailLine: String?,
)

private val DayStatus.slotLength: Int get() = slot?.lengthMinutes ?: Settings.DEFAULT_SLOT_MINUTES

fun ringContent(status: DayStatus): RingContent {
    val total = formatSteps(status.todayTotal)
    val nextCheck = status.nextCheck?.let { "Next check ${formatNextCheck(it, status.today)}" }
    val base = RingContent(
        todayProgress = status.todayProgress,
        slotProgress = status.slotProgress,
        goalMet = status.dailyGoalMet,
        title = null,
        value = total,
        valueCaption = "steps today",
        captionIsSlot = false,
        todayLine = null,
        paceLine = null,
        detailLine = nextCheck,
    )
    return when {
        status.kind == DayStatusKind.PAUSED -> base.copy(
            goalMet = false,
            title = "Paused",
            detailLine = status.pausedThrough?.let { "until ${formatPauseEnd(it, status.today)}" },
        )
        status.kind == DayStatusKind.DAY_OFF -> base.copy(goalMet = false, title = "Day off")
        status.finished -> base.copy(title = "Goal met", valueCaption = "today", detailLine = null)
        status.kind == DayStatusKind.OUTSIDE_WINDOW -> base.copy(
            valueCaption = status.dailyGoal?.let { "of ${formatSteps(it)} today" },
        )
        else -> base.copy(
            title = if (status.dailyGoalMet) "Goal met · stay active" else null,
            value = formatSteps(status.stepsInSlot),
            valueCaption = "of ${formatSteps(status.slotTarget)} ${SlotWording.current(status.slotLength)}",
            captionIsSlot = true,
            todayLine = status.dailyGoal?.let { "Today $total / ${formatSteps(it)}" },
            paceLine = status.paceGap?.let(::formatPaceLine),
        )
    }
}

/** The Pace check reminder screen. */
data class PaceCheckContent(
    val left: String,
    val caption: String,
    val slotProgress: Float,
    val todayLine: String,
    val todayProgress: Float,
    /** Where pace falls along the today bar, or null once the daily goal is met. */
    val paceMarker: Float?,
    val paceLine: String,
    val goalMet: Boolean,
)

/** Null when there is nothing left to remind about, so an open reminder screen should close. */
fun paceCheckContent(status: DayStatus): PaceCheckContent? {
    val slot = status.slot ?: return null
    val dailyGoal = status.dailyGoal ?: return null
    val left = status.slotTarget - status.stepsInSlot
    if (status.kind != DayStatusKind.ACTIVE || status.mode == CheckMode.OFF || left <= 0) return null

    val goalMet = status.mode == CheckMode.INACTIVITY
    return PaceCheckContent(
        left = formatSteps(left),
        caption = "left ${SlotWording.current(slot.lengthMinutes)}",
        slotProgress = status.slotProgress,
        todayLine = "Today ${formatSteps(status.todayTotal)} of ${formatSteps(dailyGoal)}",
        todayProgress = status.todayProgress,
        paceMarker = status.paceTarget?.let { it.toFloat() / dailyGoal.coerceAtLeast(1) },
        paceLine = status.paceGap?.let(::formatPaceLine) ?: "Daily goal met",
        goalMet = goalMet,
    )
}

/** Text and range for a watch-face complication. */
data class ComplicationContent(
    val value: Float,
    val max: Float,
    val shortText: String,
    val shortTitle: String?,
    val longText: String,
    /**
     * Below zero by the steps past [max]. The system rejects a value above max, so this carries the
     * true count, value - min, for our watch face to add live steps to. A ring still draws full.
     */
    val min: Float = 0f,
)

private fun unavailable(text: String) = ComplicationContent(0f, 1f, text, null, text)

private fun DayStatus.unavailableContent(): ComplicationContent? = when (kind) {
    DayStatusKind.PAUSED -> unavailable("Paused")
    DayStatusKind.DAY_OFF -> unavailable("Off")
    else -> null
}

/** This slot's steps against its target. */
fun slotComplication(status: DayStatus): ComplicationContent {
    status.unavailableContent()?.let { return it }
    if (status.kind != DayStatusKind.ACTIVE) return unavailable("--")
    val steps = formatSteps(status.stepsInSlot)
    val target = formatSteps(status.slotTarget)
    return ComplicationContent(
        value = status.stepsInSlot.coerceAtMost(status.slotTarget).toFloat(),
        min = (status.slotTarget - status.stepsInSlot).coerceAtMost(0).toFloat(),
        max = status.slotTarget.coerceAtLeast(1).toFloat(),
        shortText = steps,
        shortTitle = "of $target",
        longText = "$steps of $target ${SlotWording.current(status.slotLength)}",
    )
}

/**
 * How many reminders today's plan has, as the value. A watch face that draws one segment per
 * reminder reads it from here because the slot target no longer divides the daily goal evenly.
 */
fun reminderCountComplication(status: DayStatus): ComplicationContent {
    val count = status.slotCount
    if (count == 0) return unavailable("Off")
    return ComplicationContent(
        value = count.toFloat(),
        max = count.toFloat(),
        shortText = count.toString(),
        shortTitle = if (count == 1) "reminder" else "reminders",
        longText = if (count == 1) "1 reminder today" else "$count reminders today",
    )
}

/** Today's total against the daily goal. */
fun todayComplication(status: DayStatus): ComplicationContent {
    status.unavailableContent()?.let { return it }
    val goal = status.dailyGoal ?: return unavailable("Off")
    val total = formatSteps(status.todayTotal)
    return ComplicationContent(
        value = status.todayTotal.coerceAtMost(goal).toFloat(),
        min = (goal - status.todayTotal).coerceAtMost(0).toFloat(),
        max = goal.coerceAtLeast(1).toFloat(),
        shortText = total,
        shortTitle = "today",
        longText = "$total of ${formatSteps(goal)} today",
    )
}
