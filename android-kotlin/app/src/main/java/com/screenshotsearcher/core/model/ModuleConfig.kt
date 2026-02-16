package com.screenshotsearcher.core.model

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

@Entity
class ModuleConfig(
    @Id var id: Long = 0,
    var ocrEnabled: Boolean = true,
    var textEmbeddingsEnabled: Boolean = true,
    var imageEmbeddingsEnabled: Boolean = true,
    var labelingEnabled: Boolean = true,
    var llmEnabled: Boolean = false
)
