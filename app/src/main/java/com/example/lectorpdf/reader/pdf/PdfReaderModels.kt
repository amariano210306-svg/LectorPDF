package com.example.lectorpdf.reader.pdf

import kotlin.math.roundToInt

enum class PdfReaderTheme { DAY, NIGHT, SEPIA, CONSOLE }
enum class PdfCropMode { NONE, AUTOMATIC, MANUAL }

data class PdfCropInsets(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f,
) {
    fun normalized(): PdfCropInsets {
        val safeLeft = left.coerceIn(0f, MAX_SIDE)
        val safeRight = right.coerceIn(0f, MAX_SIDE)
        val safeTop = top.coerceIn(0f, MAX_SIDE)
        val safeBottom = bottom.coerceIn(0f, MAX_SIDE)
        return copy(left = safeLeft, top = safeTop, right = safeRight, bottom = safeBottom)
    }

    val isEmpty: Boolean get() = left == 0f && top == 0f && right == 0f && bottom == 0f

    companion object { const val MAX_SIDE = .30f }
}

data class PdfBookmarkLocator(val page: Int, val offsetFraction: Float)

internal object PdfBookmarkCodec {
    private val pagePattern = Regex("\\\"page\\\"\\s*:\\s*(\\d+)")
    private val offsetPattern = Regex("\\\"offset\\\"\\s*:\\s*([0-9.]+)")

    fun encode(page: Int, offsetFraction: Float): String =
        "{\"page\":${page.coerceAtLeast(0)},\"offset\":${(offsetFraction.coerceIn(0f, 1f) * 10000).roundToInt() / 10000f}}"

    fun decode(value: String): PdfBookmarkLocator? {
        val page = pagePattern.find(value)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val offset = offsetPattern.find(value)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
        return PdfBookmarkLocator(page, offset.coerceIn(0f, 1f))
    }
}

internal fun splitPdfSpeechText(text: String, maxLength: Int = 2_800): List<String> {
    if (text.isBlank()) return emptyList()
    val paragraphs = text.replace("\r", "").split(Regex("\\n+"))
    val result = mutableListOf<String>()
    paragraphs.forEach { raw ->
        var remaining = raw.trim()
        while (remaining.isNotEmpty()) {
            if (remaining.length <= maxLength) {
                result += remaining
                remaining = ""
            } else {
                val boundary = remaining.lastIndexOfAny(charArrayOf('.', '!', '?', ';', ' '), startIndex = maxLength)
                    .takeIf { it >= maxLength / 2 } ?: maxLength
                result += remaining.substring(0, boundary + 1).trim()
                remaining = remaining.substring(boundary + 1).trim()
            }
        }
    }
    return result.filter(String::isNotBlank)
}
