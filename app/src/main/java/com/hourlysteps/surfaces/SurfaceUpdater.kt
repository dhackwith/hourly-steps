package com.hourlysteps.surfaces

import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.hourlysteps.domain.SurfaceRefresher

private const val TAG = "SurfaceUpdater"
private const val MIN_INTERVAL_MS = 60_000L

/** Asks the system to refresh the tile and complications when their numbers may have changed. */
class SurfaceUpdater(private val context: Context) : SurfaceRefresher {

    private val throttle = RefreshThrottle(MIN_INTERVAL_MS)

    /** After a step batch. At most once a minute, to save battery while walking. */
    fun onStepsChanged() {
        if (throttle.allowBatch(SystemClock.elapsedRealtime())) update()
    }

    /** After something the wearer did or a check: they expect to see it right away. */
    override fun updateNow() {
        throttle.refreshed(SystemClock.elapsedRealtime())
        update()
    }

    /** The last slot's steps may still be on their way, and they change the new slot's numbers when they land. */
    override fun refreshForNewSlot() {
        update()
        throttle.openForNextBatch()
    }

    private fun update() {
        runCatching {
            TileService.getUpdater(context).requestUpdate(HourlyStepsTileService::class.java)
            listOf(
                SlotComplicationService::class.java,
                TodayComplicationService::class.java,
                ReminderCountComplicationService::class.java,
                StretchComplicationService::class.java,
            ).forEach {
                ComplicationDataSourceUpdateRequester.create(context, ComponentName(context, it)).requestUpdateAll()
            }
        }.onFailure { Log.w(TAG, "Could not request surface update", it) }
    }
}
