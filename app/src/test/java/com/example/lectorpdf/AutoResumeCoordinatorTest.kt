package com.example.lectorpdf

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoResumeCoordinatorTest {
    @Test
    fun automaticResumeCanOnlyBeConsumedOncePerProcess() {
        val coordinator = AutoResumeCoordinator()

        assertTrue(coordinator.tryAcquire(skip = false))
        assertFalse(coordinator.tryAcquire(skip = false))
        assertTrue(coordinator.wasConsumed())
    }

    @Test
    fun returnToHomeIntentNeverAcquiresAutomaticResume() {
        val coordinator = AutoResumeCoordinator()

        assertFalse(coordinator.tryAcquire(skip = true))
        assertFalse(coordinator.wasConsumed())
    }
}
