package com.hourlysteps.domain

import java.time.LocalDate

// What background work needs from the rest of the app, kept small so it can be tested without a watch.

interface SettingsSource {
    suspend fun current(): Settings
}

interface SettingsEditor {
    suspend fun update(transform: (Settings) -> Settings)
}

interface StepMonitoring {
    suspend fun startPassiveMonitoring()
}

interface StepCounts {
    /** Asks the watch to hand over any steps it's still holding. */
    suspend fun flush()
    /** Flushes, then waits a few seconds for what it hands over to be recorded. Returns early if nothing comes. */
    suspend fun flushAndAwaitBatch()
    suspend fun stepsInSlot(slot: Slot): Int
    suspend fun dayTotal(date: LocalDate): Int
    suspend fun pruneOlderThan(days: Long)
}

interface ActivitySource {
    /** What to believe the wearer is doing at [nowMillis]. */
    fun currentAt(nowMillis: Long): UserActivity
}

interface StreakTracker {
    suspend fun finalizePastDays()
    suspend fun current(): StreakInfo
}

/** Refreshes what's shown outside the app: the tile and complications. */
interface SurfaceRefresher {
    fun updateNow()
    /** Refreshes for a slot that just started, and lets the next step batch refresh again however soon it comes. */
    fun refreshForNewSlot()
}

interface SlotChangeAlarm {
    /** Arms the refresh at the start of the next slot or the next day, whichever comes first. */
    suspend fun scheduleSlotChange()
}

interface SurfaceCatchUpAlarm {
    /** Arms a refresh for the first time the watch is awake after [delayMs], replacing any already armed. */
    fun scheduleSurfaceCatchUp(delayMs: Long)
}

interface StepCheckAlarm {
    /** Arms a look at the latest steps about a minute from now, replacing any already armed. */
    fun scheduleStepCheck()
}
