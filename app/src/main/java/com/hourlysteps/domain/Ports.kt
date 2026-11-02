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
}

interface StepCheckAlarm {
    /** Arms a look at the latest steps about a minute from now, replacing any already armed. */
    fun scheduleStepCheck()
}
