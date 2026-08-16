package com.example.lectorpdf.reader.pdf

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
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
import kotlin.math.sqrt

enum class PdfFitMode { WIDTH, PAGE }

data class PdfSearchResult(val pageIndex: Int, val matchCount: Int)

class PdfDocumentEngine(private val context: Context) : AutoCloseable {
    private var descriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private val mutex = Mutex()
    private val marginCache = mutableMapOf<Int, RectF>()
    private val memoryClassMb = (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).memoryClass
    private val cacheSizeKb = (memoryClassMb * 1024 / 8).coerceIn(32 * 1024, 96 * 1024)
    private val cache = object : LruCache<String, Bitmap>(cacheSizeKb) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount / 1024
    }

    val pageCount: Int get() = renderer?.pageCount ?: 0

    suspend fun open(uri: Uri): Int = withContext(Dispatchers.IO) {
        mutex.withLock {
            closeInternal()
            val openedDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
                ?: error("No se pudo abrir el archivo PDF")
            try {
                val openedRenderer = PdfRenderer(openedDescriptor)
                descriptor = openedDescriptor
                renderer = openedRenderer
                openedRenderer.pageCount
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
        cropMargins: Boolean,
        thumbnail: Boolean = false,
    ): Bitmap = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        val widthBucket = if (thumbnail) 176 else ((viewportWidth * zoom).roundToInt() / 32 * 32).coerceIn(320, MAX_BITMAP_DIMENSION.toInt())
        val heightBucket = if (thumbnail) 248 else ((viewportHeight * zoom).roundToInt() / 32 * 32).coerceIn(320, MAX_BITMAP_DIMENSION.toInt())
        val key = "$pageIndex-$widthBucket-$heightBucket-${rotation.normalizedRotation()}-$fitMode-$cropMargins-$thumbnail"
        cache.get(key)?.takeUnless(Bitmap::isRecycled) ?: mutex.withLock {
            currentCoroutineContext().ensureActive()
            cache.get(key)?.takeUnless(Bitmap::isRecycled) ?: renderLocked(
                pageIndex = pageIndex,
                targetWidth = widthBucket,
                targetHeight = heightBucket,
                rotation = rotation.normalizedRotation(),
                fitMode = fitMode,
                cropMargins = cropMargins,
                thumbnail = thumbnail,
            ).also { bitmap -> cache.put(key, bitmap) }
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
        targetWidth: Int,
        targetHeight: Int,
        rotation: Int,
        fitMode: PdfFitMode,
        cropMargins: Boolean,
        thumbnail: Boolean,
    ): Bitmap {
        val activeRenderer = checkNotNull(renderer) { "El PDF no está abierto" }
        require(pageIndex in 0 until activeRenderer.pageCount)
        return activeRenderer.openPage(pageIndex).use { page ->
            val crop = if (cropMargins && !thumbnail) {
                marginCache.getOrPut(pageIndex) { detectContentBounds(page) }
            } else {
                FULL_PAGE
            }
            val cropWidth = (page.width * crop.width()).coerceAtLeast(1f)
            val cropHeight = (page.height * crop.height()).coerceAtLeast(1f)
            val rotatedWidth = if (rotation == 90 || rotation == 270) cropHeight else cropWidth
            val rotatedHeight = if (rotation == 90 || rotation == 270) cropWidth else cropHeight
            val rawScale = when (fitMode) {
                PdfFitMode.WIDTH -> targetWidth / rotatedWidth
                PdfFitMode.PAGE -> min(targetWidth / rotatedWidth, targetHeight / rotatedHeight)
            }
            val dimensionLimit = min(MAX_BITMAP_DIMENSION / rotatedWidth, MAX_BITMAP_DIMENSION / rotatedHeight)
            val pixelLimit = sqrt(MAX_BITMAP_PIXELS / (rotatedWidth * rotatedHeight))
            val scale = min(rawScale, min(dimensionLimit, pixelLimit)).coerceAtLeast(.08f)
            val rawWidth = (cropWidth * scale).roundToInt().coerceAtLeast(1)
            val rawHeight = (cropHeight * scale).roundToInt().coerceAtLeast(1)
            val bitmap = createBitmap(rawWidth, rawHeight, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            val matrix = Matrix().apply {
                setScale(scale, scale)
                postTranslate(-page.width * crop.left * scale, -page.height * crop.top * scale)
            }
            page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            if (rotation == 0) return@use bitmap
            val rotationMatrix = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, rotationMatrix, true).also {
                if (it !== bitmap) bitmap.recycle()
            }
        }
    }

    private fun detectContentBounds(page: PdfRenderer.Page): RectF {
        val scale = (MARGIN_SAMPLE_WIDTH.toFloat() / page.width).coerceAtMost(1f)
        val sample = createBitmap(
            (page.width * scale).roundToInt().coerceAtLeast(1),
            (page.height * scale).roundToInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        return try {
            sample.eraseColor(Color.WHITE)
            page.render(sample, null, Matrix().apply { setScale(scale, scale) }, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            var minX = sample.width
            var minY = sample.height
            var maxX = -1
            var maxY = -1
            val pixels = IntArray(sample.width)
            var y = 0
            while (y < sample.height) {
                sample.getPixels(pixels, 0, sample.width, 0, y, sample.width, 1)
                var x = 0
                while (x < sample.width) {
                    val color = pixels[x]
                    if (Color.red(color) < WHITE_THRESHOLD || Color.green(color) < WHITE_THRESHOLD || Color.blue(color) < WHITE_THRESHOLD) {
                        minX = min(minX, x)
                        minY = min(minY, y)
                        maxX = maxOf(maxX, x)
                        maxY = maxOf(maxY, y)
                    }
                    x += 2
                }
                y += 2
            }
            if (maxX <= minX || maxY <= minY) return FULL_PAGE
            val paddingX = (sample.width * .018f).roundToInt()
            val paddingY = (sample.height * .012f).roundToInt()
            RectF(
                ((minX - paddingX).coerceAtLeast(0) / sample.width.toFloat()).coerceAtMost(.24f),
                ((minY - paddingY).coerceAtLeast(0) / sample.height.toFloat()).coerceAtMost(.24f),
                ((maxX + paddingX).coerceAtMost(sample.width - 1) / sample.width.toFloat()).coerceAtLeast(.76f),
                ((maxY + paddingY).coerceAtMost(sample.height - 1) / sample.height.toFloat()).coerceAtLeast(.76f),
            )
        } finally {
            sample.recycle()
        }
    }

    override fun close() = closeInternal()

    suspend fun closeSafely() = withContext(Dispatchers.IO) { mutex.withLock { closeInternal() } }

    private fun closeInternal() {
        cache.snapshot().values.distinct().filterNot(Bitmap::isRecycled).forEach(Bitmap::recycle)
        cache.evictAll()
        marginCache.clear()
        renderer?.close()
        descriptor?.close()
        renderer = null
        descriptor = null
    }

    private companion object {
        val FULL_PAGE = RectF(0f, 0f, 1f, 1f)
        const val MARGIN_SAMPLE_WIDTH = 240
        const val WHITE_THRESHOLD = 246
        const val MAX_BITMAP_DIMENSION = 4096f
        const val MAX_BITMAP_PIXELS = 12_000_000f
    }
}

private fun Int.normalizedRotation(): Int = ((this % 360) + 360) % 360
