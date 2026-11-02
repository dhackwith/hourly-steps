package com.hourlysteps.reminder

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import com.hourlysteps.data.db.CheckLogDao
import com.hourlysteps.data.db.CheckLogEntity
import com.hourlysteps.data.settings.SettingsRepository
import com.hourlysteps.data.steps.ActivityStore
import com.hourlysteps.data.steps.StepsRepository
import com.hourlysteps.domain.CheckDecider
import com.hourlysteps.domain.CheckMode
import com.hourlysteps.domain.CheckResult
import com.hourlysteps.domain.CheckSkip
import com.hourlysteps.domain.ReminderSchedule
import com.hourlysteps.domain.Settings
import com.hourlysteps.domain.Slot
import com.hourlysteps.domain.Slots
import com.hourlysteps.surfaces.SurfaceUpdater
import java.time.Clock
import java.time.ZonedDateTime

private const val TAG = "ReminderCheck"

/** The slot check and its follow-ups: flush, read, decide, notify, log, re-arm. */
class ReminderCheck(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val stepsRepository: StepsRepository,
    private val activityStore: ActivityStore,
    private val notifier: ReminderNotifier,
    private val scheduler: ReminderScheduler,
    private val checkLogDao: CheckLogDao,
    private val surfaceUpdater: SurfaceUpdater,
    private val clock: Clock,
) {

    private class Outcome(val result: CheckResult, val settings: Settings, val slot: Slot?)

    /**
     * The main check for a slot.
     *
     * @param scheduledAt the check time this alarm was armed for, or null when fired manually.
     * @param force ignore the wearer's circumstances, such as charging (debug / manual trigger).
     */
    suspend fun run(scheduledAt: ZonedDateTime?, force: Boolean = false): CheckResult {
        val now = ZonedDateTime.now(clock)
        val outcome = evaluate(scheduledAt, now, force, isFollowUp = false)

        if (outcome.result != CheckResult.None && outcome.settings.secondNudge && outcome.slot != null) {
            val at = outcome.slot.secondNudgeAt(now.zone)
            if (at.isAfter(now)) scheduler.scheduleFollowUp(at, FollowUpKind.SECOND_NUDGE)
        }

        // Re-arm from whichever is later so an alarm that fires a hair early cannot double-fire.
        val after = if (scheduledAt != null && scheduledAt.isAfter(now)) scheduledAt else now
        scheduler.ensureScheduled(after)
        surfaceUpdater.updateNow()

        stepsRepository.pruneOlderThan(RETENTION_DAYS)
        checkLogDao.pruneBefore(now.toLocalDate().minusDays(RETENTION_DAYS).toString())
        return outcome.result
    }

    /** A second nudge or a snoozed reminder coming back: remind again if still short. */
    suspend fun runFollowUp(scheduledAt: ZonedDateTime, kind: FollowUpKind) {
        // The setting may have been turned off after this was armed. A snooze was asked for directly.
        if (kind == FollowUpKind.SECOND_NUDGE && !settingsRepository.current().secondNudge) return
        evaluate(scheduledAt, ZonedDateTime.now(clock), force = false, isFollowUp = true)
        surfaceUpdater.updateNow()
    }

    /** Clears the reminder and brings it back in a few minutes, if the slot lasts that long. */
    suspend fun snooze() {
        notifier.cancel()
        val now = ZonedDateTime.now(clock)
        val slot = Slots.at(settingsRepository.current().schedule, now) ?: return
        val until = ReminderSchedule.snoozeUntil(slot, now)
        Log.i(TAG, "Snoozed until $until")
        until?.let { scheduler.scheduleFollowUp(it, FollowUpKind.SNOOZE) }
    }

    private suspend fun evaluate(
        scheduledAt: ZonedDateTime?,
        now: ZonedDateTime,
        force: Boolean,
        isFollowUp: Boolean,
    ): Outcome {
        val reference = scheduledAt ?: now
        val date = reference.toLocalDate()
        val settings = settingsRepository.current()

        stepsRepository.flush()
        val plan = settings.schedule.planFor(date.dayOfWeek)
        val slot = Slots.at(settings.schedule, reference)
        val stepsInSlot = slot?.let { stepsRepository.stepsInSlot(it) } ?: 0
        val todayTotal = stepsRepository.dayTotal(date)

        val skippedReason = CheckSkip.reason(
            settings = settings,
            reference = reference,
            now = now,
            onCharger = isOnCharger(),
            activity = activityStore.currentAt(now.toInstant().toEpochMilli()),
            lateSensitive = scheduledAt != null,
            force = force,
        )
        val result = if (skippedReason != null || plan == null || slot == null) {
            CheckResult.None
        } else {
            CheckDecider.decide(settings, plan, slot, stepsInSlot, todayTotal)
        }

        if (isFollowUp && result == CheckResult.None) notifier.cancel()
        notifier.show(result, settings, slot, now)
        val label = if (isFollowUp) "Follow-up" else "Check"
        Log.i(TAG, "$label at $reference: slot=$stepsInSlot total=$todayTotal -> $result skipped=$skippedReason")

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
                isFollowUp = isFollowUp,
            ),
        )
        return Outcome(result, settings, slot)
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
