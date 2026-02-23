package com.screenshotsearcher.core.model

import io.objectbox.converter.PropertyConverter

class LabelListConverter : PropertyConverter<List<ImageLabel>, String> {
    override fun convertToDatabaseValue(entityProperty: List<ImageLabel>?): String {
        if (entityProperty.isNullOrEmpty()) return ""
        return entityProperty.joinToString(";") { label ->
            "${escape(label.text)}|${label.confidence}"
        }
    }

    override fun convertToEntityProperty(databaseValue: String?): List<ImageLabel> {
        if (databaseValue.isNullOrBlank()) return emptyList()
        return splitEscaped(databaseValue, ';').mapNotNull { entry ->
            if (entry.isBlank()) return@mapNotNull null
            val parts = splitEscaped(entry, '|')
            if (parts.size != 2) return@mapNotNull null
            val text = unescape(parts[0])
            val confidence = parts[1].toFloatOrNull() ?: return@mapNotNull null
            ImageLabel(text, confidence)
        }
    }

    private fun escape(value: String): String {
        return buildString {
            value.forEach { ch ->
                when (ch) {
                    '\\', '|', ';' -> {
                        append('\\')
                        append(ch)
                    }
                    else -> append(ch)
                }
            }
        }
    }

    private fun unescape(value: String): String {
        val result = StringBuilder()
        var escaped = false
        value.forEach { ch ->
            if (escaped) {
                result.append(ch)
                escaped = false
            } else if (ch == '\\') {
                escaped = true
            } else {
                result.append(ch)
            }
        }
        if (escaped) {
            result.append('\\')
        }
        return result.toString()
    }

    private fun splitEscaped(value: String, separator: Char): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var escaped = false
        value.forEach { ch ->
            if (escaped) {
                current.append(ch)
                escaped = false
            } else if (ch == '\\') {
                escaped = true
            } else if (ch == separator) {
                result.add(current.toString())
                current.setLength(0)
            } else {
                current.append(ch)
            }
        }
        result.add(current.toString())
        return result
    }
}
