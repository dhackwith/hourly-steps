package com.hourlysteps.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.LocalDate
import java.time.ZoneId

/** The interval one v1 hourly row becomes: the whole of that local hour. */
fun hourlyRowInterval(date: String, hour: Int, zone: ZoneId): Pair<Long, Long> {
    val start = LocalDate.parse(date).atTime(hour, 0)
    return start.atZone(zone).toInstant().toEpochMilli() to
        start.plusHours(1).atZone(zone).toInstant().toEpochMilli()
}

/** v1 to v2: hourly buckets become step points, and the check log moves from hours to slots. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS step_points (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                startMillis INTEGER NOT NULL,
                endMillis INTEGER NOT NULL,
                steps INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_step_points_endMillis ON step_points (endMillis)")

        val zone = ZoneId.systemDefault()
        db.query("SELECT date, hour, steps FROM hourly_steps WHERE steps > 0").use { rows ->
            while (rows.moveToNext()) {
                val (start, end) = hourlyRowInterval(rows.getString(0), rows.getInt(1), zone)
                db.execSQL(
                    "INSERT INTO step_points (startMillis, endMillis, steps) VALUES (?, ?, ?)",
                    arrayOf<Any>(start, end, rows.getInt(2)),
                )
            }
        }
        db.execSQL("DROP TABLE hourly_steps")

        db.execSQL(
            """
            CREATE TABLE check_log_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                timeMillis INTEGER NOT NULL,
                date TEXT NOT NULL,
                slotStartMinute INTEGER NOT NULL,
                stepsInSlot INTEGER NOT NULL,
                todayTotal INTEGER NOT NULL,
                mode TEXT NOT NULL,
                need INTEGER NOT NULL,
                catchUpPerSlot INTEGER,
                notified INTEGER NOT NULL,
                skippedReason TEXT
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO check_log_new (id, timeMillis, date, slotStartMinute, stepsInSlot, todayTotal, mode,
                need, catchUpPerSlot, notified, skippedReason)
            SELECT id, timeMillis, date, hour * 60, stepsThisHour, todayTotal, mode,
                need, catchUpPerHour, notified, skippedReason FROM check_log
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE check_log")
        db.execSQL("ALTER TABLE check_log_new RENAME TO check_log")
    }
}
