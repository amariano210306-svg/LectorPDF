package com.example.lectorpdf.reader.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PdfBookmarkCodecTest {
    @Test fun locatorRoundTripsPageAndOffset() {
        val locator = PdfBookmarkCodec.decode(PdfBookmarkCodec.encode(42, .375f))
        assertEquals(42, locator?.page)
        assertEquals(.375f, locator?.offsetFraction ?: -1f, .0001f)
    }

    @Test fun malformedLocatorIsRejected() {
        assertNull(PdfBookmarkCodec.decode("not-json"))
    }

    @Test fun offsetIsClamped() {
        assertEquals(1f, PdfBookmarkCodec.decode("{\"page\":1,\"offset\":8}")?.offsetFraction ?: -1f, 0f)
    }

    @Test fun speechTextIsSplitWithoutLosingOrder() {
        val chunks = splitPdfSpeechText("Primera frase. Segunda frase extensa.", maxLength = 18)
        assertEquals(listOf("Primera frase.", "Segunda frase", "extensa."), chunks)
    }
}
