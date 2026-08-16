package com.example.lectorpdf

import java.util.concurrent.atomic.AtomicBoolean

internal class AutoResumeCoordinator {
    private val consumed = AtomicBoolean(false)

    fun tryAcquire(skip: Boolean): Boolean = !skip && consumed.compareAndSet(false, true)
    fun wasConsumed(): Boolean = consumed.get()
}
