package com.screenshotsearcher

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.screenshotsearcher.infra.mediapipe.TextEmbedderWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TextEmbeddingInstrumentedTest {

    @Test
    fun embeddingHasExpectedDimensionAndL2Norm() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val wrapper = TextEmbedderWrapper.create(context)
        val vector = wrapper.embed("hello world")
        wrapper.close()

        assertEquals(100, vector.size)
        val norm = TextEmbedderWrapper.l2Norm(vector)
        assertTrue("L2 norm expected ~1.0, got $norm", kotlin.math.abs(norm - 1.0) <= 0.001)
    }
}
