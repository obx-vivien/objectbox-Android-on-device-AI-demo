package com.screenshotsearcher

import android.app.Application
import com.screenshotsearcher.infra.objectbox.ObjectBoxStore

class ScreenshotSearcherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ObjectBoxStore.init(this)
    }
}
