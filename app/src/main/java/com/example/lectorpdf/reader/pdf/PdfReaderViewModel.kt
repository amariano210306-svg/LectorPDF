package com.example.lectorpdf.reader.pdf

import android.app.Application
import android.graphics.Bitmap
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.core.net.toUri
import com.example.lectorpdf.LectorApplication
import com.example.lectorpdf.data.local.dao.BookDao
import com.example.lectorpdf.data.local.dao.ReadingDao
import com.example.lectorpdf.data.local.entity.ReadingSessionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

enum class PdfPageDirection { VERTICAL, HORIZONTAL }

data class PdfReaderUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val title: String = "",
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    val zoom: Float = 1f,
    val rotation: Int = 0,
    val fitMode: PdfFitMode = PdfFitMode.PAGE,
    val direction: PdfPageDirection = PdfPageDirection.VERTICAL,
    val controlsVisible: Boolean = true,
    val focusMode: Boolean = false,
    val brightness: Float = -1f,
    val pages: Map<Int, Bitmap> = emptyMap(),
    val thumbnails: Map<Int, Bitmap> = emptyMap(),
    val searchQuery: String = "",
    val searchResults: List<PdfSearchResult> = emptyList(),
    val searching: Boolean = false,
) {
    val searchSupported: Boolean get() = Build.VERSION.SDK_INT >= 35
}

class PdfReaderViewModel(
    private val bookId: Long,
    private val engine: PdfDocumentEngine,
    private val bookDao: BookDao,
    private val readingDao: ReadingDao,
    private val applicationScope: CoroutineScope,
) : ViewModel() {
    private val mutableState = MutableStateFlow(PdfReaderUiState())
    val uiState: StateFlow<PdfReaderUiState> = mutableState
    private val rendering = ConcurrentHashMap.newKeySet<String>()
    private var progressJob: Job? = null
    private var searchJob: Job? = null
    private var sessionStartedAt: Long? = null
    private var sessionStartProgress = 0f
    private var previousStatus = "UNREAD"

    init {
        viewModelScope.launch {
            runCatching {
                val book = checkNotNull(bookDao.findById(bookId)) { "El libro ya no está en la biblioteca" }
                val progress = bookDao.findProgress(bookId)
                previousStatus = progress?.status ?: "UNREAD"
                val count = engine.open(book.uri.toUri())
                require(count > 0) { "El PDF no contiene páginas legibles" }
                mutableState.update {
                    it.copy(
                        loading = false,
                        title = book.title,
                        pageCount = count,
                        currentPage = (progress?.currentPage ?: 0).coerceIn(0, count - 1),
                        zoom = (progress?.zoom ?: 1f).coerceIn(1f, 4f),
                    )
                }
                beginSession()
            }.onFailure { error -> mutableState.update { it.copy(loading = false, error = error.message ?: "No se pudo abrir el PDF") } }
        }
    }

    fun requestPage(page: Int, width: Int, height: Int, thumbnail: Boolean = false) {
        val state = mutableState.value
        if (page !in 0 until state.pageCount || width <= 0 || height <= 0) return
        val key = "$page-${state.zoom}-${state.rotation}-${state.fitMode}-$thumbnail-$width-$height"
        if (!rendering.add(key)) return
        viewModelScope.launch {
            runCatching {
                engine.render(
                    pageIndex = page,
                    viewportWidth = width,
                    viewportHeight = height,
                    zoom = if (thumbnail) 1f else state.zoom,
                    rotation = state.rotation,
                    fitMode = if (thumbnail) PdfFitMode.PAGE else state.fitMode,
                    thumbnail = thumbnail,
                )
            }.onSuccess { bitmap ->
                mutableState.update { current ->
                    if (thumbnail) {
                        val limited = (current.thumbnails + (page to bitmap)).entries.toList().takeLast(60).associate { it.toPair() }
                        current.copy(thumbnails = limited)
                    } else {
                        val nearby = (current.pages + (page to bitmap)).filterKeys { kotlin.math.abs(it - current.currentPage) <= 2 }
                        current.copy(pages = nearby)
                    }
                }
            }
            rendering.remove(key)
        }
    }

    fun goToPage(page: Int) {
        val safePage = page.coerceIn(0, (mutableState.value.pageCount - 1).coerceAtLeast(0))
        if (safePage == mutableState.value.currentPage) return
        mutableState.update { it.copy(currentPage = safePage) }
        scheduleProgressSave()
    }

    fun nextPage() = goToPage(mutableState.value.currentPage + 1)
    fun previousPage() = goToPage(mutableState.value.currentPage - 1)

    fun setZoom(value: Float) {
        val zoom = value.coerceIn(1f, 4f)
        if (zoom == mutableState.value.zoom) return
        mutableState.update { it.copy(zoom = zoom) }
        scheduleProgressSave()
    }

    fun rotate() = mutableState.update { it.copy(rotation = (it.rotation + 90) % 360, pages = emptyMap(), thumbnails = emptyMap()) }
    fun setFitMode(value: PdfFitMode) = mutableState.update { it.copy(fitMode = value, zoom = 1f, pages = emptyMap()) }
    fun setDirection(value: PdfPageDirection) = mutableState.update { it.copy(direction = value, zoom = 1f, pages = emptyMap()) }
    fun setBrightness(value: Float) = mutableState.update {
        it.copy(brightness = if (value < 0f) -1f else value.coerceIn(.05f, 1f))
    }
    fun toggleControls() = mutableState.update { if (it.focusMode) it else it.copy(controlsVisible = !it.controlsVisible) }
    fun hideControls() = mutableState.update { it.copy(controlsVisible = false) }
    fun toggleFocusMode() = mutableState.update { it.copy(focusMode = !it.focusMode, controlsVisible = it.focusMode) }

    fun search(query: String) {
        mutableState.update { it.copy(searchQuery = query, searchResults = emptyList()) }
        searchJob?.cancel()
        if (query.isBlank() || Build.VERSION.SDK_INT < 35) return
        searchJob = viewModelScope.launch {
            mutableState.update { it.copy(searching = true) }
            runCatching {
                engine.search(query) { result -> mutableState.update { it.copy(searchResults = it.searchResults + result) } }
            }
            mutableState.update { it.copy(searching = false) }
        }
    }

    fun beginSession() {
        if (sessionStartedAt != null || mutableState.value.loading) return
        sessionStartedAt = System.currentTimeMillis()
        sessionStartProgress = progressFraction()
    }

    fun endSession() {
        val start = sessionStartedAt ?: return
        sessionStartedAt = null
        val end = System.currentTimeMillis()
        val duration = end - start
        if (duration < 10_000) return
        val endProgress = progressFraction()
        applicationScope.launch {
            readingDao.insertSession(ReadingSessionEntity(bookId = bookId, startedAt = start, endedAt = end, durationMillis = duration, startProgress = sessionStartProgress, endProgress = endProgress))
            bookDao.addReadingTime(bookId, duration)
        }
    }

    fun flushProgress() {
        progressJob?.cancel()
        saveProgress()
    }

    private fun scheduleProgressSave() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch { delay(350); saveProgress() }
    }

    private fun saveProgress() {
        val state = mutableState.value
        if (state.pageCount <= 0) return
        val fraction = progressFraction()
        val status = when {
            previousStatus == "FINISHED" -> "FINISHED"
            fraction > 0f -> "READING"
            else -> previousStatus
        }
        applicationScope.launch {
            bookDao.updatePdfProgress(bookId, state.currentPage, state.pageCount, fraction, state.zoom, System.currentTimeMillis(), status)
        }
    }

    private fun progressFraction(): Float {
        val state = mutableState.value
        return if (state.pageCount == 0) 0f else ((state.currentPage + 1f) / state.pageCount).coerceIn(0f, 1f)
    }

    override fun onCleared() {
        endSession()
        saveProgress()
        applicationScope.launch { engine.closeSafely() }
    }

    class Factory(private val application: Application, private val bookId: Long) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val container = (application as LectorApplication).container
            return PdfReaderViewModel(bookId, container.createPdfEngine(), container.bookDao, container.readingDao, container.applicationScope) as T
        }
    }
}
