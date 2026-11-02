package com.hourlysteps.reminder

import com.hourlysteps.domain.CheckResult
import com.hourlysteps.domain.DayPlan
import com.hourlysteps.domain.Settings
import com.hourlysteps.domain.Slot
import java.time.ZonedDateTime

interface ReminderAlarms {
    /** Arms the next check strictly after [after], and returns when it is, or null if none is coming. */
    suspend fun ensureScheduled(after: ZonedDateTime): ZonedDateTime?
    fun scheduleFollowUp(at: ZonedDateTime, kind: FollowUpKind)
}

interface ReminderPresenter {
    suspend fun show(result: CheckResult, settings: Settings, plan: DayPlan?, slot: Slot?, now: ZonedDateTime, todayTotal: Int)
    fun cancel()
    fun isShowing(): Boolean
}
