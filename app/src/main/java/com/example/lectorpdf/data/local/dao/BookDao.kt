package com.example.lectorpdf.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.lectorpdf.data.local.entity.BookEntity
import com.example.lectorpdf.data.local.entity.BookProgressEntity
import com.example.lectorpdf.data.local.model.BookRow
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query(
        """
        SELECT b.id, b.uri, b.fileName, b.title, b.author, b.format, b.sizeBytes,
               b.dateAdded, b.lastModified, b.coverPath, b.isFavorite,
               COALESCE(p.progress, 0) AS progress, p.currentPage, p.pageCount,
               p.lastOpenedAt, COALESCE(p.totalReadingTimeMillis, 0) AS totalReadingTimeMillis,
               COALESCE(p.status, 'UNREAD') AS status
        FROM books b
        LEFT JOIN book_progress p ON p.bookId = b.id
        WHERE b.isAvailable = 1
          AND (:search = '' OR b.title LIKE '%' || :search || '%' COLLATE NOCASE
               OR COALESCE(b.author, '') LIKE '%' || :search || '%' COLLATE NOCASE
               OR b.fileName LIKE '%' || :search || '%' COLLATE NOCASE)
          AND (:format = '' OR b.format = :format)
          AND (:status = '' OR COALESCE(p.status, 'UNREAD') = :status)
          AND (:favoriteOnly = 0 OR b.isFavorite = 1)
        ORDER BY
          CASE WHEN :sort = 'TITLE' THEN b.title END COLLATE NOCASE ASC,
          CASE WHEN :sort = 'AUTHOR' THEN COALESCE(b.author, '') END COLLATE NOCASE ASC,
          CASE WHEN :sort = 'DATE_ADDED' THEN b.dateAdded END DESC,
          CASE WHEN :sort = 'LAST_OPENED' THEN COALESCE(p.lastOpenedAt, 0) END DESC,
          CASE WHEN :sort = 'SIZE' THEN b.sizeBytes END DESC,
          CASE WHEN :sort = 'PROGRESS' THEN COALESCE(p.progress, 0) END DESC,
          b.id DESC
        """,
    )
    fun observeLibrary(
        search: String,
        format: String,
        status: String,
        favoriteOnly: Boolean,
        sort: String,
    ): Flow<List<BookRow>>

    @Query(
        """
        SELECT b.id, b.uri, b.fileName, b.title, b.author, b.format, b.sizeBytes,
               b.dateAdded, b.lastModified, b.coverPath, b.isFavorite,
               COALESCE(p.progress, 0) AS progress, p.currentPage, p.pageCount,
               p.lastOpenedAt, COALESCE(p.totalReadingTimeMillis, 0) AS totalReadingTimeMillis,
               COALESCE(p.status, 'UNREAD') AS status
        FROM books b JOIN book_progress p ON p.bookId = b.id
        WHERE b.isAvailable = 1 AND p.lastOpenedAt IS NOT NULL
        ORDER BY p.lastOpenedAt DESC LIMIT :limit
        """,
    )
    fun observeRecent(limit: Int): Flow<List<BookRow>>

    @Query(
        """
        SELECT b.id, b.uri, b.fileName, b.title, b.author, b.format, b.sizeBytes,
               b.dateAdded, b.lastModified, b.coverPath, b.isFavorite,
               COALESCE(p.progress, 0) AS progress, p.currentPage, p.pageCount,
               p.lastOpenedAt, COALESCE(p.totalReadingTimeMillis, 0) AS totalReadingTimeMillis,
               COALESCE(p.status, 'UNREAD') AS status
        FROM books b LEFT JOIN book_progress p ON p.bookId = b.id
        WHERE b.id = :bookId LIMIT 1
        """,
    )
    fun observeBook(bookId: Long): Flow<BookRow?>

    @Query("SELECT * FROM books WHERE uri = :uri LIMIT 1")
    suspend fun findByUri(uri: String): BookEntity?

    @Query("SELECT * FROM books WHERE mediaStoreVolume = :volume AND mediaStoreId = :mediaStoreId LIMIT 1")
    suspend fun findByMediaStoreId(volume: String, mediaStoreId: Long): BookEntity?

    @Query(
        """
        SELECT * FROM books
        WHERE fileName = :fileName COLLATE NOCASE AND sizeBytes = :sizeBytes
          AND lastModified = :lastModified AND format = :format
        LIMIT 1
        """,
    )
    suspend fun findMetadataMatch(fileName: String, sizeBytes: Long, lastModified: Long, format: String): BookEntity?

    @Query("SELECT * FROM books WHERE id = :bookId LIMIT 1")
    suspend fun findById(bookId: Long): BookEntity?

    @Query(
        """
        SELECT b.* FROM books b
        JOIN book_progress p ON p.bookId = b.id
        WHERE b.isAvailable = 1 AND b.format = 'PDF' AND p.lastOpenedAt IS NOT NULL
        ORDER BY p.lastOpenedAt DESC LIMIT 1
        """,
    )
    suspend fun findLastOpenedPdf(): BookEntity?

    @Query("SELECT * FROM book_progress WHERE bookId = :bookId LIMIT 1")
    suspend fun findProgress(bookId: Long): BookProgressEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBook(book: BookEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProgress(progress: BookProgressEntity)

    @Update
    suspend fun updateBook(book: BookEntity)

    @Query(
        """
        UPDATE books SET isAvailable = 0
        WHERE sourceType = :sourceType
          AND ((:scanRootUri IS NULL AND scanRootUri IS NULL) OR scanRootUri = :scanRootUri)
          AND (lastSeenScanId IS NULL OR lastSeenScanId != :scanId)
        """,
    )
    suspend fun markMissingFromScan(sourceType: String, scanRootUri: String?, scanId: Long): Int

    @Query("UPDATE books SET isFavorite = :isFavorite WHERE id = :bookId")
    suspend fun setFavorite(bookId: Long, isFavorite: Boolean)

    @Query("UPDATE books SET title = :title WHERE id = :bookId")
    suspend fun renameInLibrary(bookId: Long, title: String)

    @Query("UPDATE books SET isAvailable = :available WHERE id = :bookId")
    suspend fun setAvailable(bookId: Long, available: Boolean)

    @Query(
        """
        UPDATE book_progress SET currentPage = :page, pageCount = :pageCount,
            progress = :progress, zoom = :zoom, lastOpenedAt = :openedAt,
            status = :status, pageOffsetFraction = :pageOffsetFraction,
            fitMode = :fitMode, direction = :direction, cropMargins = :cropMargins, cropMode = :cropMode,
            orientation = :orientation, rotation = :rotation, pdfTheme = :pdfTheme,
            cropLeft = :cropLeft, cropTop = :cropTop, cropRight = :cropRight,
            cropBottom = :cropBottom
        WHERE bookId = :bookId
        """,
    )
    suspend fun updatePdfProgress(
        bookId: Long,
        page: Int,
        pageCount: Int,
        progress: Float,
        zoom: Float,
        openedAt: Long,
        status: String,
        pageOffsetFraction: Float,
        fitMode: String,
        direction: String,
        cropMargins: Boolean,
        cropMode: String,
        orientation: String,
        rotation: Int,
        pdfTheme: String,
        cropLeft: Float,
        cropTop: Float,
        cropRight: Float,
        cropBottom: Float,
    )

    @Query("UPDATE book_progress SET totalReadingTimeMillis = totalReadingTimeMillis + :durationMillis WHERE bookId = :bookId")
    suspend fun addReadingTime(bookId: Long, durationMillis: Long)

    @Query("DELETE FROM books WHERE id = :bookId")
    suspend fun removeFromLibrary(bookId: Long)

    @Query("SELECT COUNT(*) FROM books WHERE isAvailable = 1")
    fun observeBookCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM books WHERE isAvailable = 1")
    suspend fun countAvailableBooks(): Int

    @Transaction
    suspend fun insertWithProgress(book: BookEntity): Pair<Long, Boolean> {
        val existing = findByUri(book.uri)
        if (existing != null) return existing.id to false
        val id = insertBook(book)
        if (id > 0) insertProgress(BookProgressEntity(bookId = id))
        return id to (id > 0)
    }

    @Transaction
    suspend fun upsertScanned(book: BookEntity): Pair<Long, Boolean> {
        val existing = book.mediaStoreId?.let { id ->
            book.mediaStoreVolume?.let { volume -> findByMediaStoreId(volume, id) }
        } ?: findByUri(book.uri) ?: if (book.sizeBytes > 0 && book.lastModified != null) {
            findMetadataMatch(book.fileName, book.sizeBytes, book.lastModified, book.format)
        } else {
            null
        }
        if (existing != null) {
            updateBook(
                existing.copy(
                    uri = book.uri,
                    fileName = book.fileName,
                    mimeType = book.mimeType,
                    sizeBytes = book.sizeBytes,
                    lastModified = book.lastModified,
                    sourceLabel = book.sourceLabel,
                    sourceType = book.sourceType,
                    mediaStoreId = book.mediaStoreId,
                    mediaStoreVolume = book.mediaStoreVolume,
                    relativePath = book.relativePath,
                    scanRootUri = book.scanRootUri,
                    isAvailable = true,
                    lastSeenScanId = book.lastSeenScanId,
                ),
            )
            return existing.id to false
        }
        return insertWithProgress(book)
    }
}
