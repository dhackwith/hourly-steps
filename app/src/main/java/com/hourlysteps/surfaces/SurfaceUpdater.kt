package com.hourlysteps.surfaces

import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.hourlysteps.domain.SurfaceCatchUpAlarm
import com.hourlysteps.domain.SurfaceRefresher

private const val TAG = "SurfaceUpdater"
private const val MIN_INTERVAL_MS = 60_000L

/** The complications whose numbers move with every step batch. */
private val STEP_SOURCES = listOf(SlotComplicationService::class.java, TodayComplicationService::class.java)

/** The complications that only change with the settings, the slot or the day. */
private val PLAN_SOURCES = listOf(ReminderCountComplicationService::class.java, StretchComplicationService::class.java)

/** Asks the system to refresh the tile and complications when their numbers may have changed. */
class SurfaceUpdater(private val context: Context, private val catchUp: SurfaceCatchUpAlarm) : SurfaceRefresher {

    private val throttle = RefreshThrottle(MIN_INTERVAL_MS)

    /**
     * After a step batch. At most once a minute, to save battery while walking. A batch that's held
     * back may be the last for a while, so it's caught up on once the minute is over.
     */
    fun onStepsChanged() {
        val now = SystemClock.elapsedRealtime()
        if (throttle.allowBatch(now)) update(STEP_SOURCES) else catchUp.scheduleSurfaceCatchUp(throttle.waitMs(now))
    }

    /** The catch-up for a batch that was held back. */
    fun onCatchUp() {
        throttle.refreshed(SystemClock.elapsedRealtime())
        update(STEP_SOURCES)
    }

    /** After something the wearer did or a check: they expect to see it right away. */
    override fun updateNow() {
        throttle.refreshed(SystemClock.elapsedRealtime())
        update(STEP_SOURCES + PLAN_SOURCES)
    }

    /** The last slot's steps may still be on their way, and they change the new slot's numbers when they land. */
    override fun refreshForNewSlot() {
        update(STEP_SOURCES + PLAN_SOURCES)
        throttle.openForNextBatch()
    }

    private fun update(sources: List<Class<out StepsComplicationService>>) {
        runCatching {
            TileService.getUpdater(context).requestUpdate(HourlyStepsTileService::class.java)
            sources.forEach {
                ComplicationDataSourceUpdateRequester.create(context, ComponentName(context, it)).requestUpdateAll()
            }
        }.onFailure { Log.w(TAG, "Could not request surface update", it) }
    }
}
