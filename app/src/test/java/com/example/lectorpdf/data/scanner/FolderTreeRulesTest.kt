package com.example.lectorpdf.data.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderTreeRulesTest {
    @Test
    fun logicalPathPreservesHierarchy() {
        val root = childLogicalPath("", "Novelas")
        val category = childLogicalPath(root, "Novelas ligeras")
        val series = childLogicalPath(category, "Overlord")

        assertEquals("Novelas/Novelas ligeras/Overlord", series)
    }

    @Test
    fun providerDocumentIdsAreDeduplicatedWithinScan() {
        val tracker = FolderVisitTracker()

        assertTrue(tracker.register("root:novelas"))
        assertTrue(tracker.register("root:novelas/overlord"))
        assertFalse(tracker.register("root:novelas/overlord"))
        assertEquals(2, tracker.count())
    }
}
