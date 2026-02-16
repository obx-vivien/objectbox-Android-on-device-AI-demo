package com.screenshotsearcher

import com.screenshotsearcher.core.model.IndexingStatus
import com.screenshotsearcher.core.model.Screenshot
import com.screenshotsearcher.core.model.MyObjectBox
import io.objectbox.BoxStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.nio.file.Files

class ObjectBoxCrudTest {
    private lateinit var store: BoxStore

    @Before
    fun setUp() {
        val dir = Files.createTempDirectory("objectbox-test").toFile()
        store = MyObjectBox.builder().directory(dir).build()
    }

    @After
    fun tearDown() {
        store.close()
    }

    @Test
    fun createReadScreenshot() {
        val box = store.boxFor(Screenshot::class.java)
        val screenshot = Screenshot(
            originalUri = "content://example/1",
            thumbnailBytes = byteArrayOf(1, 2, 3),
            indexingStatus = IndexingStatus.INDEXED
        )
        val id = box.put(screenshot)
        val loaded = box.get(id)
        assertNotNull(loaded)
        assertEquals("content://example/1", loaded.originalUri)
        assertEquals(IndexingStatus.INDEXED, loaded.indexingStatus)
    }

    @Test
    fun schemaContainsScreenshotEntity() {
        val entityInfo = store.boxFor(Screenshot::class.java).entityInfo
        assertEquals("Screenshot", entityInfo.dbName)
    }
}
