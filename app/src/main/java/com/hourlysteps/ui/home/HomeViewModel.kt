package com.hourlysteps.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hourlysteps.AppContainer
import com.hourlysteps.domain.CheckDecider
import com.hourlysteps.domain.CheckMode
import com.hourlysteps.domain.GoalCalculator
import com.hourlysteps.domain.ReminderSchedule
import com.hourlysteps.domain.Settings
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

data class HomeUiState(
    val stepsThisHour: Int = 0,
    val todayTotal: Int = 0,
    val settings: Settings = Settings(),
    val mode: CheckMode = CheckMode.GOAL,
    /** Threshold the ring tracks: the hourly goal, or the inactivity threshold after the goal is met. */
    val ringTarget: Int = GoalCalculator.hourlyGoal(Settings()),
    val catchUpPerHour: Int? = null,
    val nextCheck: ZonedDateTime? = null,
    val inWindow: Boolean = false,
) {
    val dailyGoalMet: Boolean get() = todayTotal >= settings.dailyGoal
    val ringProgress: Float
        get() = if (ringTarget <= 0) 1f else (stepsThisHour.toFloat() / ringTarget).coerceIn(0f, 1f)
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val container: AppContainer) : ViewModel() {

    /** Ticks every 30 s, truncated to the minute so the hour bucket switches promptly at :00. */
    private val now = flow {
        while (true) {
            emit(ZonedDateTime.now(container.clock).truncatedTo(ChronoUnit.MINUTES))
            delay(30_000)
        }
    }.distinctUntilChanged()

    private val hourKey = now.map { it.truncatedTo(ChronoUnit.HOURS) }.distinctUntilChanged()

    private val steps = hourKey.flatMapLatest { hour ->
        combine(
            container.stepsRepository.observeStepsThisHour(hour),
            container.stepsRepository.observeTodayTotal(hour),
        ) { thisHour, total -> thisHour to total }
    }

    /**
     * Null until the first real values load. The Wear progress ring keeps its first progress
     * value and skips the first change after that, so it must not be composed with a placeholder.
     */
    val state: StateFlow<HomeUiState?> = combine(
        now,
        steps,
        container.settingsRepository.settings,
    ) { time, (thisHour, total), settings ->
        val mode = CheckDecider.modeFor(settings, total)
        val hourlyGoal = GoalCalculator.hourlyGoal(settings)
        val catchUp = if (mode == CheckMode.GOAL) {
            GoalCalculator.catchUpPerHour(settings, total, time.hour)?.takeIf { it > hourlyGoal }
        } else {
            null
        }
        HomeUiState(
            stepsThisHour = thisHour,
            todayTotal = total,
            settings = settings,
            mode = mode,
            ringTarget = if (mode == CheckMode.INACTIVITY) settings.inactivityThreshold else hourlyGoal,
            catchUpPerHour = catchUp,
            nextCheck = ReminderSchedule.nextCheckTime(time, settings),
            inWindow = time.hour in settings.firstHour..settings.lastHour,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
