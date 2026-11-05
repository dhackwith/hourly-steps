package com.hourlysteps.reminder

import android.util.Log
import com.hourlysteps.domain.SlotChangeAlarm
import com.hourlysteps.domain.StepCounts
import com.hourlysteps.domain.SurfaceRefresher
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "SlotChange"

/** The start of a slot: collect the last one's steps, then show the new slot and arm the next change. */
class SlotChange(
    private val stepCounts: StepCounts,
    private val surfaces: SurfaceRefresher,
    private val alarm: SlotChangeAlarm,
) {

    suspend fun run() {
        // Steps the watch is still holding belong to the slot that just ended. Shown before they're
        // recorded, a watch face counts them in the new slot.
        val collected = runCatching { withTimeoutOrNull(MAX_WAIT_MS) { stepCounts.flushAndAwaitBatch() } != null }
            .onFailure { Log.w(TAG, "Collecting the last slot's steps failed", it) }
            .getOrDefault(false)
        if (!collected) Log.w(TAG, "Refreshing without the last slot's latest steps")
        Log.i(TAG, "Slot changed; refreshing the tile and complications")
        surfaces.refreshForNewSlot()
        alarm.scheduleSlotChange()
    }

    private companion object {
        /** Well inside the receiver's wake lock and broadcast time limit. */
        const val MAX_WAIT_MS = 8_000L
    }
}
