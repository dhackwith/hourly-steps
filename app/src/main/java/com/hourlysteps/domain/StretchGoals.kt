package com.hourlysteps.domain

/** How far into the stretch goals today is, once the daily goal is met. */
data class StretchProgress(
    /** Stretch goals already reached. */
    val reached: Int,
    /** How many are set. */
    val count: Int,
    /** Steps into the lap being walked. The last lap stays full once it's reached. */
    val lapSteps: Int,
    val lapSize: Int,
    /** Today's total that finishes the lap being walked. */
    val target: Int,
) {
    /** The lap being walked, from 1. Stays on the last one once it's reached. */
    val level: Int get() = (reached + 1).coerceAtMost(count)

    val allReached: Boolean get() = reached >= count

    val lapProgress: Float get() = (lapSteps.toFloat() / lapSize.coerceAtLeast(1)).coerceIn(0f, 1f)
}

/** Each stretch goal is a number of steps on top of the goal before it, starting from the daily goal. */
object StretchGoals {

    /** Today's total that reaches each stretch goal, in order. */
    fun targets(dailyGoal: Int, goals: List<Int>): List<Int> =
        goals.runningFold(dailyGoal) { total, goal -> total + goal }.drop(1)

    fun reachedCount(dailyGoal: Int, goals: List<Int>, todayTotal: Int): Int =
        targets(dailyGoal, goals).count { todayTotal >= it }

    /** Null until the daily goal is met, and when no stretch goals are set. */
    fun progress(dailyGoal: Int, goals: List<Int>, todayTotal: Int): StretchProgress? {
        if (goals.isEmpty() || todayTotal < dailyGoal) return null
        val targets = targets(dailyGoal, goals)
        val reached = targets.count { todayTotal >= it }
        val lap = reached.coerceAtMost(goals.lastIndex)
        val allReached = reached == goals.size
        return StretchProgress(
            reached = reached,
            count = goals.size,
            lapSteps = if (allReached) goals[lap] else todayTotal - (targets[lap] - goals[lap]),
            lapSize = goals[lap],
            target = targets[lap],
        )
    }

    /** What to call the stretch goal at [level], from 1. */
    fun name(level: Int, count: Int): String =
        if (count == 1) "Stretch goal" else "Stretch $level of $count"
}
