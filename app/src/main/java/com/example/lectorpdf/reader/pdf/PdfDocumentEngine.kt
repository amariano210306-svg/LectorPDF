package com.example.lectorpdf.reader.pdf

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Point
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.graphics.pdf.models.selection.SelectionBoundary
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class PdfFitMode { WIDTH, PAGE, CONTENT }

data class PdfSearchResult(val pageIndex: Int, val matchCount: Int, val snippet: String? = null)
data class PdfPageInfo(val width: Float, val height: Float, val crop: PdfNormalizedRect)
data class PdfTextSelection(
    val pageIndex: Int,
    val text: String,
    val bounds: List<PdfRect>,
    val start: PdfPoint,
    val stop: PdfPoint,
)

class PdfDocumentEngine(private val context: Context) {
    private var renderer: PdfRenderer? = null
    private val mutex = Mutex()
    private val lifecycle = PdfEngineLifecycleGuard()
    private val marginCache = mutableMapOf<Int, RectF>()
    private val memoryClassMb = (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).memoryClass
    private val cacheSizeBytes = (memoryClassMb.toLong() * 1024 * 1024 / 8)
        .coerceIn(32L * 1024 * 1024, 96L * 1024 * 1024)
    private val cache = ByteLruCache<String, Bitmap>(cacheSizeBytes)

    val pageCount: Int get() = renderer?.pageCount ?: 0

    suspend fun open(uri: Uri): Int = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(lifecycle.current() == PdfEngineLifecycle.NEW) { "Este motor PDF ya fue utilizado" }
            debugLog("open requested")
            val openedDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
                ?: error("No se pudo abrir el archivo PDF")
            try {
                val openedRenderer = PdfRenderer(openedDescriptor)
                renderer = openedRenderer
                lifecycle.markOpen()
                debugLog("renderer opened pages=${openedRenderer.pageCount}")
                openedRenderer.pageCount
            } catch (error: Throwable) {
                openedDescriptor.close()
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
        cropMode: PdfCropMode,
        manualCrop: PdfCropInsets = PdfCropInsets(),
        thumbnail: Boolean = false,
    ): Bitmap = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        val widthBucket = if (thumbnail) 176 else ((viewportWidth * zoom).roundToInt() / 32 * 32).coerceIn(320, MAX_BITMAP_DIMENSION.toInt())
        val heightBucket = if (thumbnail) 248 else ((viewportHeight * zoom).roundToInt() / 32 * 32).coerceIn(320, MAX_BITMAP_DIMENSION.toInt())
        val safeManualCrop = manualCrop.normalized()
        val key = "$pageIndex-$widthBucket-$heightBucket-${rotation.normalizedRotation()}-$fitMode-$cropMode-$safeManualCrop-$thumbnail"
        mutex.withLock {
            lifecycle.requireOpen()
            currentCoroutineContext().ensureActive()
            cache.get(key)?.takeUnless(Bitmap::isRecycled) ?: renderLocked(
                pageIndex = pageIndex,
                targetWidth = widthBucket,
                targetHeight = heightBucket,
                rotation = rotation.normalizedRotation(),
                fitMode = fitMode,
                cropMode = cropMode,
                manualCrop = safeManualCrop,
                thumbnail = thumbnail,
            ).also { bitmap ->
                currentCoroutineContext().ensureActive()
                cache.put(key, bitmap, bitmap.allocationByteCount.toLong())
                debugLog("cache insert page=$pageIndex bytes=${bitmap.allocationByteCount}")
            }
        }
    }

    suspend fun search(query: String, onPage: suspend (PdfSearchResult) -> Unit) = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < 35 || query.isBlank()) return@withContext
        mutex.withLock {
            lifecycle.requireOpen()
            val activeRenderer = checkNotNull(renderer)
            repeat(activeRenderer.pageCount) { index ->
                currentCoroutineContext().ensureActive()
                activeRenderer.openPage(index).use { page ->
                    val matches = page.searchText(query).size
                    if (matches > 0) {
                        val pageText = page.textContents.joinToString(" ") { it.text }.replace(Regex("\\s+"), " ")
                        val matchStart = pageText.indexOf(query, ignoreCase = true)
                        val snippet = if (matchStart >= 0) {
                            pageText.substring((matchStart - 45).coerceAtLeast(0), (matchStart + query.length + 75).coerceAtMost(pageText.length))
                        } else null
                        onPage(PdfSearchResult(index, matches, snippet))
                    }
                }
            }
        }
    }

    suspend fun extractPageText(pageIndex: Int): String? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < 35) return@withContext null
        mutex.withLock {
            lifecycle.requireOpen()
            val activeRenderer = checkNotNull(renderer)
            if (pageIndex !in 0 until activeRenderer.pageCount) return@withLock null
            currentCoroutineContext().ensureActive()
            activeRenderer.openPage(pageIndex).use { page ->
                page.textContents.joinToString("\n") { it.text }.trim().ifBlank { null }
            }
        }
    }

    suspend fun pageInfo(
        pageIndex: Int,
        fitMode: PdfFitMode,
        cropMode: PdfCropMode,
        manualCrop: PdfCropInsets,
    ): PdfPageInfo = withContext(Dispatchers.IO) {
        mutex.withLock {
            lifecycle.requireOpen()
            val activeRenderer = checkNotNull(renderer)
            require(pageIndex in 0 until activeRenderer.pageCount)
            activeRenderer.openPage(pageIndex).use { page ->
                val crop = resolveCropLocked(pageIndex, page, fitMode, cropMode, manualCrop)
                PdfPageInfo(page.width.toFloat(), page.height.toFloat(), crop)
            }
        }
    }

    suspend fun selectText(pageIndex: Int, start: PdfPoint, stop: PdfPoint): PdfTextSelection? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < 35) return@withContext null
        mutex.withLock {
            lifecycle.requireOpen()
            selectTextApi35(pageIndex, start, stop)
        }
    }

    @androidx.annotation.RequiresApi(35)
    private fun selectTextApi35(pageIndex: Int, start: PdfPoint, stop: PdfPoint): PdfTextSelection? {
        val activeRenderer = checkNotNull(renderer)
        if (pageIndex !in 0 until activeRenderer.pageCount) return null
        return activeRenderer.openPage(pageIndex).use { page ->
            val selection = page.selectContent(
                SelectionBoundary(Point(start.x.roundToInt(), start.y.roundToInt())),
                SelectionBoundary(Point(stop.x.roundToInt(), stop.y.roundToInt())),
            ) ?: return@use null
            val contents = selection.selectedTextContents
            val text = contents.joinToString("\n") { it.text }.trim()
            val bounds = contents.flatMap { content ->
                content.bounds.map { PdfRect(it.left, it.top, it.right, it.bottom) }
            }
            if (text.isBlank() || bounds.isEmpty()) null else PdfTextSelection(
                pageIndex = pageIndex,
                text = text,
                bounds = bounds,
                start = selection.start.point?.let { PdfPoint(it.x.toFloat(), it.y.toFloat()) } ?: start,
                stop = selection.stop.point?.let { PdfPoint(it.x.toFloat(), it.y.toFloat()) } ?: stop,
            )
        }
    }

    private fun renderLocked(
        pageIndex: Int,
        targetWidth: Int,
        targetHeight: Int,
        rotation: Int,
        fitMode: PdfFitMode,
        cropMode: PdfCropMode,
        manualCrop: PdfCropInsets,
        thumbnail: Boolean,
    ): Bitmap {
        val activeRenderer = checkNotNull(renderer) { "El PDF no está abierto" }
        require(pageIndex in 0 until activeRenderer.pageCount)
        return activeRenderer.openPage(pageIndex).use { page ->
            val cropNormalized = if (thumbnail) PdfNormalizedRect() else resolveCropLocked(pageIndex, page, fitMode, cropMode, manualCrop)
            val crop = RectF(cropNormalized.left, cropNormalized.top, cropNormalized.right, cropNormalized.bottom)
            val cropWidth = (page.width * crop.width()).coerceAtLeast(1f)
            val cropHeight = (page.height * crop.height()).coerceAtLeast(1f)
            val rotatedWidth = if (rotation == 90 || rotation == 270) cropHeight else cropWidth
            val rotatedHeight = if (rotation == 90 || rotation == 270) cropWidth else cropHeight
            val rawScale = when (fitMode) {
                PdfFitMode.WIDTH, PdfFitMode.CONTENT -> targetWidth / rotatedWidth
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

    private fun resolveCropLocked(
        pageIndex: Int,
        page: PdfRenderer.Page,
        fitMode: PdfFitMode,
        cropMode: PdfCropMode,
        manualCrop: PdfCropInsets,
    ): PdfNormalizedRect {
        val effectiveMode = if (fitMode == PdfFitMode.CONTENT && cropMode == PdfCropMode.NONE) {
            PdfCropMode.AUTOMATIC
        } else cropMode
        val automatic = if (effectiveMode == PdfCropMode.AUTOMATIC) {
            marginCache.getOrPut(pageIndex) { detectContentBounds(page) }.let {
                PdfNormalizedRect(it.left, it.top, it.right, it.bottom)
            }
        } else PdfNormalizedRect()
        return when (effectiveMode) {
            PdfCropMode.NONE -> PdfNormalizedRect()
            PdfCropMode.AUTOMATIC -> automatic
            PdfCropMode.MANUAL -> combineCrop(PdfNormalizedRect(), manualCrop)
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
            val cornerInset = 3.coerceAtMost((minOf(sample.width, sample.height) - 1).coerceAtLeast(0))
            val backgroundSamples = intArrayOf(
                sample[cornerInset, cornerInset],
                sample[(sample.width - 1 - cornerInset).coerceAtLeast(0), cornerInset],
                sample[cornerInset, (sample.height - 1 - cornerInset).coerceAtLeast(0)],
                sample[(sample.width - 1 - cornerInset).coerceAtLeast(0), (sample.height - 1 - cornerInset).coerceAtLeast(0)],
            )
            val background = averageColor(backgroundSamples)
            if (backgroundSamples.any { colorDistance(it, background) > 58 }) return FULL_PAGE
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
                    if (colorDistance(color, background) >= CONTENT_DISTANCE_THRESHOLD) {
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
            val detected = conservativeDetectedCrop(
                ((minX - paddingX).coerceAtLeast(0) / sample.width.toFloat()).coerceAtMost(.24f),
                ((minY - paddingY).coerceAtLeast(0) / sample.height.toFloat()).coerceAtMost(.24f),
                ((maxX + paddingX).coerceAtMost(sample.width - 1) / sample.width.toFloat()).coerceAtLeast(.76f),
                ((maxY + paddingY).coerceAtMost(sample.height - 1) / sample.height.toFloat()).coerceAtLeast(.76f),
            )
            RectF(detected.left, detected.top, detected.right, detected.bottom)
        } finally {
            sample.recycle()
        }
    }

    suspend fun closeSafely() = withContext(Dispatchers.IO) {
        val ownsClose = lifecycle.requestClose()
        debugLog("close requested ownsClose=$ownsClose")
        mutex.withLock {
            if (lifecycle.current() == PdfEngineLifecycle.CLOSED) return@withLock
            if (!ownsClose && lifecycle.current() != PdfEngineLifecycle.CLOSING) return@withLock
            closeInternal()
        }
    }

    private fun closeInternal() {
        val activeRenderer = renderer
        renderer = null
        cache.clear()
        marginCache.clear()
        debugLog("cache cleared")
        try {
            // PdfRenderer owns and closes the ParcelFileDescriptor supplied to its constructor.
            activeRenderer?.close()
            debugLog("renderer and owned descriptor closed")
        } finally {
            lifecycle.markClosed()
        }
    }

    private fun debugLog(message: String) {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) Log.d(TAG, message)
    }

    private companion object {
        val FULL_PAGE = RectF(0f, 0f, 1f, 1f)
        const val MARGIN_SAMPLE_WIDTH = 240
        const val CONTENT_DISTANCE_THRESHOLD = 26
        const val MAX_BITMAP_DIMENSION = 4096f
        const val MAX_BITMAP_PIXELS = 12_000_000f
        const val TAG = "PdfDocumentEngine"
    }
}

private fun Int.normalizedRotation(): Int = ((this % 360) + 360) % 360

private fun averageColor(colors: IntArray): Int = Color.rgb(
    colors.map(Color::red).average().roundToInt(),
    colors.map(Color::green).average().roundToInt(),
    colors.map(Color::blue).average().roundToInt(),
)

private fun colorDistance(first: Int, second: Int): Int = maxOf(
    kotlin.math.abs(Color.red(first) - Color.red(second)),
    kotlin.math.abs(Color.green(first) - Color.green(second)),
    kotlin.math.abs(Color.blue(first) - Color.blue(second)),
)
