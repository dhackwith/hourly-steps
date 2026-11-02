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

    @Volatile
    private var lastUpdate = 0L

    /** After a step batch. At most once a minute, to save battery while walking. */
    fun onStepsChanged() {
        val now = SystemClock.elapsedRealtime()
        if (lastUpdate != 0L && now - lastUpdate < MIN_INTERVAL_MS) return
        update(now)
    }

    /** After something the wearer did or a check: they expect to see it right away. */
    override fun updateNow() = update(SystemClock.elapsedRealtime())

    private fun update(now: Long) {
        lastUpdate = now
        runCatching {
            TileService.getUpdater(context).requestUpdate(HourlyStepsTileService::class.java)
            listOf(
                SlotComplicationService::class.java,
                TodayComplicationService::class.java,
                ReminderCountComplicationService::class.java,
            ).forEach {
                ComplicationDataSourceUpdateRequester.create(context, ComponentName(context, it)).requestUpdateAll()
            }
        }.onFailure { Log.w(TAG, "Could not request surface update", it) }
    }
}
