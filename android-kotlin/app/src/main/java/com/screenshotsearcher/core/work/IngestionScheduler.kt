package com.screenshotsearcher.core.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.screenshotsearcher.core.data.SettingsRepository
import com.screenshotsearcher.core.model.IndexingStatus
import com.screenshotsearcher.infra.objectbox.ObjectBoxStore

class IngestionScheduler(private val context: Context) {
    private val workManager = WorkManager.getInstance(context)
    private val settingsRepository = SettingsRepository(
        ObjectBoxStore.moduleConfigBox(),
        ObjectBoxStore.appStateBox()
    )

    fun enqueue() {
        val work = OneTimeWorkRequestBuilder<IngestionWorker>().build()
        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, work)
    }

    fun pause() {
        val appState = settingsRepository.getOrCreateAppState()
        appState.userPaused = true
        settingsRepository.saveAppState(appState)
        workManager.cancelUniqueWork(WORK_NAME)
    }

    fun resume() {
        val appState = settingsRepository.getOrCreateAppState()
        appState.userPaused = false
        settingsRepository.saveAppState(appState)
        enqueue()
    }

    fun cancelAndMarkQueued() {
        workManager.cancelUniqueWork(WORK_NAME)
        val screenshotBox = ObjectBoxStore.screenshotBox()
        val queued = screenshotBox.query(
            com.screenshotsearcher.core.model.Screenshot_.indexingStatus.equal(IndexingStatus.QUEUED.ordinal)
        ).build().find()
        queued.forEach {
            it.indexingStatus = IndexingStatus.CANCELLED
            screenshotBox.put(it)
        }
    }

    companion object {
        private const val WORK_NAME = "ingestion"
    }
}
