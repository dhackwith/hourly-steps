package com.hourlysteps.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "hourly_steps", primaryKeys = ["date", "hour"])
data class HourlyStepsEntity(
    val date: String,
    val hour: Int,
    val steps: Int,
)

@Entity(tableName = "check_log")
data class CheckLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timeMillis: Long,
    val date: String,
    val hour: Int,
    val stepsThisHour: Int,
    val todayTotal: Int,
    val mode: String,
    val need: Int,
    val catchUpPerHour: Int?,
    val notified: Boolean,
    val skippedReason: String?,
)

@Dao
abstract class HourlyStepsDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIgnore(entity: HourlyStepsEntity)

    @Query("UPDATE hourly_steps SET steps = steps + :delta WHERE date = :date AND hour = :hour")
    abstract suspend fun increment(date: String, hour: Int, delta: Int)

    @Transaction
    open suspend fun addSteps(date: String, hour: Int, delta: Int) {
        if (delta <= 0) return
        insertIgnore(HourlyStepsEntity(date, hour, 0))
        increment(date, hour, delta)
    }

    @Query("SELECT steps FROM hourly_steps WHERE date = :date AND hour = :hour")
    abstract suspend fun stepsFor(date: String, hour: Int): Int?

    @Query("SELECT COALESCE(SUM(steps), 0) FROM hourly_steps WHERE date = :date")
    abstract suspend fun totalFor(date: String): Int

    @Query("SELECT steps FROM hourly_steps WHERE date = :date AND hour = :hour")
    abstract fun observeStepsFor(date: String, hour: Int): Flow<Int?>

    @Query("SELECT COALESCE(SUM(steps), 0) FROM hourly_steps WHERE date = :date")
    abstract fun observeTotalFor(date: String): Flow<Int>

    @Query("DELETE FROM hourly_steps WHERE date < :beforeDate")
    abstract suspend fun pruneBefore(beforeDate: String)
}

@Dao
abstract class CheckLogDao {

    @Insert
    abstract suspend fun insert(entity: CheckLogEntity)

    @Query("SELECT * FROM check_log ORDER BY timeMillis DESC LIMIT :limit")
    abstract fun observeRecent(limit: Int): Flow<List<CheckLogEntity>>

    @Query("DELETE FROM check_log WHERE date < :beforeDate")
    abstract suspend fun pruneBefore(beforeDate: String)
}

@Database(
    entities = [HourlyStepsEntity::class, CheckLogEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun hourlyStepsDao(): HourlyStepsDao
    abstract fun checkLogDao(): CheckLogDao
}
