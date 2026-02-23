package com.screenshotsearcher

import com.screenshotsearcher.core.data.MetadataFilters
import com.screenshotsearcher.core.model.Screenshot
import org.junit.Assert.assertEquals
import org.junit.Test

class MetadataFiltersTest {
    @Test
    fun filtersMatchByNameAndSize() {
        val small = Screenshot(displayName = "cat.png", sizeBytes = 1_000, width = 200, height = 100)
        val large = Screenshot(displayName = "dog.png", sizeBytes = 10_000, width = 800, height = 600)
        val filters = MetadataFilters(nameContains = "dog", minSizeBytes = 5_000)
        val results = listOf(small, large).filter { filters.matches(it) }
        assertEquals(listOf(large), results)
    }
}
