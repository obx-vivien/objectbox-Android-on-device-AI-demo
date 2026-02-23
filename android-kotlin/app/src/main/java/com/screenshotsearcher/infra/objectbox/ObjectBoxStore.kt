package com.screenshotsearcher.infra.objectbox

import android.content.Context
import com.screenshotsearcher.core.model.AppState
import com.screenshotsearcher.core.model.ModuleConfig
import com.screenshotsearcher.core.model.MyObjectBox
import com.screenshotsearcher.core.model.Screenshot
import io.objectbox.Box
import io.objectbox.BoxStore

object ObjectBoxStore {
    lateinit var store: BoxStore
        private set

    fun init(context: Context) {
        if (::store.isInitialized) {
            return
        }
        store = MyObjectBox.builder()
            .androidContext(context)
            .build()
    }

    fun screenshotBox(): Box<Screenshot> = store.boxFor(Screenshot::class.java)
    fun moduleConfigBox(): Box<ModuleConfig> = store.boxFor(ModuleConfig::class.java)
    fun appStateBox(): Box<AppState> = store.boxFor(AppState::class.java)
}
