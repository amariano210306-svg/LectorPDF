package com.example.lectorpdf.reader.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PdfAnnotationCodecTest {
    @Test
    fun locatorRoundTripPreservesNormalizedGeometryAndStyle() {
        val source = PdfAnnotationLocator(
            page = 7,
            offsetFraction = .34789f,
            style = PdfAnnotationStyle.UNDERLINE,
            rects = listOf(PdfRect(.1f, .2f, .45f, .24f), PdfRect(.11f, .25f, .7f, .29f)),
        )
        val restored = PdfAnnotationCodec.decode(PdfAnnotationCodec.encode(source))
        assertNotNull(restored)
        assertEquals(source.page, restored?.page)
        assertEquals(source.style, restored?.style)
        assertEquals(2, restored?.rects?.size)
        assertEquals(source.rects.first().left, restored!!.rects.first().left, .00002f)
    }
}
