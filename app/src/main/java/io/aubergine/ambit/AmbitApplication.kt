package io.aubergine.ambit

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.aubergine.ambit.reliability.Watchdog

@HiltAndroidApp
class AmbitApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Watchdog.schedule(this)
    }
}
