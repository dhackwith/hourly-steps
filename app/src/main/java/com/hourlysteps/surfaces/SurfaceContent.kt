package com.hourlysteps.surfaces

import com.hourlysteps.domain.DayStatus
import com.hourlysteps.domain.DayStatusKind
import com.hourlysteps.domain.Settings
import com.hourlysteps.domain.SlotWording
import com.hourlysteps.ui.formatNextCheck
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
    val catchUpLine: String?,
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
        catchUpLine = null,
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
            catchUpLine = status.catchUpPerSlot?.let { "${formatSteps(it)}${SlotWording.perSlotShort(status.slotLength)} to catch up" },
        )
    }
}

/** Text and range for a watch-face complication. */
data class ComplicationContent(
    val value: Float,
    val max: Float,
    val shortText: String,
    val shortTitle: String?,
    val longText: String,
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
        max = status.slotTarget.coerceAtLeast(1).toFloat(),
        shortText = steps,
        shortTitle = "of $target",
        longText = "$steps of $target ${SlotWording.current(status.slotLength)}",
    )
}

/** Today's total against the daily goal. */
fun todayComplication(status: DayStatus): ComplicationContent {
    status.unavailableContent()?.let { return it }
    val goal = status.dailyGoal ?: return unavailable("Off")
    val total = formatSteps(status.todayTotal)
    return ComplicationContent(
        value = status.todayTotal.coerceAtMost(goal).toFloat(),
        max = goal.coerceAtLeast(1).toFloat(),
        shortText = total,
        shortTitle = "today",
        longText = "$total of ${formatSteps(goal)} today",
    )
}
