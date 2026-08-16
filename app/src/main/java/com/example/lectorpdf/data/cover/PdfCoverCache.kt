package com.example.lectorpdf.data.cover

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import com.example.lectorpdf.domain.model.LibraryBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.math.roundToInt

class PdfCoverCache(context: Context) {
    private val directory = File(context.cacheDir, "pdf-covers").apply { mkdirs() }
    private val resolver = context.applicationContext.contentResolver

    suspend fun loadOrCreate(book: LibraryBook): Bitmap? = withContext(Dispatchers.IO) {
        renderPermits.withPermit {
            val key = sha256("${book.uri}|${book.sizeBytes}|${book.lastModified ?: 0}")
            val file = File(directory, "$key.jpg")
            if (file.isFile) {
                file.setLastModified(System.currentTimeMillis())
                BitmapFactory.decodeFile(file.absolutePath)?.let { return@withPermit it }
                file.delete()
            }
            val descriptor = resolver.openFileDescriptor(book.uri.toUri(), "r") ?: return@withPermit null
            val renderer = try {
                PdfRenderer(descriptor)
            } catch (error: Throwable) {
                descriptor.close()
                throw error
            }
            try {
                if (renderer.pageCount == 0) return@withPermit null
                renderer.openPage(0).use { page ->
                    val scale = (COVER_WIDTH.toFloat() / page.width).coerceAtMost(2f)
                    val width = (page.width * scale).roundToInt().coerceAtLeast(1)
                    val height = (page.height * scale).roundToInt().coerceIn(1, COVER_MAX_HEIGHT)
                    val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, Matrix().apply { setScale(scale, scale) }, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 82, it) }
                    trimDiskCache()
                    bitmap
                }
            } finally {
                renderer.close()
            }
        }
    }

    private fun trimDiskCache() {
        val files = directory.listFiles()?.filter(File::isFile)?.sortedByDescending(File::lastModified).orEmpty()
        var retainedBytes = 0L
        files.forEach { file ->
            retainedBytes += file.length()
            if (retainedBytes > MAX_CACHE_BYTES) file.delete()
        }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val COVER_WIDTH = 360
        const val COVER_MAX_HEIGHT = 640
        const val MAX_CACHE_BYTES = 80L * 1024 * 1024
        val renderPermits = Semaphore(2)
    }
}
