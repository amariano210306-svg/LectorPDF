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
import com.example.lectorpdf.data.preferences.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

enum class PdfPageDirection { CONTINUOUS, PAGED_HORIZONTAL }

data class PdfReaderUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val title: String = "",
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    val zoom: Float = 1f,
    val rotation: Int = 0,
    val fitMode: PdfFitMode = PdfFitMode.WIDTH,
    val direction: PdfPageDirection = PdfPageDirection.CONTINUOUS,
    val controlsVisible: Boolean = true,
    val focusMode: Boolean = false,
    val brightness: Float = -1f,
    val pages: Map<Int, Bitmap> = emptyMap(),
    val thumbnails: Map<Int, Bitmap> = emptyMap(),
    val searchQuery: String = "",
    val searchResults: List<PdfSearchResult> = emptyList(),
    val searching: Boolean = false,
    val pageOffsetFraction: Float = 0f,
    val cropMargins: Boolean = false,
    val orientation: ReaderOrientation = ReaderOrientation.AUTO,
    val navigationToken: Long = 0,
    val controlsInteractionToken: Long = 0,
) {
    val searchSupported: Boolean get() = Build.VERSION.SDK_INT >= 35
}

class PdfReaderViewModel(
    private val bookId: Long,
    private val engine: PdfDocumentEngine,
    private val bookDao: BookDao,
    private val readingDao: ReadingDao,
    private val settingsRepository: SettingsRepository,
    private val applicationScope: CoroutineScope,
) : ViewModel() {
    private val mutableState = MutableStateFlow(PdfReaderUiState())
    val uiState: StateFlow<PdfReaderUiState> = mutableState
    private val renderJobs = mutableMapOf<Int, Job>()
    private val thumbnailJobs = mutableMapOf<Int, Job>()
    private var visiblePages: Set<Int> = emptySet()
    private var progressJob: Job? = null
    private var searchJob: Job? = null
    private var sessionStartedAt: Long? = null
    private var sessionStartProgress = 0f
    private var previousStatus = "UNREAD"
    private var lastLandscapeViewport: Boolean? = null

    init {
        viewModelScope.launch {
            runCatching {
                val book = checkNotNull(bookDao.findById(bookId)) { "El libro ya no está en la biblioteca" }
                val progress = bookDao.findProgress(bookId)
                previousStatus = progress?.status ?: "UNREAD"
                val count = engine.open(book.uri.toUri())
                require(count > 0) { "El PDF no contiene páginas legibles" }
                settingsRepository.setLastOpenedBook(bookId)
                mutableState.update {
                    it.copy(
                        loading = false,
                        title = book.title,
                        pageCount = count,
                        currentPage = (progress?.currentPage ?: 0).coerceIn(0, count - 1),
                        zoom = (progress?.zoom ?: 1f).coerceIn(1f, 4f),
                        fitMode = progress?.fitMode.toEnumOrDefault(PdfFitMode.WIDTH),
                        direction = progress?.direction.toPdfDirection(),
                        pageOffsetFraction = (progress?.pageOffsetFraction ?: 0f).coerceIn(0f, 1f),
                        cropMargins = progress?.cropMargins ?: false,
                        orientation = progress?.orientation.toEnumOrDefault(ReaderOrientation.AUTO),
                        rotation = progress?.rotation ?: 0,
                        navigationToken = 1,
                    )
                }
                beginSession()
            }.onFailure { error -> mutableState.update { it.copy(loading = false, error = error.message ?: "No se pudo abrir el PDF") } }
        }
    }

    fun requestPage(page: Int, width: Int, height: Int, thumbnail: Boolean = false) {
        val state = mutableState.value
        if (page !in 0 until state.pageCount || width <= 0 || height <= 0) return
        if (thumbnail) {
            if (state.thumbnails[page]?.isRecycled == false || thumbnailJobs[page]?.isActive == true) return
        } else {
            renderJobs.remove(page)?.cancel()
        }
        val job = viewModelScope.launch {
            try {
                val bitmap = engine.render(
                    pageIndex = page,
                    viewportWidth = width,
                    viewportHeight = height,
                    zoom = if (thumbnail) 1f else state.zoom,
                    rotation = state.rotation,
                    fitMode = if (thumbnail) PdfFitMode.PAGE else state.fitMode,
                    cropMargins = state.cropMargins,
                    thumbnail = thumbnail,
                )
                coroutineContext.ensureActive()
                mutableState.update { current ->
                    if (thumbnail) {
                        val limited = (current.thumbnails + (page to bitmap)).entries.toList().takeLast(48).associate { it.toPair() }
                        current.copy(thumbnails = limited)
                    } else {
                        val relevant = visiblePages.isEmpty() || page in visiblePages
                        if (relevant) current.copy(pages = current.pages + (page to bitmap)) else current
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // La página conserva su estado de carga; un nuevo viewport puede reintentarla.
            } finally {
                val runningJob = coroutineContext[Job]
                if (thumbnail) {
                    if (thumbnailJobs[page] === runningJob) thumbnailJobs.remove(page)
                } else if (renderJobs[page] === runningJob) {
                    renderJobs.remove(page)
                }
            }
        }
        if (thumbnail) thumbnailJobs[page] = job else renderJobs[page] = job
    }

    fun setVisiblePages(pages: Set<Int>) {
        val count = mutableState.value.pageCount
        val retained = pages.flatMap { page -> (page - 1..page + 1).filter { it in 0 until count } }.toSet()
        if (retained == visiblePages) return
        visiblePages = retained
        renderJobs.filterKeys { it !in retained }.values.forEach(Job::cancel)
        mutableState.update { it.copy(pages = it.pages.filterKeys(retained::contains)) }
    }

    fun onViewportShapeChanged(landscape: Boolean) {
        val previous = lastLandscapeViewport
        lastLandscapeViewport = landscape
        if (previous != null && previous != landscape) {
            mutableState.update {
                it.copy(zoom = 1f, pages = emptyMap(), navigationToken = it.navigationToken + 1)
            }
            scheduleProgressSave()
        }
    }

    fun goToPage(page: Int) {
        val safePage = page.coerceIn(0, (mutableState.value.pageCount - 1).coerceAtLeast(0))
        mutableState.update { it.copy(currentPage = safePage, pageOffsetFraction = 0f, navigationToken = it.navigationToken + 1) }
        scheduleProgressSave()
    }

    fun updateReadingPosition(page: Int, offsetFraction: Float) {
        val state = mutableState.value
        if (page !in 0 until state.pageCount) return
        val safeOffset = offsetFraction.coerceIn(0f, 1f)
        if (page == state.currentPage && kotlin.math.abs(safeOffset - state.pageOffsetFraction) < .002f) return
        mutableState.update { it.copy(currentPage = page, pageOffsetFraction = safeOffset) }
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

    fun rotate() { mutableState.update { it.copy(rotation = (it.rotation + 90) % 360, pages = emptyMap(), thumbnails = emptyMap(), navigationToken = it.navigationToken + 1) }; scheduleProgressSave() }
    fun setFitMode(value: PdfFitMode) { mutableState.update { it.copy(fitMode = value, zoom = 1f, pages = emptyMap(), navigationToken = it.navigationToken + 1) }; scheduleProgressSave() }
    fun setDirection(value: PdfPageDirection) { mutableState.update { it.copy(direction = value, zoom = 1f, pages = emptyMap(), navigationToken = it.navigationToken + 1) }; scheduleProgressSave() }
    fun setOrientation(value: ReaderOrientation) { mutableState.update { it.copy(orientation = value) }; scheduleProgressSave() }
    fun setCropMargins(value: Boolean) { mutableState.update { it.copy(cropMargins = value, pages = emptyMap(), navigationToken = it.navigationToken + 1) }; scheduleProgressSave() }
    fun setBrightness(value: Float) = mutableState.update {
        it.copy(brightness = if (value < 0f) -1f else value.coerceIn(.05f, 1f))
    }
    fun toggleControls() = mutableState.update { if (it.focusMode) it else it.copy(controlsVisible = !it.controlsVisible, controlsInteractionToken = it.controlsInteractionToken + 1) }
    fun showControls() = mutableState.update { if (it.focusMode) it else it.copy(controlsVisible = true, controlsInteractionToken = it.controlsInteractionToken + 1) }
    fun noteControlsInteraction() = mutableState.update { it.copy(controlsInteractionToken = it.controlsInteractionToken + 1) }
    fun hideControls() = mutableState.update { it.copy(controlsVisible = false) }
    fun toggleFocusMode() = mutableState.update { it.copy(focusMode = !it.focusMode, controlsVisible = it.focusMode, controlsInteractionToken = it.controlsInteractionToken + 1) }

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
        progressJob = viewModelScope.launch { delay(750); saveProgress() }
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
            bookDao.updatePdfProgress(
                bookId = bookId,
                page = state.currentPage,
                pageCount = state.pageCount,
                progress = fraction,
                zoom = state.zoom,
                openedAt = System.currentTimeMillis(),
                status = status,
                pageOffsetFraction = state.pageOffsetFraction,
                fitMode = state.fitMode.name,
                direction = state.direction.name,
                cropMargins = state.cropMargins,
                orientation = state.orientation.name,
                rotation = state.rotation,
            )
        }
    }

    private fun progressFraction(): Float {
        val state = mutableState.value
        return pdfProgress(state.currentPage, state.pageOffsetFraction, state.pageCount)
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
            return PdfReaderViewModel(bookId, container.createPdfEngine(), container.bookDao, container.readingDao, container.settingsRepository, container.applicationScope) as T
        }
    }
}

private inline fun <reified T : Enum<T>> String?.toEnumOrDefault(default: T): T =
    this?.let { value -> enumValues<T>().firstOrNull { it.name == value } } ?: default

private fun String?.toPdfDirection(): PdfPageDirection = when (this) {
    "HORIZONTAL", PdfPageDirection.PAGED_HORIZONTAL.name -> PdfPageDirection.PAGED_HORIZONTAL
    else -> PdfPageDirection.CONTINUOUS
}
