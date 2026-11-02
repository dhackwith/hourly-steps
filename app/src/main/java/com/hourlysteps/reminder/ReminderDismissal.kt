package com.hourlysteps.reminder

import com.hourlysteps.domain.CheckDecider
import com.hourlysteps.domain.CheckResult
import com.hourlysteps.domain.SettingsSource
import com.hourlysteps.domain.Slots
import com.hourlysteps.domain.StepCounts
import java.time.Clock
import java.time.ZonedDateTime

/** Takes a reminder away once the steps that arrive after it make it no longer true. */
class ReminderDismissal(
    private val reminder: ReminderPresenter,
    private val settingsSource: SettingsSource,
    private val stepCounts: StepCounts,
    private val clock: Clock,
) {
    /** Called after new steps arrive: drop the reminder if this slot's threshold is now met. */
    suspend fun dismissIfSatisfied() {
        if (!reminder.isShowing()) return
        val now = ZonedDateTime.now(clock)
        val settings = settingsSource.current()
        val date = now.toLocalDate()
        val plan = settings.schedule.planFor(date.dayOfWeek)
        val slot = Slots.at(settings.schedule, now)
        if (plan == null || slot == null) {
            reminder.cancel()
            return
        }
        val result = CheckDecider.decide(
            settings = settings,
            plan = plan,
            slot = slot,
            stepsInSlot = stepCounts.stepsInSlot(slot),
            todayTotal = stepCounts.dayTotal(date),
        )
        if (result == CheckResult.None) reminder.cancel()
    }
}
