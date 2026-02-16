package com.screenshotsearcher

import com.screenshotsearcher.core.pipeline.Thumbnailer
import org.junit.Assert.assertEquals
import org.junit.Test

class ThumbnailerTest {
    @Test
    fun calculatesScaledSizePreservingAspect() {
        val (w, h) = Thumbnailer.calculateTargetSize(width = 2000, height = 1000, maxSizePx = 250)
        assertEquals(250, w)
        assertEquals(125, h)
    }

    @Test
    fun calculatesScaledSizeForPortrait() {
        val (w, h) = Thumbnailer.calculateTargetSize(width = 800, height = 1600, maxSizePx = 200)
        assertEquals(100, w)
        assertEquals(200, h)
    }
}
