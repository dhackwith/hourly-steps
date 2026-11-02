package com.hourlysteps.data.steps

import android.util.Log
import com.hourlysteps.data.db.DaySummaryDao
import com.hourlysteps.data.db.DaySummaryEntity
import com.hourlysteps.data.settings.SettingsRepository
import com.hourlysteps.domain.DayRecord
import com.hourlysteps.domain.DayResult
import com.hourlysteps.domain.StreakInfo
import com.hourlysteps.domain.StreakTracker
import com.hourlysteps.domain.Streaks
import java.time.Clock
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

private const val TAG = "StreakRepository"

/**
 * Records how each finished day went, and works out the streak.
 *
 * Days are recorded the next time anything runs, rather than by a midnight alarm: the first step
 * batch, check or screen of a new day. That also recovers by itself if the watch was off. Records
 * are kept indefinitely, since steps are only kept for two weeks and a year of them is tiny.
 */
class StreakRepository(
    private val dao: DaySummaryDao,
    private val stepsRepository: StepsRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
) : StreakTracker {

    override suspend fun finalizePastDays() {
        val today = ZonedDateTime.now(clock).toLocalDate()
        val start = Streaks.firstUnrecordedDay(
            lastRecorded = dao.latestDate()?.let(LocalDate::parse),
            earliestStepDay = stepsRepository.earliestStepDay(),
            today = today,
        ) ?: return

        val settings = settingsRepository.current()
        var date = start
        while (date.isBefore(today)) {
            val total = stepsRepository.dayTotal(date)
            val result = Streaks.finalResult(settings, date, total)
            dao.insertIgnore(DaySummaryEntity(date.toString(), total, settings.schedule.planFor(date.dayOfWeek)?.dailyGoal, result.name))
            Log.i(TAG, "Recorded $date: $total steps, $result")
            date = date.plusDays(1)
        }
    }

    /** The streak right now. Call [finalizePastDays] first if a new day may have started. */
    override suspend fun current(): StreakInfo {
        val today = ZonedDateTime.now(clock).toLocalDate()
        val todayResult = Streaks.todayResult(settingsRepository.current(), today, stepsRepository.dayTotal(today))
        return Streaks.compute(dao.all().map { it.toRecord() }, today, todayResult)
    }

    fun observe(today: LocalDate): Flow<StreakInfo> = flow {
        finalizePastDays()
        emitAll(
            combine(
                dao.observeAll().map { rows -> rows.map { it.toRecord() } },
                settingsRepository.settings,
                stepsRepository.observeDayTotal(today),
            ) { records, settings, total ->
                Streaks.compute(records, today, Streaks.todayResult(settings, today, total))
            },
        )
    }

    /** Debug builds only: replace the history with [results], oldest first, ending yesterday. */
    suspend fun replaceHistoryForTesting(results: List<DayResult>) {
        val today = ZonedDateTime.now(clock).toLocalDate()
        dao.deleteAll()
        results.forEachIndexed { index, result ->
            val date = today.minusDays((results.size - index).toLong())
            dao.insertIgnore(DaySummaryEntity(date.toString(), 0, null, result.name))
        }
    }

    private fun DaySummaryEntity.toRecord() =
        DayRecord(LocalDate.parse(date), runCatching { DayResult.valueOf(result) }.getOrDefault(DayResult.MISSED))
}
