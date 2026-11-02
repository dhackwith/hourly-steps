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
import com.hourlysteps.data.db.HourlyStepsDao
import com.hourlysteps.domain.HourBucketing
import java.time.Clock
import java.time.Instant
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "StepsRepository"

/** Owns Health Services passive monitoring and the per-hour step store. */
class StepsRepository(
    private val context: Context,
    private val dao: HourlyStepsDao,
    private val clock: Clock,
) {
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
            .build()
        client.setPassiveListenerServiceAsync(StepsPassiveListenerService::class.java, config).await()
    }

    suspend fun stopPassiveMonitoring() {
        runCatching { client.clearPassiveListenerServiceAsync().await() }
            .onFailure { Log.w(TAG, "clearPassiveListenerService failed", it) }
    }

    /** Ask Health Services to deliver any batched data now. Bounded so a check can never hang. */
    suspend fun flush() {
        val done = withTimeoutOrNull(FLUSH_TIMEOUT_MS) {
            runCatching { client.flushAsync().await() }
                .onFailure { Log.w(TAG, "flush failed", it) }
        }
        if (done == null) Log.w(TAG, "flush timed out")
    }

    suspend fun record(points: List<IntervalDataPoint<Long>>) {
        if (points.isEmpty()) return
        val bootInstant = Instant.ofEpochMilli(System.currentTimeMillis() - SystemClock.elapsedRealtime())
        for (point in points) {
            val key = HourBucketing.keyForIntervalEnd(point.getEndInstant(bootInstant), clock.zone)
            dao.addSteps(key.dateKey, key.hour, point.value.toInt())
        }
    }

    suspend fun stepsThisHour(at: ZonedDateTime): Int {
        val key = HourBucketing.keyFor(at)
        return dao.stepsFor(key.dateKey, key.hour) ?: 0
    }

    suspend fun todayTotal(at: ZonedDateTime): Int = dao.totalFor(HourBucketing.keyFor(at).dateKey)

    fun observeStepsThisHour(at: ZonedDateTime): Flow<Int> {
        val key = HourBucketing.keyFor(at)
        return dao.observeStepsFor(key.dateKey, key.hour).map { it ?: 0 }
    }

    fun observeTodayTotal(at: ZonedDateTime): Flow<Int> =
        dao.observeTotalFor(HourBucketing.keyFor(at).dateKey)

    suspend fun pruneOlderThan(days: Long) {
        val cutoff = ZonedDateTime.now(clock).toLocalDate().minusDays(days).toString()
        dao.pruneBefore(cutoff)
    }

    private companion object {
        const val FLUSH_TIMEOUT_MS = 5_000L
    }
}
