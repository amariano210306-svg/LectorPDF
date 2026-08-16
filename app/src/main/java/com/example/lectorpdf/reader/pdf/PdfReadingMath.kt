package com.example.lectorpdf.reader.pdf

import kotlin.math.abs

internal data class PdfVisiblePage(
    val index: Int,
    val offset: Int,
    val size: Int,
) {
    fun visiblePixels(viewportStart: Int, viewportEnd: Int): Int =
        (minOf(offset + size, viewportEnd) - maxOf(offset, viewportStart)).coerceAtLeast(0)
}

internal data class PdfReadingPosition(val page: Int, val offsetFraction: Float)

internal fun dominantReadingPosition(
    viewportStart: Int,
    viewportEnd: Int,
    pages: List<PdfVisiblePage>,
): PdfReadingPosition? {
    val viewportCenter = (viewportStart + viewportEnd) / 2
    val dominant = pages.maxWithOrNull(
        compareBy<PdfVisiblePage> { it.visiblePixels(viewportStart, viewportEnd) }
            .thenBy { -abs((it.offset + it.size / 2) - viewportCenter) },
    ) ?: return null
    val offset = ((viewportStart - dominant.offset).coerceAtLeast(0) / dominant.size.coerceAtLeast(1).toFloat())
        .coerceIn(0f, 1f)
    return PdfReadingPosition(dominant.index, offset)
}

internal fun pdfProgress(page: Int, offsetFraction: Float, pageCount: Int): Float =
    if (pageCount <= 0) 0f else ((page + offsetFraction.coerceIn(0f, 1f)) / pageCount).coerceIn(0f, 1f)
