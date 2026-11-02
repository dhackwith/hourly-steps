package com.hourlysteps.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.hourlysteps.R
import com.hourlysteps.domain.VibrationStrength

class NotificationChannels(private val context: Context) {

    private val notificationManager: NotificationManager
        get() = context.getSystemService(NotificationManager::class.java)

    /**
     * Makes the channels match the vibration setting and removes any others.
     *
     * Wear OS will not let a background app vibrate for a notification, only the channel's own
     * pattern plays, and it shows the pop-up alert only for a channel that vibrates. A channel's
     * pattern cannot change after creation, so the strength is part of the channel's id.
     */
    fun sync(strength: VibrationStrength) {
        val alerting = listOf(
            channel(GOAL, strength, Haptics.reminderPattern(strength), R.string.channel_goal_name, R.string.channel_goal_description),
            channel(INACTIVITY, strength, Haptics.reminderPattern(strength), R.string.channel_inactivity_name, R.string.channel_inactivity_description),
            channel(CELEBRATION, strength, Haptics.celebrationPattern(strength), R.string.channel_celebration_name, R.string.channel_celebration_description),
        )
        val record = NotificationChannel(CELEBRATION_RECORD, context.getString(R.string.channel_record_name), NotificationManager.IMPORTANCE_LOW).apply {
            description = context.getString(R.string.channel_record_description)
            enableVibration(false)
            setSound(null, null)
        }
        val wanted = alerting + record
        val wantedIds = wanted.map { it.id }.toSet()
        notificationManager.notificationChannels
            .filter { it.id !in wantedIds }
            .forEach { notificationManager.deleteNotificationChannel(it.id) }
        notificationManager.createNotificationChannels(wanted)
    }

    private fun channel(base: String, strength: VibrationStrength, pattern: LongArray?, name: Int, description: Int) =
        NotificationChannel(id(base, strength), context.getString(name), NotificationManager.IMPORTANCE_HIGH).apply {
            this.description = context.getString(description)
            enableVibration(true)
            vibrationPattern = pattern ?: IMPERCEPTIBLE_PATTERN
            setSound(null, null)
        }

    companion object {
        const val GOAL = "goal_reminders"
        const val INACTIVITY = "inactivity_reminders"
        /** Versioned because a channel's vibration can't change once it exists. */
        const val CELEBRATION = "goal_celebrations_v2"
        const val CELEBRATION_RECORD = "goal_record"

        fun id(base: String, strength: VibrationStrength): String = "${base}_${strength.name.lowercase()}"

        /** Too short to feel, but enough for Wear OS to treat the notification as alerting. */
        private val IMPERCEPTIBLE_PATTERN = longArrayOf(0, 1)
    }
}
