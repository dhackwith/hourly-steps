package com.hourlysteps.reminder

import android.content.Context
import android.os.BatteryManager
import android.util.Log
import com.hourlysteps.data.db.CheckLogDao
import com.hourlysteps.data.db.CheckLogEntity
import com.hourlysteps.data.settings.SettingsRepository
import com.hourlysteps.data.steps.StepsRepository
import com.hourlysteps.domain.CheckDecider
import com.hourlysteps.domain.CheckResult
import com.hourlysteps.domain.HourBucketing
import java.time.Clock
import java.time.ZonedDateTime

private const val TAG = "ReminderCheck"

/** The :50 check itself: flush, read, decide, notify, log, re-arm. */
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
     * @param scheduledAt the slot this alarm was armed for, or null when fired manually.
     * @param force ignore the charging skip (debug / manual trigger).
     */
    suspend fun run(scheduledAt: ZonedDateTime?, force: Boolean = false): CheckResult {
        val now = ZonedDateTime.now(clock)
        val slot = scheduledAt ?: now
        val settings = settingsRepository.current()

        stepsRepository.flush()
        val stepsThisHour = stepsRepository.stepsThisHour(slot)
        val todayTotal = stepsRepository.todayTotal(slot)

        val skippedReason = when {
            !force && settings.skipWhileCharging && isCharging() -> "charging"
            else -> null
        }
        val result = if (skippedReason != null) {
            CheckResult.None
        } else {
            CheckDecider.decide(settings, stepsThisHour, todayTotal, slot.hour)
        }

        notifier.show(result, settings, now)
        Log.i(TAG, "Check at $slot: hour=$stepsThisHour total=$todayTotal -> $result skipped=$skippedReason")

        val key = HourBucketing.keyFor(slot)
        checkLogDao.insert(
            CheckLogEntity(
                timeMillis = now.toInstant().toEpochMilli(),
                date = key.dateKey,
                hour = key.hour,
                stepsThisHour = stepsThisHour,
                todayTotal = todayTotal,
                mode = CheckDecider.modeFor(settings, todayTotal).name,
                need = when (result) {
                    is CheckResult.GoalShortfall -> result.need
                    is CheckResult.InactivityShortfall -> result.need
                    CheckResult.None -> 0
                },
                catchUpPerHour = (result as? CheckResult.GoalShortfall)?.catchUpPerHour,
                notified = result != CheckResult.None,
                skippedReason = skippedReason,
            )
        )

        // Re-arm from whichever is later so an alarm that fires a hair early cannot double-fire.
        val after = if (scheduledAt != null && scheduledAt.isAfter(now)) scheduledAt else now
        scheduler.ensureScheduled(after)

        stepsRepository.pruneOlderThan(RETENTION_DAYS)
        checkLogDao.pruneBefore(now.toLocalDate().minusDays(RETENTION_DAYS).toString())
        return result
    }

    private fun isCharging(): Boolean =
        context.getSystemService(BatteryManager::class.java)?.isCharging == true

    private companion object {
        const val RETENTION_DAYS = 14L
    }
}
