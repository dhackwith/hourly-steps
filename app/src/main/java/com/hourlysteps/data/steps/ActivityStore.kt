package com.hourlysteps.data.steps

import android.content.Context
import androidx.core.content.edit
import com.hourlysteps.domain.ActivitySource
import com.hourlysteps.domain.ActivityState
import com.hourlysteps.domain.UserActivity

/** Remembers the last activity Health Services reported. It only tells us when it changes. */
class ActivityStore(context: Context) : ActivitySource {

    private val prefs = context.getSharedPreferences("activity", Context.MODE_PRIVATE)

    fun record(activity: UserActivity, changedAtMillis: Long) {
        prefs.edit {
            putString(KEY_ACTIVITY, activity.name)
            putLong(KEY_CHANGED_AT, changedAtMillis)
        }
    }

    override fun currentAt(nowMillis: Long): UserActivity {
        val activity = prefs.getString(KEY_ACTIVITY, null)
            ?.let { runCatching { UserActivity.valueOf(it) }.getOrNull() }
            ?: return UserActivity.UNKNOWN
        return ActivityState(activity, prefs.getLong(KEY_CHANGED_AT, 0L)).currentAt(nowMillis)
    }

    private companion object {
        const val KEY_ACTIVITY = "activity"
        const val KEY_CHANGED_AT = "changed_at"
    }
}
