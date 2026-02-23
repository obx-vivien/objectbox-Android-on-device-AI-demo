package com.screenshotsearcher.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.screenshotsearcher.core.data.ScreenshotRepository
import com.screenshotsearcher.core.data.SettingsRepository
import com.screenshotsearcher.core.model.ModuleConfig
import com.screenshotsearcher.core.work.IngestionScheduler
import com.screenshotsearcher.infra.objectbox.ObjectBoxStore
import com.screenshotsearcher.infra.captioning.GemmaCaptioner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(
        ObjectBoxStore.moduleConfigBox(),
        ObjectBoxStore.appStateBox()
    )
    private val screenshotRepository = ScreenshotRepository(ObjectBoxStore.screenshotBox())

    private val _moduleConfig = MutableStateFlow(repository.getOrCreateModuleConfig())
    val moduleConfig: StateFlow<ModuleConfig> = _moduleConfig
    private val _llmModelAvailable = MutableStateFlow(GemmaCaptioner.isModelAvailable())
    val llmModelAvailable: StateFlow<Boolean> = _llmModelAvailable
    private val _llmActionStatus = MutableStateFlow<String?>(null)
    val llmActionStatus: StateFlow<String?> = _llmActionStatus
    private val ingestionScheduler = IngestionScheduler(application)

    fun updateModuleConfig(transform: (ModuleConfig) -> Unit) {
        val updated = _moduleConfig.value
        transform(updated)
        repository.saveModuleConfig(updated)
        _moduleConfig.value = updated
    }

    fun pauseIngestion() {
        ingestionScheduler.pause()
    }

    fun resumeIngestion() {
        ingestionScheduler.resume()
    }

    fun cancelQueued() {
        ingestionScheduler.cancelAndMarkQueued()
    }

    fun refreshLlmStatus() {
        _llmModelAvailable.value = GemmaCaptioner.isModelAvailable()
    }

    fun warmUpLlm() {
        viewModelScope.launch(Dispatchers.IO) {
            val result = GemmaCaptioner.warmUp(getApplication())
            _llmActionStatus.value = if (result.isSuccess) {
                "Warm up complete"
            } else {
                "Warm up failed: ${result.exceptionOrNull()?.message ?: "unknown error"}"
            }
        }
    }

    fun releaseLlm() {
        GemmaCaptioner.release()
        _llmActionStatus.value = "LLM released"
    }

    fun clearLlmStatusMessage() {
        _llmActionStatus.value = null
    }

    fun reindexAll() {
        screenshotRepository.reindexAll()
        ingestionScheduler.enqueue()
    }

    fun clearDatabase() {
        screenshotRepository.clearAll()
    }
}
