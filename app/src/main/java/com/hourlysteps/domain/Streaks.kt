package com.hourlysteps.domain

import java.time.LocalDate

enum class DayResult {
    MET,
    MISSED,

    /** A day off or a paused day: neither extends nor breaks a streak. */
    REST,
}

data class DayRecord(val date: LocalDate, val result: DayResult)

/** One day in the week strip. A null result means today in progress, or before any history. */
data class StreakDay(val date: LocalDate, val result: DayResult?, val isToday: Boolean)

data class StreakInfo(
    val current: Int,
    val longest: Int,
    /** The last seven days, oldest first, ending today. */
    val week: List<StreakDay>,
    /** Null while today's goal is still open. */
    val todayResult: DayResult?,
)

object Streaks {

    /** How a finished day counts. */
    fun finalResult(settings: Settings, date: LocalDate, total: Int): DayResult {
        val plan = settings.schedule.planFor(date.dayOfWeek)
        return when {
            plan == null || settings.isPaused(date) -> DayResult.REST
            total >= plan.dailyGoal -> DayResult.MET
            else -> DayResult.MISSED
        }
    }

    /** How today counts so far. Not having met the goal yet isn't a miss until the day is over. */
    fun todayResult(settings: Settings, date: LocalDate, total: Int): DayResult? =
        finalResult(settings, date, total).takeIf { it != DayResult.MISSED }

    /**
     * The current streak is the run of met days ending today, or yesterday while today is open.
     * Rest days are skipped over; a missed day ends it.
     *
     * @param records finished days, in any order. Records dated today or later are ignored.
     */
    fun compute(records: List<DayRecord>, today: LocalDate, todayResult: DayResult?): StreakInfo {
        val past = records.filter { it.date.isBefore(today) }.sortedBy { it.date }
        val results = past.map { it.result } + listOfNotNull(todayResult)

        var running = 0
        var longest = 0
        for (result in results) {
            when (result) {
                DayResult.MET -> running++
                DayResult.MISSED -> running = 0
                DayResult.REST -> Unit
            }
            longest = maxOf(longest, running)
        }

        val byDate = past.associate { it.date to it.result }
        val week = (6L downTo 0L).map { back ->
            val date = today.minusDays(back)
            StreakDay(date, if (back == 0L) todayResult else byDate[date], isToday = back == 0L)
        }
        return StreakInfo(current = running, longest = longest, week = week, todayResult = todayResult)
    }

    /** The first date that still needs recording, or null if nothing does. */
    fun firstUnrecordedDay(lastRecorded: LocalDate?, earliestStepDay: LocalDate?, today: LocalDate): LocalDate? {
        val start = lastRecorded?.plusDays(1) ?: earliestStepDay ?: return null
        return start.takeIf { it.isBefore(today) }
    }

    /** "3 days in a row", or null when there's no streak to mention. */
    fun line(current: Int): String? = when {
        current <= 0 -> null
        current == 1 -> "Streak started"
        else -> "$current days in a row"
    }
}
