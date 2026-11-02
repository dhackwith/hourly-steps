package com.hourlysteps.domain

enum class CelebrationKind {
    /** This hour's share of the daily goal was reached. */
    HOURLY_GOAL,

    /** Daily goal already met; this hour's inactivity threshold was reached. */
    INACTIVITY_GOAL,
}

data class Celebration(
    val kind: CelebrationKind,
    val stepsThisHour: Int,
    val target: Int,
    val todayTotal: Int,
    val dailyGoal: Int,
)

object CelebrationDecider {

    /**
     * Whether reaching [stepsThisHour] in [hour] deserves a celebration right now.
     *
     * The target follows the daily goal as it stood when the hour began, not after the latest
     * steps: otherwise the batch that crosses both the hourly and the daily goal would switch
     * the hour into "goal met" mode and the hourly celebration would be lost.
     */
    fun decide(
        settings: Settings,
        stepsThisHour: Int,
        todayTotal: Int,
        hour: Int,
        alreadyCelebratedThisHour: Boolean,
    ): Celebration? {
        if (!settings.celebrate || alreadyCelebratedThisHour) return null
        if (hour !in settings.firstHour..settings.lastHour) return null

        val totalAtHourStart = todayTotal - stepsThisHour
        val (kind, target) = when {
            totalAtHourStart < settings.dailyGoal ->
                CelebrationKind.HOURLY_GOAL to GoalCalculator.hourlyGoal(settings)
            settings.inactivityEnabled ->
                CelebrationKind.INACTIVITY_GOAL to settings.inactivityThreshold
            else -> return null
        }
        if (stepsThisHour < target) return null
        return Celebration(kind, stepsThisHour, target, todayTotal, settings.dailyGoal)
    }
}
