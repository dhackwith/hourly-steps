package com.hourlysteps.data.steps

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.concurrent.futures.await
import androidx.health.services.client.HealthServices
import androidx.health.services.client.PassiveMonitoringClient
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.IntervalDataPoint
import androidx.health.services.client.data.PassiveListenerConfig
import com.hourlysteps.data.db.StepPointDao
import com.hourlysteps.data.db.StepPointEntity
import com.hourlysteps.domain.Slot
import com.hourlysteps.domain.StepCounts
import com.hourlysteps.domain.StepPoint
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "StepsRepository"

/** Owns Health Services passive monitoring and the store of raw step points. */
class StepsRepository(
    private val context: Context,
    private val dao: StepPointDao,
    private val clock: Clock,
) : StepCounts {
    private val client: PassiveMonitoringClient
        get() = HealthServices.getClient(context).passiveMonitoringClient

    suspend fun supportsSteps(): Boolean = runCatching {
        client.getCapabilitiesAsync().await()
            .supportedDataTypesPassiveMonitoring
            .contains(DataType.STEPS)
    }.getOrElse { e ->
        Log.w(TAG, "Capability query failed", e)
        false
    }

    /** Idempotent: Health Services replaces any earlier registration from this app. */
    suspend fun startPassiveMonitoring() {
        val config = PassiveListenerConfig.builder()
            .setDataTypes(setOf(DataType.STEPS))
            .setShouldUserActivityInfoBeRequested(true)
            .build()
        client.setPassiveListenerServiceAsync(StepsPassiveListenerService::class.java, config).await()
    }

    suspend fun stopPassiveMonitoring() {
        runCatching { client.clearPassiveListenerServiceAsync().await() }
            .onFailure { Log.w(TAG, "clearPassiveListenerService failed", it) }
    }

    /** Ask Health Services to deliver any batched data now. Bounded so a check can never hang. */
    override suspend fun flush() {
        val done = withTimeoutOrNull(FLUSH_TIMEOUT_MS) {
            runCatching { client.flushAsync().await() }
                .onFailure { Log.w(TAG, "flush failed", it) }
        }
        if (done == null) Log.w(TAG, "flush timed out")
    }

    suspend fun record(points: List<IntervalDataPoint<Long>>) {
        val bootInstant = Instant.ofEpochMilli(System.currentTimeMillis() - SystemClock.elapsedRealtime())
        val entities = points.filter { it.value > 0 }.map { point ->
            StepPointEntity(
                startMillis = point.getStartInstant(bootInstant).toEpochMilli(),
                endMillis = point.getEndInstant(bootInstant).toEpochMilli(),
                steps = point.value.toInt(),
            )
        }
        if (entities.isNotEmpty()) dao.insertAll(entities)
    }

    override suspend fun stepsInSlot(slot: Slot): Int =
        dao.sumBetween(millis(slot.startAt(clock.zone)), millis(slot.endAt(clock.zone)))

    override suspend fun dayTotal(date: LocalDate): Int =
        dao.sumBetween(dayStart(date), dayStart(date.plusDays(1)))

    fun observeStepsInSlot(slot: Slot?): Flow<Int> =
        if (slot == null) {
            flowOf(0)
        } else {
            dao.observeSumBetween(millis(slot.startAt(clock.zone)), millis(slot.endAt(clock.zone)))
        }

    fun observeDayTotal(date: LocalDate): Flow<Int> =
        dao.observeSumBetween(dayStart(date), dayStart(date.plusDays(1)))

    fun observeDayPoints(date: LocalDate): Flow<List<StepPoint>> =
        dao.observeBetween(dayStart(date), dayStart(date.plusDays(1)))
            .map { rows -> rows.map { StepPoint(it.endMillis, it.steps) } }

    /** The day of the oldest step data kept, or null if there is none. */
    suspend fun earliestStepDay(): LocalDate? =
        dao.earliestEndMillis()?.let { Instant.ofEpochMilli(it).atZone(clock.zone).toLocalDate() }

    override suspend fun pruneOlderThan(days: Long) {
        dao.pruneBefore(dayStart(ZonedDateTime.now(clock).toLocalDate().minusDays(days)))
    }

    private fun dayStart(date: LocalDate): Long = millis(date.atStartOfDay(clock.zone))

    private fun millis(time: ZonedDateTime): Long = time.toInstant().toEpochMilli()

    private companion object {
        const val FLUSH_TIMEOUT_MS = 5_000L
    }
}
