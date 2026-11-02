package com.hourlysteps.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hourlysteps.AppContainer
import com.hourlysteps.domain.DayStatus
import com.hourlysteps.domain.Slots
import com.hourlysteps.domain.StreakInfo
import com.hourlysteps.domain.TodaySummary
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val settings = container.settingsRepository.settings

    /** Ticks every 30 s, truncated to the minute so the slot switches promptly at its boundary. */
    private val now = flow {
        while (true) {
            emit(ZonedDateTime.now(container.clock).truncatedTo(ChronoUnit.MINUTES))
            delay(30_000)
        }
    }.distinctUntilChanged()

    private val steps = combine(now, settings) { time, s ->
        time.toLocalDate() to Slots.at(s.schedule, time)
    }.distinctUntilChanged().flatMapLatest { (date, slot) ->
        combine(
            container.stepsRepository.observeStepsInSlot(slot),
            container.stepsRepository.observeDayTotal(date),
        ) { inSlot, total -> inSlot to total }
    }

    /** Null until the first real values load, so the screen never flashes placeholder zeros. */
    val status: StateFlow<DayStatus?> = combine(now, steps, settings) { time, (inSlot, total), s ->
        DayStatus.compute(time, s, inSlot, total)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val day = now.map { it.toLocalDate() }.distinctUntilChanged()

    val today: StateFlow<TodaySummary?> = day.flatMapLatest { date ->
        combine(
            now,
            settings,
            container.stepsRepository.observeDayPoints(date),
            container.observeDayChecks(date),
        ) { time, s, points, checks -> TodaySummary.compute(time, s, points, checks) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val streak: StateFlow<StreakInfo?> = day.flatMapLatest { date ->
        container.streakRepository.observe(date)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun resume() {
        container.appScope.launch { container.setPausedThrough(null) }
    }
}
