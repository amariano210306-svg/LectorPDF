package com.example.lectorpdf.domain.model

enum class BookFormat { PDF, EPUB }

enum class ReadingStatus { UNREAD, WANT_TO_READ, READING, FINISHED }

enum class LibraryViewMode { GRID, LIST }

enum class LibrarySort { TITLE, AUTHOR, DATE_ADDED, LAST_OPENED, SIZE, PROGRESS }

enum class LibraryFilter { ALL, PDF, EPUB, READING, UNREAD, FINISHED, FAVORITES }

enum class AppTheme { SYSTEM, LIGHT, DARK, AMOLED }

data class LibraryBook(
    val id: Long,
    val uri: String,
    val fileName: String,
    val title: String,
    val author: String?,
    val format: BookFormat,
    val sizeBytes: Long,
    val dateAdded: Long,
    val lastModified: Long?,
    val coverPath: String?,
    val isFavorite: Boolean,
    val progress: Float,
    val currentPage: Int?,
    val pageCount: Int?,
    val lastOpenedAt: Long?,
    val totalReadingTimeMillis: Long,
    val status: ReadingStatus,
)

data class ReadingSummary(
    val todayMillis: Long = 0,
    val weekMillis: Long = 0,
    val monthMillis: Long = 0,
    val booksStarted: Int = 0,
    val booksFinished: Int = 0,
    val currentStreakDays: Int = 0,
)
