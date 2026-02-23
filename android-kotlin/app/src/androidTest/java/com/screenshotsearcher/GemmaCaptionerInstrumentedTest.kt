package com.screenshotsearcher

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.screenshotsearcher.infra.captioning.GemmaCaptioner
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GemmaCaptionerInstrumentedTest {
    @Test
    fun missingModelFailsGracefully() {
        assumeFalse(GemmaCaptioner.isModelAvailable())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        try {
            GemmaCaptioner.create(context)
            fail("Expected model creation to fail when model is missing")
        } catch (_: IllegalArgumentException) {
            // Expected: model missing.
        }
    }

    @Test
    fun warmUpSucceedsWhenModelPresent() {
        assumeTrue(GemmaCaptioner.isModelAvailable())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val result = GemmaCaptioner.warmUp(context)
        assertTrue("Warm up should succeed when model is present", result.isSuccess)
    }
}
