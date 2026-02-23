package com.screenshotsearcher

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.screenshotsearcher.infra.image.MetadataExtractor
import com.screenshotsearcher.infra.image.DominantColorExtractor
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class MetadataExtractionInstrumentedTest {
    @Test
    fun extractsMetadataAndDominantColorsFromFile() {
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val cacheFile = File(appContext.cacheDir, "label_fixture.jpg")
        testContext.assets.open("label_fixture.jpg").use { input ->
            FileOutputStream(cacheFile).use { output ->
                input.copyTo(output)
            }
        }
        val bitmap = BitmapFactory.decodeFile(cacheFile.absolutePath)
            ?: throw AssertionError("Failed to decode cached asset")
        val metadata = MetadataExtractor.fromUri(appContext, Uri.fromFile(cacheFile), bitmap)
        assertNotNull(metadata.displayName)
        assertTrue(metadata.sizeBytes == null || metadata.sizeBytes > 0)
        assertTrue(metadata.width > 0)
        assertTrue(metadata.height > 0)

        val colors = DominantColorExtractor.extract(bitmap)
        assertTrue(colors.isNotEmpty())
    }
}
