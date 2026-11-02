package com.hourlysteps.celebration

import android.util.Log
import com.hourlysteps.domain.ActivitySource
import com.hourlysteps.domain.Celebration
import com.hourlysteps.domain.CelebrationDecider
import com.hourlysteps.domain.SettingsSource
import com.hourlysteps.domain.Slots
import com.hourlysteps.domain.StepCheckAlarm
import com.hourlysteps.domain.StepCounts
import com.hourlysteps.domain.StreakTracker
import com.hourlysteps.domain.UserActivity
import com.hourlysteps.reminder.Haptics
import java.time.Clock
import java.time.ZonedDateTime

private const val TAG = "CelebrationManager"

/**
 * Detects the moment a slot's goal or the daily goal is reached and celebrates it once.
 *
 * A screen opened from the background is gone in seconds and may go unseen, so it leaves a silent
 * notification behind.
 */
class CelebrationManager(
    private val surface: CelebrationSurface,
    private val memory: CelebrationMemory,
    private val stepCheckAlarm: StepCheckAlarm,
    private val settingsSource: SettingsSource,
    private val stepCounts: StepCounts,
    private val activitySource: ActivitySource,
    private val streakTracker: StreakTracker,
    private val clock: Clock,
) {
    /**
     * Called after each batch of new step data. Close to a goal it also arranges to ask for steps
     * again soon, because batches can arrive minutes apart. A look that finds no new steps ends
     * that, so standing still near a goal costs nothing.
     */
    suspend fun onStepsRecorded(hadNewSteps: Boolean = true) {
        val now = ZonedDateTime.now(clock)
        val date = now.toLocalDate()
        val settings = settingsSource.current()
        if (settings.isPaused(date)) return
        // Celebrate during a workout, but never wake someone up to do it.
        if (settings.skipWhileAsleep && activitySource.currentAt(now.toInstant().toEpochMilli()) == UserActivity.ASLEEP) return
        val plan = settings.schedule.planFor(date.dayOfWeek) ?: return
        val slot = Slots.at(settings.schedule, now)
        val total = stepCounts.dayTotal(date)
        val inSlot = slot?.let { stepCounts.stepsInSlot(it) } ?: 0

        val slotKey = slot?.let { "$date#${it.startMinute}" }
        val hourly = slot?.let {
            CelebrationDecider.decide(
                settings = settings,
                plan = plan,
                slot = it,
                stepsInSlot = inSlot,
                todayTotal = total,
                alreadyCelebratedThisSlot = memory.lastSlot() == slotKey,
            )
        }
        val daily = CelebrationDecider.decideDaily(
            settings = settings,
            plan = plan,
            stepsInSlot = inSlot,
            todayTotal = total,
            alreadyCelebratedToday = memory.lastDay() == date.toString(),
        )

        // When both goals fall in the same batch, the daily moment is the one to show.
        if (daily != null) {
            memory.remember(slot = if (hourly != null) slotKey else null, day = date.toString())
            streakTracker.finalizePastDays()
            celebrate(daily.copy(streak = streakTracker.current().current))
        } else if (hourly != null) {
            memory.remember(slot = slotKey)
            celebrate(hourly)
        }

        val left = CelebrationDecider.stepsToNext(
            settings = settings,
            plan = plan,
            slot = slot,
            stepsInSlot = inSlot,
            todayTotal = total,
            alreadyCelebratedThisSlot = memory.lastSlot() == slotKey,
            alreadyCelebratedToday = memory.lastDay() == date.toString(),
        )
        if (hadNewSteps && left != null && left <= CelebrationDecider.CLOSE_STEPS) {
            Log.i(TAG, "$left steps from a goal; asking for steps again soon")
            stepCheckAlarm.scheduleStepCheck()
        }
    }

    suspend fun celebrate(celebration: Celebration) {
        val inBackground = !surface.isUiVisible()
        Log.i(TAG, "Celebrating $celebration uiVisible=${!inBackground}")
        val strength = settingsSource.current().vibration
        surface.prepare(strength)
        val pattern = Haptics.celebrationPattern(strength)

        // A notification would not alert while the app is in the foreground on Wear OS anyway.
        val canOpen = !inBackground || surface.canOpenFromBackground()
        surface.wakeScreen()
        if (canOpen && surface.openScreen(celebration, pattern)) {
            if (inBackground) surface.postRecord(celebration)
            return
        }
        surface.postAlert(celebration, strength)
        surface.vibrate(pattern)
    }
}
