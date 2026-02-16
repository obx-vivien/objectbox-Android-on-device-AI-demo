package com.screenshotsearcher.core.model

import io.objectbox.converter.PropertyConverter

class IndexingStatusConverter : PropertyConverter<IndexingStatus, Int> {
    override fun convertToDatabaseValue(entityProperty: IndexingStatus): Int {
        return entityProperty.ordinal
    }

    override fun convertToEntityProperty(databaseValue: Int): IndexingStatus {
        return IndexingStatus.entries.getOrElse(databaseValue) { IndexingStatus.INDEXED }
    }
}
