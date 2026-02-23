package com.screenshotsearcher.core.model

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

@Entity
class AppState(
    @Id var id: Long = 0,
    var userPaused: Boolean = false,
    var lastIndexingRunTimestamp: Long? = null
)
