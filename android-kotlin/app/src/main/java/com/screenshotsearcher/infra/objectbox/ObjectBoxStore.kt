package com.screenshotsearcher.infra.objectbox

import android.content.Context
import com.screenshotsearcher.core.model.Screenshot
import com.screenshotsearcher.core.model.MyObjectBox
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
}
