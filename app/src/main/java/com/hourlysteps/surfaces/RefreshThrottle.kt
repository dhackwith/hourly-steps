package com.hourlysteps.surfaces

/** Lets step batches refresh the surfaces at most once per [minIntervalMs], unless it's been opened for the next one. */
class RefreshThrottle(private val minIntervalMs: Long) {

    @Volatile
    private var lastRefresh: Long? = null

    /** Whether a batch at [now] may refresh. If it may, that counts as a refresh. */
    fun allowBatch(now: Long): Boolean {
        val last = lastRefresh
        if (last != null && now - last < minIntervalMs) return false
        lastRefresh = now
        return true
    }

    /** Something else refreshed at [now]. */
    fun refreshed(now: Long) {
        lastRefresh = now
    }

    /** The next batch refreshes however soon it comes. */
    fun openForNextBatch() {
        lastRefresh = null
    }
}
