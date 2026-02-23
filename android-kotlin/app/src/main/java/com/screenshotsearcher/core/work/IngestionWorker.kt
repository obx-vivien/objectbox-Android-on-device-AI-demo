package com.screenshotsearcher.core.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.screenshotsearcher.core.data.ScreenshotRepository
import com.screenshotsearcher.core.data.SettingsRepository
import com.screenshotsearcher.core.model.AppState
import com.screenshotsearcher.core.model.IndexingStatus
import com.screenshotsearcher.infra.objectbox.ObjectBoxStore

class IngestionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val screenshotBox = ObjectBoxStore.screenshotBox()
        val repository = ScreenshotRepository(screenshotBox)
        val settingsRepository = SettingsRepository(
            ObjectBoxStore.moduleConfigBox(),
            ObjectBoxStore.appStateBox()
        )
        val appState = settingsRepository.getOrCreateAppState()
        if (appState.userPaused) {
            return Result.retry()
        }
        val config = settingsRepository.getOrCreateModuleConfig()
        val queued = screenshotBox.query(
            com.screenshotsearcher.core.model.Screenshot_.indexingStatus.equal(IndexingStatus.QUEUED.ordinal)
        ).build().find()
        for (item in queued) {
            if (appState.userPaused) {
                return Result.retry()
            }
            repository.processQueuedScreenshot(applicationContext, item, config)
        }
        appState.lastIndexingRunTimestamp = System.currentTimeMillis()
        settingsRepository.saveAppState(appState)
        return Result.success()
    }
}
