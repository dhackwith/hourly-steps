package com.hourlysteps.data.steps

import android.util.Log
import androidx.health.services.client.PassiveListenerService
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import com.hourlysteps.HourlyStepsApp
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
                container.reminderNotifier.dismissIfSatisfied()
                container.celebrationManager.onStepsRecorded()
                container.surfaceUpdater.onStepsChanged()
            }.onFailure { Log.e(TAG, "Failed to record steps", it) }
        }
    }

    override fun onPermissionLost() {
        val container = (application as HourlyStepsApp).container
        Log.w(TAG, "Activity recognition permission lost; cancelling reminders")
        container.reminderScheduler.cancel()
        container.reminderNotifier.cancel()
    }
}
