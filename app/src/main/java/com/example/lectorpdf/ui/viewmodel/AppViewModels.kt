package com.example.lectorpdf.ui.viewmodel

import android.app.Application
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.lectorpdf.LectorApplication
import com.example.lectorpdf.data.importer.DocumentImporter
import com.example.lectorpdf.data.importer.ImportResult
import com.example.lectorpdf.data.local.dao.CollectionSummary
import com.example.lectorpdf.data.local.dao.FolderDao
import com.example.lectorpdf.data.local.dao.LibraryFolderRow
import com.example.lectorpdf.data.local.dao.LibrarySourceRow
import com.example.lectorpdf.data.local.entity.LibraryFolderEntity
import com.example.lectorpdf.data.local.dao.ReadingDao
import com.example.lectorpdf.data.preferences.AppSettings
import com.example.lectorpdf.data.preferences.SettingsRepository
import com.example.lectorpdf.data.repository.LibraryRepository
import com.example.lectorpdf.data.scanner.DocumentScanner
import com.example.lectorpdf.data.scanner.StorageScanProgress
import com.example.lectorpdf.data.scanner.StorageScanResult
import com.example.lectorpdf.domain.model.AppTheme
import com.example.lectorpdf.domain.model.LibraryBook
import com.example.lectorpdf.domain.model.LibraryFilter
import com.example.lectorpdf.domain.model.LibrarySort
import com.example.lectorpdf.domain.model.LibraryViewMode
import com.example.lectorpdf.domain.model.ReadingSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

data class MainUiState(
    val loading: Boolean = true,
    val settings: AppSettings = AppSettings(),
    val message: String? = null,
)

class MainViewModel(
    private val application: Application,
    private val settingsRepository: SettingsRepository,
    private val repository: LibraryRepository,
    private val scanner: DocumentScanner,
) : ViewModel() {
    private val message = MutableStateFlow<String?>(null)
    val uiState = combine(settingsRepository.settings, message) { settings, currentMessage ->
        MainUiState(loading = false, settings = settings, message = currentMessage)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    fun completeOnboarding() = viewModelScope.launch { settingsRepository.completeOnboarding() }
    fun dismissMessage() { message.value = null }

    suspend fun prepareAutoResume(): Long? {
        val settings = settingsRepository.settings.first()
        if (!settings.resumeLastReading) return null
        val bookId = settings.lastOpenedBookId ?: repository.findLastOpenedPdf()?.id?.also {
            settingsRepository.setLastOpenedBook(it)
        } ?: return null
        val book = repository.findBook(bookId)
        val valid = book != null && book.format == "PDF" && book.isAvailable && withContext(Dispatchers.IO) {
            runCatching { application.contentResolver.openFileDescriptor(book.uri.toUri(), "r")?.use { true } ?: false }.getOrDefault(false)
        }
        if (!valid) {
            book?.let { repository.setAvailable(it.id, false) }
            settingsRepository.clearLastOpenedBook()
            message.value = "El último documento ya no está disponible."
            return null
        }
        return bookId
    }

    init {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            if (!settings.initialStorageScanCompleted) {
                if (repository.countAvailableBooks() == 0) scanner.scanAll()
                settingsRepository.setInitialStorageScanCompleted(true)
            }
        }
    }
}

data class HomeUiState(
    val recent: List<LibraryBook> = emptyList(),
    val totalBooks: Int = 0,
    val collections: List<CollectionSummary> = emptyList(),
    val lastOpenedBookId: Long? = null,
) {
    val continueReading: LibraryBook? get() =
        lastOpenedBookId?.let { id -> recent.firstOrNull { it.id == id } } ?: recent.firstOrNull()
}

class HomeViewModel(repository: LibraryRepository, settingsRepository: SettingsRepository) : ViewModel() {
    val uiState = combine(
        repository.observeRecent(),
        repository.observeBookCount(),
        repository.collectionDao.observeCollections(),
        settingsRepository.settings,
    ) { recent, count, collections, settings -> HomeUiState(recent, count, collections, settings.lastOpenedBookId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}

data class LibraryUiState(
    val books: List<LibraryBook> = emptyList(),
    val search: String = "",
    val filter: LibraryFilter = LibraryFilter.ALL,
    val sort: LibrarySort = LibrarySort.DATE_ADDED,
    val viewMode: LibraryViewMode = LibraryViewMode.GRID,
    val section: LibrarySection = LibrarySection.ALL_BOOKS,
    val sources: List<LibrarySourceRow> = emptyList(),
    val folders: List<LibraryFolderRow> = emptyList(),
    val breadcrumb: List<LibraryFolderEntity> = emptyList(),
    val currentFolderId: Long? = null,
    val includeSubfolders: Boolean = false,
)

enum class LibrarySection { FOLDERS, ALL_BOOKS }

private data class FolderContent(
    val folderId: Long? = null,
    val folders: List<LibraryFolderRow> = emptyList(),
    val bookIds: Set<Long> = emptySet(),
    val breadcrumb: List<LibraryFolderEntity> = emptyList(),
    val includeSubfolders: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(
    private val repository: LibraryRepository,
    private val settingsRepository: SettingsRepository,
    private val folderDao: FolderDao,
) : ViewModel() {
    private val search = MutableStateFlow("")
    private val filter = MutableStateFlow(LibraryFilter.ALL)
    private val section = MutableStateFlow<LibrarySection?>(null)
    private val currentFolderId = MutableStateFlow<Long?>(null)
    private val includeSubfolders = MutableStateFlow(false)

    private val booksState = combine(search, filter, settingsRepository.settings) { query, activeFilter, settings ->
        Triple(query, activeFilter, settings)
    }.flatMapLatest { (query, activeFilter, settings) ->
        repository.observeLibrary(query, activeFilter, settings.librarySort).map { books ->
            LibraryUiState(books, query, activeFilter, settings.librarySort, settings.libraryViewMode)
        }
    }

    private val folderContent = combine(currentFolderId, includeSubfolders) { folderId, descendants -> folderId to descendants }
        .flatMapLatest { (folderId, descendants) ->
            if (folderId == null) flowOf(FolderContent())
            else combine(
                folderDao.observeChildren(folderId),
                folderDao.observeBookIds(folderId, descendants),
                folderDao.observeBreadcrumb(folderId),
            ) { folders, bookIds, breadcrumb ->
                FolderContent(folderId, folders, bookIds.toSet(), breadcrumb, descendants)
            }
        }

    val uiState: StateFlow<LibraryUiState> = combine(
        booksState,
        folderDao.observeSources(),
        section,
        folderContent,
    ) { base, sources, selectedSection, folder ->
        val effectiveSection = selectedSection ?: if (sources.any { it.type == "SAF_TREE" }) LibrarySection.FOLDERS else LibrarySection.ALL_BOOKS
        base.copy(
            books = if (effectiveSection == LibrarySection.FOLDERS && folder.folderId != null) base.books.filter { it.id in folder.bookIds } else base.books,
            section = effectiveSection,
            sources = sources,
            folders = folder.folders,
            breadcrumb = folder.breadcrumb,
            currentFolderId = folder.folderId,
            includeSubfolders = folder.includeSubfolders,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun setSearch(value: String) { search.value = value }
    fun setFilter(value: LibraryFilter) { filter.value = value }
    fun setSection(value: LibrarySection) { section.value = value }
    fun openSource(source: LibrarySourceRow) { currentFolderId.value = source.rootFolderId; section.value = LibrarySection.FOLDERS }
    fun openFolder(folderId: Long) { currentFolderId.value = folderId }
    fun showFolderSources() { currentFolderId.value = null; section.value = LibrarySection.FOLDERS }
    fun toggleIncludeSubfolders() { includeSubfolders.value = !includeSubfolders.value }
    fun navigateUp(): Boolean {
        val current = uiState.value
        if (current.currentFolderId == null) return false
        currentFolderId.value = current.breadcrumb.lastOrNull()?.parentFolderId
        return true
    }
    fun setSort(value: LibrarySort) = viewModelScope.launch { settingsRepository.setLibrarySort(value) }
    fun toggleViewMode() = viewModelScope.launch {
        val next = if (uiState.value.viewMode == LibraryViewMode.GRID) LibraryViewMode.LIST else LibraryViewMode.GRID
        settingsRepository.setLibraryViewMode(next)
    }
    fun setFavorite(book: LibraryBook, favorite: Boolean) = viewModelScope.launch { repository.setFavorite(book.id, favorite) }
}

data class ImportUiState(
    val importing: Boolean = false,
    val scanning: Boolean = false,
    val progress: StorageScanProgress? = null,
    val result: ImportResult? = null,
    val scanResult: StorageScanResult? = null,
)

class FilesViewModel(
    private val importer: DocumentImporter,
    private val scanner: DocumentScanner,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val uiState = MutableStateFlow(ImportUiState())

    fun import(uris: List<android.net.Uri>) {
        if (uris.isEmpty() || uiState.value.importing || uiState.value.scanning) return
        viewModelScope.launch {
            uiState.value = uiState.value.copy(importing = true, result = null)
            uiState.value = uiState.value.copy(importing = false, result = importer.import(uris))
        }
    }

    fun scanDevice() {
        if (uiState.value.importing || uiState.value.scanning) return
        viewModelScope.launch {
            uiState.value = uiState.value.copy(scanning = true, scanResult = null, progress = StorageScanProgress(source = "Preparando escaneo"))
            val result = scanner.scanAll { progress -> uiState.value = uiState.value.copy(progress = progress) }
            uiState.value = uiState.value.copy(scanning = false, scanResult = result)
        }
    }

    fun addFolder(uri: android.net.Uri) {
        if (uiState.value.importing || uiState.value.scanning) return
        viewModelScope.launch {
            settingsRepository.addScanFolder(uri.toString())
            uiState.value = uiState.value.copy(scanning = true, scanResult = null, progress = StorageScanProgress(source = "Carpeta seleccionada"))
            val result = scanner.scanTree(uri) { progress -> uiState.value = uiState.value.copy(progress = progress) }
            uiState.value = uiState.value.copy(scanning = false, scanResult = result)
        }
    }
}

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {
    val settings = repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    fun setTheme(value: AppTheme) = viewModelScope.launch { repository.setTheme(value) }
    fun setDynamicColor(value: Boolean) = viewModelScope.launch { repository.setDynamicColor(value) }
    fun setAnimations(value: Boolean) = viewModelScope.launch { repository.setAnimationsEnabled(value) }
    fun setKeepScreenOn(value: Boolean) = viewModelScope.launch { repository.setKeepScreenOn(value) }
    fun setVolumeButtons(value: Boolean) = viewModelScope.launch { repository.setVolumeButtons(value) }
    fun setResumeLastReading(value: Boolean) = viewModelScope.launch { repository.setResumeLastReading(value) }
}

class StatsViewModel(readingDao: ReadingDao) : ViewModel() {
    private val now = System.currentTimeMillis()
    private val today = startOf(Calendar.DAY_OF_MONTH)
    private val week = startOfWeek()
    private val month = startOf(Calendar.MONTH)
    val summary: StateFlow<ReadingSummary> = combine(
        readingDao.observeReadingTimeSince(today),
        readingDao.observeReadingTimeSince(week),
        readingDao.observeReadingTimeSince(month),
        readingDao.observeStatusCount("READING"),
        readingDao.observeStatusCount("FINISHED"),
    ) { todayTime, weekTime, monthTime, started, finished ->
        ReadingSummary(todayTime, weekTime, monthTime, started, finished)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReadingSummary())

    private fun startOf(field: Int): Long = Calendar.getInstance().apply {
        timeInMillis = now
        if (field == Calendar.MONTH) set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun startOfWeek(): Long = Calendar.getInstance().apply {
        timeInMillis = now
        firstDayOfWeek = Calendar.MONDAY
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

class BookDetailsViewModel(
    repository: LibraryRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val bookId: Long = checkNotNull(savedStateHandle["bookId"])
    val book = repository.observeBook(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val mutableRepository = repository

    fun setFavorite(value: Boolean) = viewModelScope.launch { mutableRepository.setFavorite(bookId, value) }
    fun rename(title: String) = viewModelScope.launch { if (title.isNotBlank()) mutableRepository.rename(bookId, title) }
    fun remove(onDone: () -> Unit) = viewModelScope.launch { mutableRepository.removeFromLibrary(bookId); onDone() }
}

object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer { MainViewModel(app(), app().container.settingsRepository, app().container.libraryRepository, app().container.documentScanner) }
        initializer { HomeViewModel(app().container.libraryRepository, app().container.settingsRepository) }
        initializer { LibraryViewModel(app().container.libraryRepository, app().container.settingsRepository, app().container.folderDao) }
        initializer { FilesViewModel(app().container.documentImporter, app().container.documentScanner, app().container.settingsRepository) }
        initializer { SettingsViewModel(app().container.settingsRepository) }
        initializer { StatsViewModel(app().container.readingDao) }
        initializer { BookDetailsViewModel(app().container.libraryRepository, createSavedStateHandle()) }
    }

    private fun androidx.lifecycle.viewmodel.CreationExtras.app(): LectorApplication =
        checkNotNull(this[APPLICATION_KEY]) as LectorApplication
}
