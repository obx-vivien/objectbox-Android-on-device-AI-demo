package com.screenshotsearcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.screenshotsearcher.core.data.ScreenshotRepository
import com.screenshotsearcher.core.model.Screenshot
import com.screenshotsearcher.core.model.MyObjectBox
import com.screenshotsearcher.infra.mediapipe.ImageEmbedderWrapper
import io.objectbox.BoxStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ImageSimilarityInstrumentedTest {
    private lateinit var store: BoxStore
    private lateinit var repository: ScreenshotRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.cacheDir, "obx-image-${System.currentTimeMillis()}")
        store = MyObjectBox.builder().androidContext(context).directory(dir).build()
        repository = ScreenshotRepository(store.boxFor(Screenshot::class.java))
    }

    @After
    fun tearDown() {
        store.close()
    }

    @Test
    fun imageSimilarityReturnsClosestMatch() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val embedder = ImageEmbedderWrapper.create(context)
        val red = solidBitmap(Color.RED)
        val blue = solidBitmap(Color.BLUE)
        val redEmbedding = embedder.embed(red)
        val blueEmbedding = embedder.embed(blue)
        embedder.close()

        val box = store.boxFor(Screenshot::class.java)
        box.put(Screenshot(originalUri = "content://1", thumbnailBytes = byteArrayOf(1), imageEmbedding = redEmbedding))
        box.put(Screenshot(originalUri = "content://2", thumbnailBytes = byteArrayOf(2), imageEmbedding = blueEmbedding))

        val results = repository.searchByImageSimilarity(context, red)
        assertTrue(results.isNotEmpty())
        assertEquals("content://1", results.first().screenshot.originalUri)
    }

    private fun solidBitmap(color: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(color)
        return bitmap
    }
}
