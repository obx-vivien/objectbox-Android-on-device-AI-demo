package com.screenshotsearcher

import com.screenshotsearcher.core.data.ScreenshotRepository
import com.screenshotsearcher.core.model.Screenshot
import com.screenshotsearcher.core.model.MyObjectBox
import io.objectbox.BoxStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.nio.file.Files

class KeywordSearchTest {
    private lateinit var store: BoxStore
    private lateinit var repository: ScreenshotRepository

    @Before
    fun setUp() {
        val dir = Files.createTempDirectory("objectbox-search").toFile()
        store = MyObjectBox.builder().directory(dir).build()
        repository = ScreenshotRepository(store.boxFor(Screenshot::class.java))
    }

    @After
    fun tearDown() {
        store.close()
    }

    @Test
    fun keywordSearchIsCaseInsensitive() {
        val box = store.boxFor(Screenshot::class.java)
        box.put(Screenshot(originalUri = "content://1", thumbnailBytes = byteArrayOf(1), ocrText = "Password Reset"))
        box.put(Screenshot(originalUri = "content://2", thumbnailBytes = byteArrayOf(2), ocrText = "Other"))

        val results = repository.searchByKeyword("password")
        assertEquals(1, results.size)
        assertEquals("content://1", results.first().originalUri)
    }
}
