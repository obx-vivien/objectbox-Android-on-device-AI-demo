package com.screenshotsearcher.core.model

import io.objectbox.converter.PropertyConverter

class IntListConverter : PropertyConverter<List<Int>, String> {
    override fun convertToDatabaseValue(entityProperty: List<Int>?): String {
        if (entityProperty.isNullOrEmpty()) return ""
        return entityProperty.joinToString(",") { value ->
            value.toUInt().toString(16)
        }
    }

    override fun convertToEntityProperty(databaseValue: String?): List<Int> {
        if (databaseValue.isNullOrBlank()) return emptyList()
        return databaseValue.split(",")
            .mapNotNull { token -> token.toUIntOrNull(16)?.toInt() }
    }
}
