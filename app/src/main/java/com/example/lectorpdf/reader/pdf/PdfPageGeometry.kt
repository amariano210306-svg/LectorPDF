package com.example.lectorpdf.reader.pdf

import kotlin.math.max
import kotlin.math.min

data class PdfPoint(val x: Float, val y: Float)

data class PdfRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
}

data class PdfNormalizedRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f,
) {
    fun sanitized(): PdfNormalizedRect {
        val l = left.coerceIn(0f, .95f)
        val t = top.coerceIn(0f, .95f)
        val r = right.coerceIn(l + .01f, 1f)
        val b = bottom.coerceIn(t + .01f, 1f)
        return PdfNormalizedRect(l, t, r, b)
    }
}

enum class PdfQuickTapAction { THEME, BOOKMARK, CENTER, NONE }

internal fun pdfQuickTapAction(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    edgeInset: Float,
    cornerSize: Float,
    quickActionsEnabled: Boolean,
    themeEnabled: Boolean,
    bookmarkEnabled: Boolean,
): PdfQuickTapAction {
    val safeCorner = cornerSize.coerceAtMost(width * .22f)
    val inTopCorner = y in edgeInset..(edgeInset + safeCorner)
    return when {
        quickActionsEnabled && themeEnabled && inTopCorner && x in edgeInset..(edgeInset + safeCorner) -> PdfQuickTapAction.THEME
        quickActionsEnabled && bookmarkEnabled && inTopCorner && x in (width - edgeInset - safeCorner)..(width - edgeInset) -> PdfQuickTapAction.BOOKMARK
        x in width * .22f..width * .78f && y in height * .15f..height * .85f -> PdfQuickTapAction.CENTER
        else -> PdfQuickTapAction.NONE
    }
}

data class PdfPageTransform(
    val pageWidth: Float,
    val pageHeight: Float,
    val crop: PdfNormalizedRect = PdfNormalizedRect(),
    val rotation: Int = 0,
    val viewportWidth: Float,
    val viewportHeight: Float,
    val fitMode: PdfFitMode,
    val zoom: Float = 1f,
    val pan: PdfPoint = PdfPoint(0f, 0f),
    val horizontalMargin: Float = 0f,
) {
    init {
        require(pageWidth > 0f && pageHeight > 0f)
        require(viewportWidth > 0f && viewportHeight > 0f)
    }

    private val safeCrop = crop.sanitized()
    private val cropLeft = safeCrop.left * pageWidth
    private val cropTop = safeCrop.top * pageHeight
    private val cropWidth = (safeCrop.right - safeCrop.left) * pageWidth
    private val cropHeight = (safeCrop.bottom - safeCrop.top) * pageHeight
    private val normalizedRotation = ((rotation % 360) + 360) % 360
    private val rotatedWidth = if (normalizedRotation == 90 || normalizedRotation == 270) cropHeight else cropWidth
    private val rotatedHeight = if (normalizedRotation == 90 || normalizedRotation == 270) cropWidth else cropHeight
    val baseScale: Float = pdfBaseScale(
        contentWidth = rotatedWidth,
        contentHeight = rotatedHeight,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
        fitMode = fitMode,
        horizontalMargin = horizontalMargin,
    )
    val displayedWidth: Float = rotatedWidth * baseScale
    val displayedHeight: Float = rotatedHeight * baseScale

    fun pdfToViewport(point: PdfPoint): PdfPoint {
        val localX = point.x - cropLeft
        val localY = point.y - cropTop
        val rotated = when (normalizedRotation) {
            90 -> PdfPoint(cropHeight - localY, localX)
            180 -> PdfPoint(cropWidth - localX, cropHeight - localY)
            270 -> PdfPoint(localY, cropWidth - localX)
            else -> PdfPoint(localX, localY)
        }
        val base = PdfPoint(
            (viewportWidth - displayedWidth) / 2f + rotated.x * baseScale,
            (viewportHeight - displayedHeight) / 2f + rotated.y * baseScale,
        )
        val center = PdfPoint(viewportWidth / 2f, viewportHeight / 2f)
        return PdfPoint(
            (base.x - center.x) * zoom + center.x + pan.x,
            (base.y - center.y) * zoom + center.y + pan.y,
        )
    }

    fun viewportToPdf(point: PdfPoint): PdfPoint {
        val center = PdfPoint(viewportWidth / 2f, viewportHeight / 2f)
        val baseX = (point.x - pan.x - center.x) / zoom.coerceAtLeast(.01f) + center.x
        val baseY = (point.y - pan.y - center.y) / zoom.coerceAtLeast(.01f) + center.y
        val rotatedX = (baseX - (viewportWidth - displayedWidth) / 2f) / baseScale
        val rotatedY = (baseY - (viewportHeight - displayedHeight) / 2f) / baseScale
        val local = when (normalizedRotation) {
            90 -> PdfPoint(rotatedY, cropHeight - rotatedX)
            180 -> PdfPoint(cropWidth - rotatedX, cropHeight - rotatedY)
            270 -> PdfPoint(cropWidth - rotatedY, rotatedX)
            else -> PdfPoint(rotatedX, rotatedY)
        }
        return PdfPoint(
            (local.x + cropLeft).coerceIn(cropLeft, cropLeft + cropWidth),
            (local.y + cropTop).coerceIn(cropTop, cropTop + cropHeight),
        )
    }

    fun pdfRectToViewport(rect: PdfRect): PdfRect {
        val points = listOf(
            pdfToViewport(PdfPoint(rect.left, rect.top)),
            pdfToViewport(PdfPoint(rect.right, rect.top)),
            pdfToViewport(PdfPoint(rect.right, rect.bottom)),
            pdfToViewport(PdfPoint(rect.left, rect.bottom)),
        )
        return PdfRect(
            points.minOf(PdfPoint::x),
            points.minOf(PdfPoint::y),
            points.maxOf(PdfPoint::x),
            points.maxOf(PdfPoint::y),
        )
    }

    fun normalizePdfRect(rect: PdfRect): PdfRect = PdfRect(
        left = (rect.left / pageWidth).coerceIn(0f, 1f),
        top = (rect.top / pageHeight).coerceIn(0f, 1f),
        right = (rect.right / pageWidth).coerceIn(0f, 1f),
        bottom = (rect.bottom / pageHeight).coerceIn(0f, 1f),
    )
}

internal fun pdfBaseScale(
    contentWidth: Float,
    contentHeight: Float,
    viewportWidth: Float,
    viewportHeight: Float,
    fitMode: PdfFitMode,
    horizontalMargin: Float = 0f,
): Float {
    val availableWidth = (viewportWidth - horizontalMargin * 2f).coerceAtLeast(1f)
    val widthScale = availableWidth / contentWidth.coerceAtLeast(1f)
    return when (fitMode) {
        PdfFitMode.PAGE -> min(widthScale, viewportHeight / contentHeight.coerceAtLeast(1f))
        PdfFitMode.WIDTH, PdfFitMode.CONTENT -> widthScale
    }.coerceAtLeast(.01f)
}

internal fun combineCrop(
    automatic: PdfNormalizedRect,
    manual: PdfCropInsets,
): PdfNormalizedRect {
    val base = automatic.sanitized()
    val insets = manual.normalized()
    return PdfNormalizedRect(
        left = min(base.left + insets.left, .46f),
        top = min(base.top + insets.top, .46f),
        right = max(base.right - insets.right, .54f),
        bottom = max(base.bottom - insets.bottom, .54f),
    ).sanitized()
}

internal fun conservativeDetectedCrop(left: Float, top: Float, right: Float, bottom: Float): PdfNormalizedRect {
    val candidate = PdfNormalizedRect(left, top, right, bottom).sanitized()
    val safeLeft = candidate.left.coerceAtMost(.24f)
    val safeTop = candidate.top.coerceAtMost(.24f)
    val safeRight = candidate.right.coerceAtLeast(.76f)
    val safeBottom = candidate.bottom.coerceAtLeast(.76f)
    return PdfNormalizedRect(safeLeft, safeTop, safeRight, safeBottom).sanitized()
}
