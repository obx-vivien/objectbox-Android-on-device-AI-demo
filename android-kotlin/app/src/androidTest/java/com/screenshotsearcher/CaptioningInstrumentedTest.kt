package com.screenshotsearcher

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.screenshotsearcher.infra.captioning.GemmaCaptioner
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CaptioningInstrumentedTest {
    @Test
    fun captionIsGenerated() {
        assumeTrue("Gemma model must be installed", GemmaCaptioner.isModelAvailable())
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = testContext.assets.open("label_fixture.jpg").use { input ->
            BitmapFactory.decodeStream(input)
        } ?: throw AssertionError("Failed to decode label_fixture.jpg")

        val captioner = GemmaCaptioner.create(appContext)
        val caption = captioner.caption(bitmap) ?: ""
        captioner.close()

        assertTrue("Expected non-empty caption, got: '$caption'", caption.isNotBlank())
    }
}
