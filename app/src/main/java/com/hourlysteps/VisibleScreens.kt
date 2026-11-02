package com.hourlysteps

import java.util.concurrent.atomic.AtomicInteger

/** Whether one of the app's screens is in the foreground. */
class VisibleScreens {

    private val started = AtomicInteger(0)

    val anyVisible: Boolean get() = started.get() > 0

    /** A count rather than a flag: one screen starts before the one beneath it stops. */
    fun onScreenStarted() {
        started.incrementAndGet()
    }

    fun onScreenStopped() {
        started.decrementAndGet()
    }
}
