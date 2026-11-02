package com.hourlysteps.reminder

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import com.hourlysteps.data.db.CheckLogDao
import com.hourlysteps.data.db.CheckLogEntity
import com.hourlysteps.data.settings.SettingsRepository
import com.hourlysteps.data.steps.StepsRepository
import com.hourlysteps.domain.CheckDecider
import com.hourlysteps.domain.CheckMode
import com.hourlysteps.domain.CheckResult
import com.hourlysteps.domain.ReminderSchedule
import com.hourlysteps.domain.Slots
import java.time.Clock
import java.time.ZonedDateTime

private const val TAG = "ReminderCheck"

/** The slot check itself: flush, read, decide, notify, log, re-arm. */
class ReminderCheck(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val stepsRepository: StepsRepository,
    private val notifier: ReminderNotifier,
    private val scheduler: ReminderScheduler,
    private val checkLogDao: CheckLogDao,
    private val clock: Clock,
) {

    /**
     * @param scheduledAt the check time this alarm was armed for, or null when fired manually.
     * @param force ignore the charging skip (debug / manual trigger).
     */
    suspend fun run(scheduledAt: ZonedDateTime?, force: Boolean = false): CheckResult {
        val now = ZonedDateTime.now(clock)
        val reference = scheduledAt ?: now
        val date = reference.toLocalDate()
        val settings = settingsRepository.current()

        stepsRepository.flush()
        val plan = settings.schedule.planFor(date.dayOfWeek)
        val slot = Slots.at(settings.schedule, reference)
        val stepsInSlot = slot?.let { stepsRepository.stepsInSlot(it) } ?: 0
        val todayTotal = stepsRepository.dayTotal(date)

        val skippedReason = when {
            settings.isPaused(date) -> "paused"
            plan == null -> "day off"
            slot == null -> "outside window"
            scheduledAt != null && ReminderSchedule.isLate(slot, now) -> "late"
            !force && settings.skipWhileCharging && isOnCharger() -> "charging"
            else -> null
        }
        val result = if (skippedReason != null || plan == null || slot == null) {
            CheckResult.None
        } else {
            CheckDecider.decide(settings, plan, slot, stepsInSlot, todayTotal)
        }

        notifier.show(result, settings, slot, now)
        Log.i(TAG, "Check at $reference: slot=$stepsInSlot total=$todayTotal -> $result skipped=$skippedReason")

        checkLogDao.insert(
            CheckLogEntity(
                timeMillis = now.toInstant().toEpochMilli(),
                date = date.toString(),
                slotStartMinute = slot?.startMinute ?: Slots.minuteOfDay(reference),
                stepsInSlot = stepsInSlot,
                todayTotal = todayTotal,
                mode = (plan?.let { CheckDecider.modeFor(settings, it, todayTotal) } ?: CheckMode.OFF).name,
                need = when (result) {
                    is CheckResult.GoalShortfall -> result.need
                    is CheckResult.InactivityShortfall -> result.need
                    CheckResult.None -> 0
                },
                catchUpPerSlot = (result as? CheckResult.GoalShortfall)?.catchUpPerSlot,
                notified = result != CheckResult.None,
                skippedReason = skippedReason,
            ),
        )

        // Re-arm from whichever is later so an alarm that fires a hair early cannot double-fire.
        val after = if (scheduledAt != null && scheduledAt.isAfter(now)) scheduledAt else now
        scheduler.ensureScheduled(after)

        stepsRepository.pruneOlderThan(RETENTION_DAYS)
        checkLogDao.pruneBefore(now.toLocalDate().minusDays(RETENTION_DAYS).toString())
        return result
    }

    /**
     * True when any charger is connected. BatteryManager.isCharging() is not enough: a full
     * battery, or one held at a charge limit, reports "not charging" while on the dock.
     */
    private fun isOnCharger(): Boolean {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
    }

    private companion object {
        const val RETENTION_DAYS = 14L
    }
}
