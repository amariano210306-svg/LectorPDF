package com.example.lectorpdf.reader.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfEngineSupportTest {
    private data class MutablePayload(var reportedSize: Long)

    @Test
    fun cacheAccountingUsesCapturedSizeNotMutablePayloadState() {
        val cache = ByteLruCache<String, MutablePayload>(maxBytes = 12)
        val first = MutablePayload(reportedSize = 10)
        cache.put("first", first, sizeBytes = first.reportedSize)

        first.reportedSize = 0
        val second = MutablePayload(reportedSize = 5)
        val evicted = cache.put("second", second, sizeBytes = second.reportedSize)

        assertEquals(5, cache.sizeBytes())
        assertNull(cache.get("first"))
        assertSame(first, evicted.single())
        assertSame(second, cache.get("second"))
    }

    @Test
    fun cacheClearIsIdempotent() {
        val cache = ByteLruCache<String, String>(maxBytes = 100)
        cache.put("one", "value", 20)

        assertEquals(listOf("value"), cache.clear())
        assertTrue(cache.clear().isEmpty())
        assertEquals(0, cache.sizeBytes())
        assertEquals(0, cache.count())
    }

    @Test
    fun lifecycleAllowsOnlyOneCloseRequest() {
        val lifecycle = PdfEngineLifecycleGuard()
        lifecycle.markOpen()

        assertTrue(lifecycle.requestClose())
        assertFalse(lifecycle.requestClose())
        lifecycle.markClosed()
        assertFalse(lifecycle.requestClose())
        assertEquals(PdfEngineLifecycle.CLOSED, lifecycle.current())
    }
}
