package com.hourlysteps.domain

/**
 * How a reminder period reads on screen. Windows are whole hours, so a slot is 30, 60 or 120
 * minutes long. A slot's own length decides the words, so a short last slot says what it is.
 */
object SlotWording {

    /** "this half hour", "this hour", "these two hours" */
    fun current(minutes: Int): String = pick(minutes, "this half hour", "this hour", "these two hours")

    /** "/30 min", "/hr", "/2 hr" */
    fun perSlotShort(minutes: Int): String = pick(minutes, "/30 min", "/hr", "/2 hr")

    /** "per 30 min", "per hour", "per 2 hours" */
    fun perSlot(minutes: Int): String = pick(minutes, "per 30 min", "per hour", "per 2 hours")

    /** "Half-hour goal", "Hourly goal", "Two-hour goal" */
    fun goalName(minutes: Int): String = pick(minutes, "Half-hour goal", "Hourly goal", "Two-hour goal")

    /** "half hours", "hours", "two-hour blocks" */
    fun plural(minutes: Int): String = pick(minutes, "half hours", "hours", "two-hour blocks")

    /** "Every 30 minutes", "Every hour", "Every 2 hours" */
    fun interval(minutes: Int): String = pick(minutes, "Every 30 minutes", "Every hour", "Every 2 hours")

    private fun pick(minutes: Int, halfHour: String, hour: String, twoHours: String): String = when (minutes) {
        30 -> halfHour
        120 -> twoHours
        else -> hour
    }
}
