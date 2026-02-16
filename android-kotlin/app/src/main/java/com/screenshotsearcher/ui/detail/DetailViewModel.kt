package com.screenshotsearcher.ui.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.screenshotsearcher.core.model.Screenshot
import com.screenshotsearcher.infra.objectbox.ObjectBoxStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class DetailViewModel(application: Application) : AndroidViewModel(application) {
    private val box = ObjectBoxStore.screenshotBox()

    private val _screenshot = MutableStateFlow<Screenshot?>(null)
    val screenshot: StateFlow<Screenshot?> = _screenshot

    fun load(id: Long) {
        _screenshot.value = box.get(id)
    }
}
