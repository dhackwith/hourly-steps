package com.hourlysteps

import android.app.Application
import kotlinx.coroutines.launch

class HourlyStepsApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.appScope.launch {
            container.reminderNotifier.syncChannels(container.settingsRepository.current().vibration)
        }
    }
}
