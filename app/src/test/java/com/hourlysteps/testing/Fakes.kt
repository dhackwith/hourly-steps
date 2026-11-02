package com.hourlysteps.testing

import com.hourlysteps.celebration.CelebrationMemory
import com.hourlysteps.celebration.CelebrationSurface
import com.hourlysteps.data.db.CheckLogDao
import com.hourlysteps.data.db.CheckLogEntity
import com.hourlysteps.domain.ActivitySource
import com.hourlysteps.domain.Celebration
import com.hourlysteps.domain.CheckResult
import com.hourlysteps.domain.DayPlan
import com.hourlysteps.domain.Settings
import com.hourlysteps.domain.SettingsSource
import com.hourlysteps.domain.Slot
import com.hourlysteps.domain.StepCheckAlarm
import com.hourlysteps.domain.StepCounts
import com.hourlysteps.domain.StreakInfo
import com.hourlysteps.domain.StreakTracker
import com.hourlysteps.domain.SurfaceRefresher
import com.hourlysteps.domain.UserActivity
import com.hourlysteps.domain.VibrationStrength
import com.hourlysteps.reminder.FollowUpKind
import com.hourlysteps.reminder.ReminderAlarms
import com.hourlysteps.reminder.ReminderPresenter
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Everything the fakes did, in order, across all of them. */
class Events {
    val all = mutableListOf<String>()
    fun add(event: String) {
        all += event
    }
    fun has(event: String) = event in all
    fun count(event: String) = all.count { it == event }
}

class FakeSettings(var settings: Settings = Settings()) : SettingsSource {
    override suspend fun current() = settings
}

class FakeSteps(private val events: Events, var inSlot: Int = 0, var total: Int = 0) : StepCounts {
    /** Steps the watch is still holding, which only count after a flush. */
    var heldBack = 0

    override suspend fun flush() {
        events.add("flush")
        inSlot += heldBack
        total += heldBack
        heldBack = 0
    }

    override suspend fun stepsInSlot(slot: Slot) = inSlot
    override suspend fun dayTotal(date: LocalDate) = total
    override suspend fun pruneOlderThan(days: Long) = events.add("prune steps $days")
}

class FakeActivity(var activity: UserActivity = UserActivity.UNKNOWN) : ActivitySource {
    override fun currentAt(nowMillis: Long) = activity
}

class FakeStreaks(private val events: Events, var current: Int = 0) : StreakTracker {
    override suspend fun finalizePastDays() = events.add("finalize days")
    override suspend fun current() = StreakInfo(current, current, emptyList(), null)
}

class FakeSurfaces(private val events: Events) : SurfaceRefresher {
    override fun updateNow() = events.add("refresh surfaces")
}

class FakeStepCheckAlarm(private val events: Events) : StepCheckAlarm {
    override fun scheduleStepCheck() = events.add("step check")
}

class FakeAlarms(private val events: Events) : ReminderAlarms {
    val followUps = mutableListOf<Pair<ZonedDateTime, FollowUpKind>>()
    var armedAfter: ZonedDateTime? = null

    override suspend fun ensureScheduled(after: ZonedDateTime): ZonedDateTime? {
        events.add("arm next check")
        armedAfter = after
        return null
    }

    override fun scheduleFollowUp(at: ZonedDateTime, kind: FollowUpKind) {
        events.add("follow-up $kind")
        followUps += at to kind
    }
}

class FakeReminder(private val events: Events) : ReminderPresenter {
    val shown = mutableListOf<CheckResult>()

    override suspend fun show(result: CheckResult, settings: Settings, plan: DayPlan?, slot: Slot?, now: ZonedDateTime, todayTotal: Int) {
        if (result != CheckResult.None) events.add("show reminder")
        shown += result
    }

    override fun cancel() = events.add("cancel reminder")
}

class FakeCheckLog(private val events: Events) : CheckLogDao() {
    val rows = mutableListOf<CheckLogEntity>()

    override suspend fun insert(entity: CheckLogEntity) {
        events.add("log check")
        rows += entity
    }

    override fun observeRecent(limit: Int): Flow<List<CheckLogEntity>> = emptyFlow()
    override fun observeForDate(date: String): Flow<List<CheckLogEntity>> = emptyFlow()
    override suspend fun pruneBefore(beforeDate: String) = events.add("prune log $beforeDate")
}

class FakeMemory(private var slot: String? = null, private var day: String? = null) : CelebrationMemory {
    override fun lastSlot() = slot
    override fun lastDay() = day

    override fun remember(slot: String?, day: String?) {
        slot?.let { this.slot = it }
        day?.let { this.day = it }
    }
}

class FakeCelebrationSurface(private val events: Events) : CelebrationSurface {
    var uiVisible = false
    var canOpenInBackground = true
    var screenOpens = true
    val celebrations = mutableListOf<Celebration>()
    var vibration: LongArray? = null

    override fun isUiVisible() = uiVisible
    override fun canOpenFromBackground() = canOpenInBackground
    override fun prepare(strength: VibrationStrength) = events.add("prepare")
    override suspend fun wakeScreen() = events.add("wake")

    override suspend fun openScreen(celebration: Celebration, vibration: LongArray?): Boolean {
        events.add("open screen")
        celebrations += celebration
        this.vibration = vibration
        return screenOpens
    }

    override fun postRecord(celebration: Celebration) = events.add("post record")

    override fun postAlert(celebration: Celebration, strength: VibrationStrength) {
        events.add("post alert")
        celebrations += celebration
    }

    override fun vibrate(pattern: LongArray?) {
        events.add("vibrate")
        vibration = pattern
    }
}
