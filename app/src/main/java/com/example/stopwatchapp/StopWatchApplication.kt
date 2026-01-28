package com.example.stopwatchapp

import android.app.Application
import timber.log.Timber

class StopWatchApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
