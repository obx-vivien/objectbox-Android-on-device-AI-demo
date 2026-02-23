package com.screenshotsearcher.core.data

import com.screenshotsearcher.core.model.AppState
import com.screenshotsearcher.core.model.ModuleConfig
import io.objectbox.Box

class SettingsRepository(
    private val moduleConfigBox: Box<ModuleConfig>,
    private val appStateBox: Box<AppState>
) {
    fun getOrCreateModuleConfig(): ModuleConfig {
        val existing = moduleConfigBox.query().build().findFirst()
        if (existing != null) return existing
        val created = ModuleConfig()
        moduleConfigBox.put(created)
        return created
    }

    fun saveModuleConfig(config: ModuleConfig) {
        moduleConfigBox.put(config)
    }

    fun getOrCreateAppState(): AppState {
        val existing = appStateBox.query().build().findFirst()
        if (existing != null) return existing
        val created = AppState()
        appStateBox.put(created)
        return created
    }

    fun saveAppState(state: AppState) {
        appStateBox.put(state)
    }
}
