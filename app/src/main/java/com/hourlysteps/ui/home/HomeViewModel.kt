package com.hourlysteps.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hourlysteps.AppContainer
import com.hourlysteps.domain.CheckDecider
import com.hourlysteps.domain.CheckMode
import com.hourlysteps.domain.GoalCalculator
import com.hourlysteps.domain.ReminderSchedule
import com.hourlysteps.domain.Settings
import com.hourlysteps.domain.Slot
import com.hourlysteps.domain.Slots
import java.time.LocalDate
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class HomeStatus {
    /** Inside a slot: the ring tracks it. */
    ACTIVE,

    /** A plan applies today, but the time is before or after its window. */
    OUTSIDE_WINDOW,
    DAY_OFF,
    PAUSED,
}

data class HomeUiState(
    val status: HomeStatus,
    val today: LocalDate,
    val stepsInSlot: Int,
    val todayTotal: Int,
    /** Today's daily goal, or null on a day off. */
    val dailyGoal: Int?,
    val mode: CheckMode,
    /** Threshold the ring tracks: the slot goal, or the inactivity threshold after the goal is met. */
    val ringTarget: Int,
    val catchUpPerSlot: Int?,
    val nextCheck: ZonedDateTime?,
    val pausedThrough: LocalDate?,
) {
    val dailyGoalMet: Boolean get() = dailyGoal != null && todayTotal >= dailyGoal
    val ringProgress: Float
        get() = if (ringTarget <= 0) 0f else (stepsInSlot.toFloat() / ringTarget).coerceIn(0f, 1f)
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val container: AppContainer) : ViewModel() {

    /** Ticks every 30 s, truncated to the minute so the slot switches promptly at its boundary. */
    private val now = flow {
        while (true) {
            emit(ZonedDateTime.now(container.clock).truncatedTo(ChronoUnit.MINUTES))
            delay(30_000)
        }
    }.distinctUntilChanged()

    private val steps = combine(now, container.settingsRepository.settings) { time, settings ->
        time.toLocalDate() to Slots.at(settings.schedule, time)
    }.distinctUntilChanged().flatMapLatest { (date, slot) ->
        combine(
            container.stepsRepository.observeStepsInSlot(slot),
            container.stepsRepository.observeDayTotal(date),
        ) { inSlot, total -> inSlot to total }
    }

    /**
     * Null until the first real values load, so the screen never flashes placeholder zeros.
     */
    val state: StateFlow<HomeUiState?> = combine(
        now,
        steps,
        container.settingsRepository.settings,
    ) { time, (inSlot, total), settings ->
        buildState(time, inSlot, total, settings, Slots.at(settings.schedule, time))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun buildState(
        time: ZonedDateTime,
        inSlot: Int,
        total: Int,
        settings: Settings,
        slot: Slot?,
    ): HomeUiState {
        val today = time.toLocalDate()
        val slotMinutes = settings.schedule.slotMinutes
        val plan = settings.schedule.planFor(today.dayOfWeek)
        val status = when {
            settings.isPaused(today) -> HomeStatus.PAUSED
            plan == null -> HomeStatus.DAY_OFF
            slot == null -> HomeStatus.OUTSIDE_WINDOW
            else -> HomeStatus.ACTIVE
        }
        val mode = plan?.let { CheckDecider.modeFor(settings, it, total) } ?: CheckMode.OFF
        val slotGoal = plan?.let { GoalCalculator.slotGoal(it, slotMinutes) } ?: 0
        val catchUp = if (status == HomeStatus.ACTIVE && mode == CheckMode.GOAL && plan != null) {
            GoalCalculator.catchUpPerSlot(plan, slotMinutes, total, Slots.minuteOfDay(time))?.takeIf { it > slotGoal }
        } else {
            null
        }
        return HomeUiState(
            status = status,
            today = today,
            stepsInSlot = inSlot,
            todayTotal = total,
            dailyGoal = plan?.dailyGoal,
            mode = mode,
            ringTarget = if (mode == CheckMode.INACTIVITY) settings.inactivityThreshold else slotGoal,
            catchUpPerSlot = catchUp,
            nextCheck = ReminderSchedule.nextCheckTime(time, settings),
            pausedThrough = settings.pausedThrough,
        )
    }

    fun resume() {
        container.appScope.launch { container.setPausedThrough(null) }
    }
}
