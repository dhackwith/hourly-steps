package com.hourlysteps.reminder

import android.util.Log
import com.hourlysteps.domain.AppTheme
import com.hourlysteps.domain.SettingsEditor
import com.hourlysteps.domain.StepMonitoring
import com.hourlysteps.domain.SurfaceRefresher
import java.time.Clock
import java.time.LocalDate
import java.time.ZonedDateTime

private const val TAG = "ReminderControls"

/** The changes that keep reminders in step: starting them up, pausing, and settings that affect them. */
class ReminderControls(
    private val settingsEditor: SettingsEditor,
    private val stepMonitoring: StepMonitoring,
    private val notifier: ReminderPresenter,
    private val arming: ReminderArming,
    private val surfaces: SurfaceRefresher,
    private val hasActivityRecognition: () -> Boolean,
    private val clock: Clock,
) {
    /**
     * Idempotent "make sure everything is running": registers passive monitoring and arms the
     * next alarm when the required permission is present. Called on app foreground, reboot,
     * app update, clock changes, and after settings edits.
     */
    suspend fun ensureArmed() {
        surfaces.updateNow()
        if (!hasActivityRecognition()) {
            Log.i(TAG, "ACTIVITY_RECOGNITION not granted; reminders stay disarmed")
            arming.cancel()
            return
        }
        runCatching { stepMonitoring.startPassiveMonitoring() }
            .onFailure { Log.e(TAG, "Passive monitoring registration failed", it) }
        arming.ensureScheduled()
    }

    /** Pauses through [through] inclusive; a null date resumes. Clears any reminder on screen. */
    suspend fun setPausedThrough(through: LocalDate?) {
        settingsEditor.update { it.copy(pausedThrough = through) }
        if (through != null) {
            notifier.cancel()
            arming.cancelFollowUp()
        }
        ensureArmed()
    }

    /** Changes how often reminders come. A reminder or follow-up from the old interval no longer applies. */
    suspend fun setSlotMinutes(minutes: Int) {
        settingsEditor.update { it.copy(schedule = it.schedule.copy(slotMinutes = minutes)) }
        notifier.cancel()
        arming.cancelFollowUp()
        ensureArmed()
    }

    /** Changes the colours. The tile holds its own copy of them, so it needs telling. */
    suspend fun setTheme(theme: AppTheme) {
        settingsEditor.update { it.copy(theme = theme) }
        surfaces.updateNow()
    }

    /** The "Pause today" button on a reminder. */
    suspend fun pauseToday() = setPausedThrough(ZonedDateTime.now(clock).toLocalDate())
}
