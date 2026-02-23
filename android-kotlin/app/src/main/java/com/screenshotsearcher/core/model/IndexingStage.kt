package com.screenshotsearcher.core.model

import io.objectbox.converter.PropertyConverter

enum class IndexingStage {
    NONE,
    THUMBNAIL,
    METADATA,
    COLORS,
    OCR,
    LABELS,
    DESCRIPTION,
    EMBEDDINGS,
    DONE
}

class IndexingStageConverter : PropertyConverter<IndexingStage, Int> {
    override fun convertToDatabaseValue(entityProperty: IndexingStage?): Int {
        return (entityProperty ?: IndexingStage.NONE).ordinal
    }

    override fun convertToEntityProperty(databaseValue: Int?): IndexingStage {
        val index = databaseValue ?: 0
        return IndexingStage.values().getOrElse(index) { IndexingStage.NONE }
    }
}
