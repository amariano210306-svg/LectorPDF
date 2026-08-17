package com.example.lectorpdf.reader.pdf

enum class PdfAnnotationStyle { HIGHLIGHT, UNDERLINE, QUOTE, NOTE }

data class PdfAnnotationLocator(
    val page: Int,
    val offsetFraction: Float,
    val style: PdfAnnotationStyle,
    val rects: List<PdfRect>,
)

internal object PdfAnnotationCodec {
    private val pagePattern = Regex("\\\"page\\\"\\s*:\\s*(\\d+)")
    private val offsetPattern = Regex("\\\"offset\\\"\\s*:\\s*([0-9.]+)")
    private val stylePattern = Regex("\\\"style\\\"\\s*:\\s*\\\"([A-Z_]+)\\\"")
    private val rectsPattern = Regex("\\\"rects\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"")

    fun encode(locator: PdfAnnotationLocator): String {
        val rects = locator.rects.joinToString(";") { rect ->
            listOf(rect.left, rect.top, rect.right, rect.bottom)
                .joinToString(",") { ((it.coerceIn(0f, 1f) * 100_000).toInt() / 100_000f).toString() }
        }
        val offset = ((locator.offsetFraction.coerceIn(0f, 1f) * 10_000).toInt() / 10_000f)
        return "{\"page\":${locator.page.coerceAtLeast(0)},\"offset\":$offset,\"style\":\"${locator.style.name}\",\"rects\":\"$rects\"}"
    }

    fun decode(value: String): PdfAnnotationLocator? {
        val page = pagePattern.find(value)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val offset = offsetPattern.find(value)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
        val style = stylePattern.find(value)?.groupValues?.get(1)
            ?.let { raw -> PdfAnnotationStyle.entries.firstOrNull { it.name == raw } }
            ?: PdfAnnotationStyle.HIGHLIGHT
        val rects = rectsPattern.find(value)?.groupValues?.get(1).orEmpty()
            .split(';')
            .mapNotNull { encoded ->
                val values = encoded.split(',').mapNotNull(String::toFloatOrNull)
                if (values.size == 4) PdfRect(values[0], values[1], values[2], values[3]) else null
            }
        if (rects.isEmpty()) return null
        return PdfAnnotationLocator(page, offset.coerceIn(0f, 1f), style, rects)
    }
}

internal fun PdfRect.fromNormalized(pageWidth: Float, pageHeight: Float): PdfRect = PdfRect(
    left * pageWidth,
    top * pageHeight,
    right * pageWidth,
    bottom * pageHeight,
)
