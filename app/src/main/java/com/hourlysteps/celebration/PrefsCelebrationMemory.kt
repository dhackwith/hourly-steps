package com.hourlysteps.celebration

import android.content.Context
import androidx.core.content.edit

class PrefsCelebrationMemory(context: Context) : CelebrationMemory {

    private val prefs = context.getSharedPreferences("celebrations", Context.MODE_PRIVATE)

    override fun lastSlot(): String? = prefs.getString(KEY_LAST_SLOT, null)

    override fun lastDay(): String? = prefs.getString(KEY_LAST_DAY, null)

    override fun lastStretch(): String? = prefs.getString(KEY_LAST_STRETCH, null)

    override fun remember(slot: String?, day: String?, stretch: String?) {
        prefs.edit {
            slot?.let { putString(KEY_LAST_SLOT, it) }
            day?.let { putString(KEY_LAST_DAY, it) }
            stretch?.let { putString(KEY_LAST_STRETCH, it) }
        }
    }

    private companion object {
        const val KEY_LAST_SLOT = "last_celebrated_slot"
        const val KEY_LAST_DAY = "last_celebrated_day"
        const val KEY_LAST_STRETCH = "last_celebrated_stretch"
    }
}
