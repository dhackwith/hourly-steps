package com.hourlysteps.data.steps

import com.hourlysteps.data.db.CheckLogDao
import com.hourlysteps.domain.CheckRecord
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CheckHistory(private val dao: CheckLogDao) {

    /** Today's check outcomes, for the Today page. */
    fun observeDay(date: LocalDate): Flow<List<CheckRecord>> =
        dao.observeForDate(date.toString()).map { rows ->
            rows.map { CheckRecord(it.timeMillis, it.slotStartMinute, it.stepsInSlot, it.notified, it.skippedReason, it.isFollowUp) }
        }
}
