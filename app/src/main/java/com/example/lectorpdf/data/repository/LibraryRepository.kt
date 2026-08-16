package com.example.lectorpdf.data.repository

import com.example.lectorpdf.data.local.dao.BookDao
import com.example.lectorpdf.data.local.dao.CollectionDao
import com.example.lectorpdf.data.local.entity.BookEntity
import com.example.lectorpdf.data.local.model.BookRow
import com.example.lectorpdf.domain.model.BookFormat
import com.example.lectorpdf.domain.model.LibraryBook
import com.example.lectorpdf.domain.model.LibraryFilter
import com.example.lectorpdf.domain.model.LibrarySort
import com.example.lectorpdf.domain.model.ReadingStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LibraryRepository(
    private val bookDao: BookDao,
    val collectionDao: CollectionDao,
) {
    fun observeLibrary(
        search: String = "",
        filter: LibraryFilter = LibraryFilter.ALL,
        sort: LibrarySort = LibrarySort.DATE_ADDED,
    ): Flow<List<LibraryBook>> {
        val format = when (filter) {
            LibraryFilter.PDF -> BookFormat.PDF.name
            LibraryFilter.EPUB -> BookFormat.EPUB.name
            else -> ""
        }
        val status = when (filter) {
            LibraryFilter.READING -> ReadingStatus.READING.name
            LibraryFilter.UNREAD -> ReadingStatus.UNREAD.name
            LibraryFilter.FINISHED -> ReadingStatus.FINISHED.name
            else -> ""
        }
        return bookDao.observeLibrary(
            search = search.trim(),
            format = format,
            status = status,
            favoriteOnly = filter == LibraryFilter.FAVORITES,
            sort = sort.name,
        ).map { rows -> rows.map(BookRow::toDomain) }
    }

    fun observeRecent(limit: Int = 8): Flow<List<LibraryBook>> =
        bookDao.observeRecent(limit).map { rows -> rows.map(BookRow::toDomain) }

    fun observeBook(bookId: Long): Flow<LibraryBook?> = bookDao.observeBook(bookId).map { it?.toDomain() }

    fun observeBookCount(): Flow<Int> = bookDao.observeBookCount()

    suspend fun importBook(book: BookEntity): Pair<Long, Boolean> = bookDao.insertWithProgress(book)
    suspend fun setFavorite(bookId: Long, favorite: Boolean) = bookDao.setFavorite(bookId, favorite)
    suspend fun rename(bookId: Long, title: String) = bookDao.renameInLibrary(bookId, title.trim())
    suspend fun removeFromLibrary(bookId: Long) = bookDao.removeFromLibrary(bookId)
}

private fun BookRow.toDomain() = LibraryBook(
    id = id,
    uri = uri,
    fileName = fileName,
    title = title,
    author = author,
    format = enumValueOrDefault(format, BookFormat.PDF),
    sizeBytes = sizeBytes,
    dateAdded = dateAdded,
    lastModified = lastModified,
    coverPath = coverPath,
    isFavorite = isFavorite,
    progress = progress.coerceIn(0f, 1f),
    currentPage = currentPage,
    pageCount = pageCount,
    lastOpenedAt = lastOpenedAt,
    totalReadingTimeMillis = totalReadingTimeMillis,
    status = enumValueOrDefault(status, ReadingStatus.UNREAD),
)

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T =
    enumValues<T>().firstOrNull { it.name == value } ?: default
