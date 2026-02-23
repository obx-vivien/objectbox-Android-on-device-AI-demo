package com.screenshotsearcher

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.screenshotsearcher.core.data.ScreenshotRepository
import com.screenshotsearcher.core.model.Screenshot
import com.screenshotsearcher.core.model.MyObjectBox
import com.screenshotsearcher.infra.mediapipe.TextEmbedderWrapper
import io.objectbox.BoxStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SemanticSearchInstrumentedTest {
    private lateinit var store: BoxStore
    private lateinit var repository: ScreenshotRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.cacheDir, "obx-semantic-${System.currentTimeMillis()}")
        store = MyObjectBox.builder().androidContext(context).directory(dir).build()
        repository = ScreenshotRepository(store.boxFor(Screenshot::class.java))
    }

    @After
    fun tearDown() {
        store.close()
    }

    @Test
    fun semanticSearchReturnsClosestMatch() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val embedder = TextEmbedderWrapper.create(context)
        val helloEmbedding = embedder.embed("hello world")
        val otherEmbedding = embedder.embed("banana orchard")
        embedder.close()

        val box = store.boxFor(Screenshot::class.java)
        box.put(
            Screenshot(
                originalUri = "content://1",
                thumbnailBytes = byteArrayOf(1),
                ocrText = "hello world",
                textEmbedding = helloEmbedding
            )
        )
        box.put(
            Screenshot(
                originalUri = "content://2",
                thumbnailBytes = byteArrayOf(2),
                ocrText = "banana orchard",
                textEmbedding = otherEmbedding
            )
        )

        val results = repository.searchBySemanticText(context, "hello world")
        assertTrue(results.isNotEmpty())
        assertEquals("content://1", results.first().screenshot.originalUri)
    }

    @Test
    fun semanticSearchEmptyQueryReturnsEmpty() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val results = repository.searchBySemanticText(context, "")
        assertTrue(results.isEmpty())
    }
}
