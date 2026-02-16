package com.screenshotsearcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.screenshotsearcher.infra.mediapipe.ImageEmbedderWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageEmbeddingInstrumentedTest {

    @Test
    fun embeddingHasExpectedDimensionAndL2Norm() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val wrapper = ImageEmbedderWrapper.create(context)
        val bitmap = solidBitmap(Color.RED)
        val vector = wrapper.embed(bitmap)
        wrapper.close()

        assertEquals(1024, vector.size)
        val norm = ImageEmbedderWrapper.l2Norm(vector)
        assertTrue("L2 norm expected ~1.0, got $norm", kotlin.math.abs(norm - 1.0) <= 0.001)
    }

    private fun solidBitmap(color: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(color)
        return bitmap
    }
}
