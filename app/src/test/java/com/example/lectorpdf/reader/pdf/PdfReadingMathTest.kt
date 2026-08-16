package com.example.lectorpdf.reader.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PdfReadingMathTest {
    @Test
    fun dominantPageUsesLargestVisibleArea() {
        val position = dominantReadingPosition(
            viewportStart = 0,
            viewportEnd = 1_000,
            pages = listOf(
                PdfVisiblePage(index = 4, offset = -700, size = 1_000),
                PdfVisiblePage(index = 5, offset = 308, size = 1_000),
            ),
        )

        assertEquals(5, position?.page)
        assertEquals(0f, position?.offsetFraction ?: -1f, .001f)
    }

    @Test
    fun offsetIsRelativeToDominantPage() {
        val position = dominantReadingPosition(
            viewportStart = 0,
            viewportEnd = 800,
            pages = listOf(PdfVisiblePage(index = 12, offset = -250, size = 1_000)),
        )

        assertEquals(12, position?.page)
        assertEquals(.25f, position?.offsetFraction ?: -1f, .001f)
    }

    @Test
    fun progressIncludesOffsetAndHandlesEmptyDocuments() {
        assertEquals(.525f, pdfProgress(page = 5, offsetFraction = .25f, pageCount = 10), .0001f)
        assertEquals(0f, pdfProgress(page = 0, offsetFraction = 0f, pageCount = 0), 0f)
        assertNull(dominantReadingPosition(0, 100, emptyList()))
    }
}
