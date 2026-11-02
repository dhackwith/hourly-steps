package com.hourlysteps

import android.app.Application

class HourlyStepsApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.reminderNotifier.createChannels()
    }
}
