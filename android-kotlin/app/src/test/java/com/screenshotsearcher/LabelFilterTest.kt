package com.screenshotsearcher

import com.screenshotsearcher.core.data.ScreenshotRepository
import com.screenshotsearcher.core.model.ImageLabel
import com.screenshotsearcher.core.model.Screenshot
import com.screenshotsearcher.core.model.MyObjectBox
import io.objectbox.BoxStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.nio.file.Files

class LabelFilterTest {
    private lateinit var store: BoxStore
    private lateinit var repository: ScreenshotRepository

    @Before
    fun setUp() {
        val dir = Files.createTempDirectory("objectbox-label-test").toFile()
        store = MyObjectBox.builder().directory(dir).build()
        repository = ScreenshotRepository(store.boxFor(Screenshot::class.java))
    }

    @After
    fun tearDown() {
        store.close()
    }

    @Test
    fun filterByLabelsRespectsConfidenceAndSelection() {
        val apple = Screenshot(
            originalUri = "content://example/1",
            labels = listOf(ImageLabel("Fruit", 0.92f), ImageLabel("Food", 0.80f))
        )
        val lowConfidence = Screenshot(
            originalUri = "content://example/2",
            labels = listOf(ImageLabel("Fruit", 0.40f))
        )
        val other = Screenshot(
            originalUri = "content://example/3",
            labels = listOf(ImageLabel("Vehicle", 0.95f))
        )

        val filtered = repository.filterByLabels(listOf(apple, lowConfidence, other), setOf("Fruit"))
        assertEquals(listOf(apple), filtered)
    }
}
