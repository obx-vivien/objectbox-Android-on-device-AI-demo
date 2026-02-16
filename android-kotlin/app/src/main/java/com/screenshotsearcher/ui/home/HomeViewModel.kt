package com.screenshotsearcher.ui.home

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.screenshotsearcher.core.data.ScreenshotRepository
import com.screenshotsearcher.infra.objectbox.ObjectBoxStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ScreenshotRepository(ObjectBoxStore.screenshotBox())

    val screenshots = repository.screenshots

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repository.seedFromAssetsIfEmpty(getApplication(), THUMBNAIL_MAX_PX)
        }
    }

    fun importImage(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.importFromUri(getApplication(), uri, THUMBNAIL_MAX_PX)
        }
    }

    fun refresh() {
        repository.refresh()
    }

    companion object {
        private const val THUMBNAIL_MAX_PX = 256
    }
}
