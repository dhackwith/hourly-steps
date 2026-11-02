package com.hourlysteps.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** One interval of steps from Health Services. Summed over any slot length. */
@Entity(tableName = "step_points", indices = [Index("endMillis")])
data class StepPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startMillis: Long,
    val endMillis: Long,
    val steps: Int,
)

@Entity(tableName = "check_log")
data class CheckLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timeMillis: Long,
    val date: String,
    val slotStartMinute: Int,
    val stepsInSlot: Int,
    val todayTotal: Int,
    val mode: String,
    val need: Int,
    val catchUpPerSlot: Int?,
    val notified: Boolean,
    val skippedReason: String?,
)

@Dao
abstract class StepPointDao {

    @Insert
    abstract suspend fun insertAll(points: List<StepPointEntity>)

    /** An interval's end is exclusive, so one ending exactly on a boundary belongs to the earlier period. */
    @Query("SELECT COALESCE(SUM(steps), 0) FROM step_points WHERE endMillis > :fromMillis AND endMillis <= :toMillis")
    abstract suspend fun sumBetween(fromMillis: Long, toMillis: Long): Int

    @Query("SELECT COALESCE(SUM(steps), 0) FROM step_points WHERE endMillis > :fromMillis AND endMillis <= :toMillis")
    abstract fun observeSumBetween(fromMillis: Long, toMillis: Long): Flow<Int>

    @Query("SELECT * FROM step_points WHERE endMillis > :fromMillis AND endMillis <= :toMillis ORDER BY endMillis")
    abstract fun observeBetween(fromMillis: Long, toMillis: Long): Flow<List<StepPointEntity>>

    @Query("DELETE FROM step_points WHERE endMillis <= :beforeMillis")
    abstract suspend fun pruneBefore(beforeMillis: Long)
}

@Dao
abstract class CheckLogDao {

    @Insert
    abstract suspend fun insert(entity: CheckLogEntity)

    @Query("SELECT * FROM check_log ORDER BY timeMillis DESC LIMIT :limit")
    abstract fun observeRecent(limit: Int): Flow<List<CheckLogEntity>>

    @Query("SELECT * FROM check_log WHERE date = :date ORDER BY timeMillis")
    abstract fun observeForDate(date: String): Flow<List<CheckLogEntity>>

    @Query("DELETE FROM check_log WHERE date < :beforeDate")
    abstract suspend fun pruneBefore(beforeDate: String)
}

@Database(
    entities = [StepPointEntity::class, CheckLogEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stepPointDao(): StepPointDao
    abstract fun checkLogDao(): CheckLogDao
}
