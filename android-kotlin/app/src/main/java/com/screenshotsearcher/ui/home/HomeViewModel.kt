package com.screenshotsearcher.ui.home

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.screenshotsearcher.core.data.ScreenshotRepository
import com.screenshotsearcher.core.data.SettingsRepository
import com.screenshotsearcher.infra.objectbox.ObjectBoxStore
import com.screenshotsearcher.core.work.IngestionScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ScreenshotRepository(ObjectBoxStore.screenshotBox())
    private val settingsRepository = SettingsRepository(
        ObjectBoxStore.moduleConfigBox(),
        ObjectBoxStore.appStateBox()
    )

    val screenshots = repository.screenshots
    val indexingStats = repository.indexingStats
    private val _appState = MutableStateFlow(settingsRepository.getOrCreateAppState())
    val appState: StateFlow<com.screenshotsearcher.core.model.AppState> = _appState
    val seedProgress = repository.seedProgress
    val captionProgress = repository.captionProgress
    private val ingestionScheduler = IngestionScheduler(application)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repository.seedFromAssetsIfEmpty(getApplication(), THUMBNAIL_MAX_PX)
            repository.enqueueCaptionBackfill(getApplication())
        }
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                _appState.value = settingsRepository.getOrCreateAppState()
                delay(1000)
            }
        }
    }

    fun importImage(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.enqueueImportFromUri(getApplication(), uri, THUMBNAIL_MAX_PX)
            ingestionScheduler.enqueue()
        }
    }

    fun refresh() {
        repository.refresh()
    }

    fun pauseIngestion() {
        ingestionScheduler.pause()
    }

    fun resumeIngestion() {
        ingestionScheduler.resume()
    }

    companion object {
        private const val THUMBNAIL_MAX_PX = 256
    }
}
