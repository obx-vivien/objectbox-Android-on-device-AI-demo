package com.screenshotsearcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.screenshotsearcher.infra.image.ImageDecoder
import com.screenshotsearcher.infra.mlkit.OcrEngine
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class OcrInstrumentedTest {

    @Test
    fun ocrRecognizesTextInBitmap() {
        val bitmap = createTextBitmap("HELLO123")
        val text = OcrEngine.recognizeText(bitmap)
        assertTrue(text.contains("HELLO123"))
    }

    @Test
    fun ocrHandlesExifRotation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val original = createTextBitmap("ROTATE")
        val rotated = original.rotate(90f)

        val file = File(context.cacheDir, "ocr_rotate.jpg")
        FileOutputStream(file).use { out ->
            rotated.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        val exif = ExifInterface(file.absolutePath)
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_270.toString())
        exif.saveAttributes()

        val uri = Uri.fromFile(file)
        val normalized = ImageDecoder.decodeNormalized(context, uri)
        val text = if (normalized != null) OcrEngine.recognizeText(normalized) else ""
        assertTrue(text.contains("ROTATE"))
    }

    private fun createTextBitmap(text: String): Bitmap {
        val bitmap = Bitmap.createBitmap(800, 200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.BLACK
        paint.textSize = 72f
        canvas.drawText(text, 40f, 120f, paint)
        return bitmap
    }

    private fun Bitmap.rotate(degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }
}
