package com.example.lectorpdf.reader.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

enum class PdfFitMode { WIDTH, PAGE }

data class PdfSearchResult(val pageIndex: Int, val matchCount: Int)

class PdfDocumentEngine(private val context: Context) : AutoCloseable {
    private var descriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private val mutex = Mutex()
    private val cache = object : LruCache<String, Bitmap>(64 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount / 1024
    }

    val pageCount: Int get() = renderer?.pageCount ?: 0

    suspend fun open(uri: Uri): Int = withContext(Dispatchers.IO) {
        mutex.withLock {
            closeInternal()
            val openedDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
                ?: error("No se pudo abrir el archivo PDF")
            try {
                descriptor = openedDescriptor
                renderer = PdfRenderer(openedDescriptor)
                renderer?.pageCount ?: 0
            } catch (error: Throwable) {
                openedDescriptor.close()
                descriptor = null
                throw error
            }
        }
    }

    suspend fun render(
        pageIndex: Int,
        viewportWidth: Int,
        viewportHeight: Int,
        zoom: Float,
        rotation: Int,
        fitMode: PdfFitMode,
        thumbnail: Boolean = false,
    ): Bitmap = withContext(Dispatchers.IO) {
        val widthBucket = if (thumbnail) 180 else (viewportWidth * zoom).roundToInt().coerceAtLeast(320)
        val key = "$pageIndex-$widthBucket-$viewportHeight-$rotation-$fitMode-$thumbnail"
        cache.get(key)?.takeUnless { it.isRecycled } ?: mutex.withLock {
            cache.get(key)?.takeUnless { it.isRecycled } ?: renderLocked(
                pageIndex, viewportWidth, viewportHeight, zoom, rotation, fitMode, thumbnail,
            ).also { cache.put(key, it) }
        }
    }

    suspend fun search(query: String, onPage: suspend (PdfSearchResult) -> Unit) = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < 35 || query.isBlank()) return@withContext
        mutex.withLock {
            val activeRenderer = checkNotNull(renderer)
            repeat(activeRenderer.pageCount) { index ->
                currentCoroutineContext().ensureActive()
                activeRenderer.openPage(index).use { page ->
                    val matches = page.searchText(query).size
                    if (matches > 0) onPage(PdfSearchResult(index, matches))
                }
            }
        }
    }

    private fun renderLocked(
        pageIndex: Int,
        viewportWidth: Int,
        viewportHeight: Int,
        zoom: Float,
        rotation: Int,
        fitMode: PdfFitMode,
        thumbnail: Boolean,
    ): Bitmap {
        val activeRenderer = checkNotNull(renderer) { "El PDF no está abierto" }
        require(pageIndex in 0 until activeRenderer.pageCount)
        activeRenderer.openPage(pageIndex).use { page ->
            val targetWidth = if (thumbnail) 180 else (viewportWidth * zoom).roundToInt().coerceAtLeast(320)
            val targetHeight = if (thumbnail) 260 else (viewportHeight * zoom).roundToInt().coerceAtLeast(320)
            val rawScale = when (fitMode) {
                PdfFitMode.WIDTH -> targetWidth.toFloat() / page.width
                PdfFitMode.PAGE -> min(targetWidth.toFloat() / page.width, targetHeight.toFloat() / page.height)
            }
            val maxScale = min(4096f / page.width, 4096f / page.height)
            val scale = min(rawScale, maxScale).coerceAtLeast(.1f)
            val bitmap = createBitmap(
                (page.width * scale).roundToInt().coerceAtLeast(1),
                (page.height * scale).roundToInt().coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            )
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            if (rotation % 360 == 0) return bitmap
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
                if (it !== bitmap) bitmap.recycle()
            }
        }
    }

    override fun close() {
        closeInternal()
    }

    suspend fun closeSafely() = withContext(Dispatchers.IO) { mutex.withLock { closeInternal() } }

    private fun closeInternal() {
        cache.evictAll()
        renderer?.close()
        descriptor?.close()
        renderer = null
        descriptor = null
    }
}
