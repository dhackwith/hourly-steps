package com.hourlysteps.data.steps

import android.util.Log
import androidx.health.services.client.PassiveListenerService
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.UserActivityInfo
import androidx.health.services.client.data.UserActivityState
import com.hourlysteps.HourlyStepsApp
import com.hourlysteps.domain.UserActivity
import kotlinx.coroutines.runBlocking

private const val TAG = "StepsListener"

/** Bound by Health Services; receives batched STEPS interval points in the background. */
class StepsPassiveListenerService : PassiveListenerService() {

    override fun onNewDataPointsReceived(dataPoints: DataPointContainer) {
        val steps = dataPoints.getData(DataType.STEPS)
        if (steps.isEmpty()) return
        val container = (application as HourlyStepsApp).container
        // Short DB write; the service may be unbound as soon as this returns, so block here.
        runBlocking {
            runCatching {
                container.stepsRepository.record(steps)
                container.streakRepository.finalizePastDays()
                container.reminderDismissal.dismissIfSatisfied()
                container.goalWatcher.onStepsRecorded(hadNewSteps = steps.any { it.value > 0 })
                container.surfaceUpdater.onStepsChanged()
            }.onFailure { Log.e(TAG, "Failed to record steps", it) }
        }
    }

    override fun onUserActivityInfoReceived(info: UserActivityInfo) {
        val activity = when (info.userActivityState) {
            UserActivityState.USER_ACTIVITY_EXERCISE -> UserActivity.EXERCISE
            UserActivityState.USER_ACTIVITY_ASLEEP -> UserActivity.ASLEEP
            UserActivityState.USER_ACTIVITY_PASSIVE -> UserActivity.PASSIVE
            else -> UserActivity.UNKNOWN
        }
        Log.i(TAG, "Activity is now $activity, since ${info.stateChangeTime}")
        (application as HourlyStepsApp).container.activityStore.record(activity, info.stateChangeTime.toEpochMilli())
    }

    override fun onPermissionLost() {
        val container = (application as HourlyStepsApp).container
        Log.w(TAG, "Activity recognition permission lost; cancelling reminders")
        container.reminderScheduler.cancel()
        container.reminderNotifier.cancel()
    }
}
