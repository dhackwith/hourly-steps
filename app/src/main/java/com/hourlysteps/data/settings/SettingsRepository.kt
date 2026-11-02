package com.hourlysteps.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hourlysteps.domain.Settings
import com.hourlysteps.domain.VibrationStrength
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val DAILY_GOAL = intPreferencesKey("daily_goal")
        val FIRST_HOUR = intPreferencesKey("first_hour")
        val LAST_HOUR = intPreferencesKey("last_hour")
        val INACTIVITY_ENABLED = booleanPreferencesKey("inactivity_enabled")
        val INACTIVITY_THRESHOLD = intPreferencesKey("inactivity_threshold")
        val VIBRATION = stringPreferencesKey("vibration")
        val SKIP_WHILE_CHARGING = booleanPreferencesKey("skip_while_charging")
    }

    val settings: Flow<Settings> = context.settingsDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map(::read)

    suspend fun current(): Settings = settings.first()

    suspend fun update(transform: (Settings) -> Settings) {
        context.settingsDataStore.edit { prefs -> write(prefs, transform(read(prefs))) }
    }

    private fun read(prefs: Preferences): Settings {
        val defaults = Settings()
        return Settings(
            dailyGoal = prefs[Keys.DAILY_GOAL] ?: defaults.dailyGoal,
            firstHour = prefs[Keys.FIRST_HOUR] ?: defaults.firstHour,
            lastHour = prefs[Keys.LAST_HOUR] ?: defaults.lastHour,
            inactivityEnabled = prefs[Keys.INACTIVITY_ENABLED] ?: defaults.inactivityEnabled,
            inactivityThreshold = prefs[Keys.INACTIVITY_THRESHOLD] ?: defaults.inactivityThreshold,
            vibration = prefs[Keys.VIBRATION]
                ?.let { runCatching { VibrationStrength.valueOf(it) }.getOrNull() }
                ?: defaults.vibration,
            skipWhileCharging = prefs[Keys.SKIP_WHILE_CHARGING] ?: defaults.skipWhileCharging,
        )
    }

    private fun write(prefs: androidx.datastore.preferences.core.MutablePreferences, s: Settings) {
        prefs[Keys.DAILY_GOAL] = s.dailyGoal
        prefs[Keys.FIRST_HOUR] = s.firstHour
        prefs[Keys.LAST_HOUR] = s.lastHour
        prefs[Keys.INACTIVITY_ENABLED] = s.inactivityEnabled
        prefs[Keys.INACTIVITY_THRESHOLD] = s.inactivityThreshold
        prefs[Keys.VIBRATION] = s.vibration.name
        prefs[Keys.SKIP_WHILE_CHARGING] = s.skipWhileCharging
    }
}
