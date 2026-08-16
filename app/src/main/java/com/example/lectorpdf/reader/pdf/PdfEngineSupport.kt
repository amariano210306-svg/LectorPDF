package com.example.lectorpdf.reader.pdf

import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicReference

internal class ByteLruCache<K, V>(private val maxBytes: Long) {
    init { require(maxBytes > 0) }

    private data class Entry<V>(val value: V, val sizeBytes: Long)
    private val entries = LinkedHashMap<K, Entry<V>>(16, .75f, true)
    private var storedBytes = 0L

    @Synchronized
    fun get(key: K): V? = entries[key]?.value

    @Synchronized
    fun put(key: K, value: V, sizeBytes: Long): List<V> {
        require(sizeBytes >= 0)
        val removed = mutableListOf<V>()
        entries.remove(key)?.let { previous ->
            storedBytes -= previous.sizeBytes
            removed += previous.value
        }
        entries[key] = Entry(value, sizeBytes)
        storedBytes += sizeBytes
        val iterator = entries.entries.iterator()
        while (storedBytes > maxBytes && iterator.hasNext()) {
            val eldest = iterator.next()
            iterator.remove()
            storedBytes -= eldest.value.sizeBytes
            removed += eldest.value.value
        }
        return removed
    }

    @Synchronized
    fun clear(): List<V> {
        val removed = entries.values.map(Entry<V>::value)
        entries.clear()
        storedBytes = 0
        return removed
    }

    @Synchronized fun sizeBytes(): Long = storedBytes
    @Synchronized fun count(): Int = entries.size
}

internal enum class PdfEngineLifecycle { NEW, OPEN, CLOSING, CLOSED }

internal class PdfEngineLifecycleGuard {
    private val state = AtomicReference(PdfEngineLifecycle.NEW)

    fun current(): PdfEngineLifecycle = state.get()

    fun markOpen() {
        check(state.compareAndSet(PdfEngineLifecycle.NEW, PdfEngineLifecycle.OPEN)) {
            "El motor PDF no puede abrirse desde ${state.get()}"
        }
    }

    fun requireOpen() {
        check(state.get() == PdfEngineLifecycle.OPEN) { "El motor PDF ya se está cerrando" }
    }

    fun requestClose(): Boolean {
        while (true) {
            when (val current = state.get()) {
                PdfEngineLifecycle.CLOSING, PdfEngineLifecycle.CLOSED -> return false
                PdfEngineLifecycle.NEW, PdfEngineLifecycle.OPEN ->
                    if (state.compareAndSet(current, PdfEngineLifecycle.CLOSING)) return true
            }
        }
    }

    fun markClosed() { state.set(PdfEngineLifecycle.CLOSED) }
}
