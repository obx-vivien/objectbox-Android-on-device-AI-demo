package com.screenshotsearcher

import android.app.Application
import android.util.Log
import com.screenshotsearcher.infra.objectbox.ObjectBoxStore
import com.screenshotsearcher.infra.captioning.GemmaCaptioner

class ScreenshotSearcherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ObjectBoxStore.init(this)
        // ObjectBox default Android path when built via androidContext(context).
        val objectBoxPath = java.io.File(filesDir, "objectbox").absolutePath
        Log.i("ObjectBox", "Store directory (default): $objectBoxPath")
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_UI_HIDDEN) {
            GemmaCaptioner.release()
        }
    }
}
