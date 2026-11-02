package com.hourlysteps.data.steps

import com.hourlysteps.data.settings.SettingsRepository
import com.hourlysteps.domain.DayStatus
import com.hourlysteps.domain.Slots
import java.time.Clock
import java.time.ZonedDateTime

/** Reads the current [DayStatus] for surfaces outside the app: the tile and complications. */
class DayStatusProvider(
    private val settingsRepository: SettingsRepository,
    private val stepsRepository: StepsRepository,
    private val clock: Clock,
) {
    suspend fun current(): DayStatus {
        val now = ZonedDateTime.now(clock)
        val settings = settingsRepository.current()
        val slot = Slots.at(settings.schedule, now)
        return DayStatus.compute(
            now = now,
            settings = settings,
            stepsInSlot = slot?.let { stepsRepository.stepsInSlot(it) } ?: 0,
            todayTotal = stepsRepository.dayTotal(now.toLocalDate()),
        )
    }
}
