package com.screenshotsearcher

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.screenshotsearcher.infra.mlkit.ImageLabelerEngine
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageLabelingInstrumentedTest {
    @Test
    fun labelsContainExpectedCategory() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val bitmap = context.assets.open("label_fixture.jpg").use { input ->
            BitmapFactory.decodeStream(input)
        } ?: throw AssertionError("Failed to decode label_fixture.jpg")

        val labels = ImageLabelerEngine.label(bitmap)
        val expected = setOf("Food", "Fruit", "Apple", "Pear", "Orange")
        val matched = labels.any { label ->
            label.text in expected && label.confidence >= 0.70f
        }
        val summary = labels.joinToString { label ->
            "${label.text}:${"%.2f".format(label.confidence)}"
        }
        assertTrue(
            "Expected at least one label in $expected with confidence >= 0.70. Got: $summary",
            matched
        )
    }
}
