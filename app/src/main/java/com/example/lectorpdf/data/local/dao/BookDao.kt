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
        WHERE (:search = '' OR b.title LIKE '%' || :search || '%' COLLATE NOCASE
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
        WHERE p.lastOpenedAt IS NOT NULL
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

    @Query("SELECT * FROM books WHERE id = :bookId LIMIT 1")
    suspend fun findById(bookId: Long): BookEntity?

    @Query("SELECT * FROM book_progress WHERE bookId = :bookId LIMIT 1")
    suspend fun findProgress(bookId: Long): BookProgressEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBook(book: BookEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProgress(progress: BookProgressEntity)

    @Update
    suspend fun updateBook(book: BookEntity)

    @Query("UPDATE books SET isFavorite = :isFavorite WHERE id = :bookId")
    suspend fun setFavorite(bookId: Long, isFavorite: Boolean)

    @Query("UPDATE books SET title = :title WHERE id = :bookId")
    suspend fun renameInLibrary(bookId: Long, title: String)

    @Query(
        """
        UPDATE book_progress SET currentPage = :page, pageCount = :pageCount,
            progress = :progress, zoom = :zoom, lastOpenedAt = :openedAt,
            status = :status
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
    )

    @Query("UPDATE book_progress SET totalReadingTimeMillis = totalReadingTimeMillis + :durationMillis WHERE bookId = :bookId")
    suspend fun addReadingTime(bookId: Long, durationMillis: Long)

    @Query("DELETE FROM books WHERE id = :bookId")
    suspend fun removeFromLibrary(bookId: Long)

    @Query("SELECT COUNT(*) FROM books")
    fun observeBookCount(): Flow<Int>

    @Transaction
    suspend fun insertWithProgress(book: BookEntity): Pair<Long, Boolean> {
        val existing = findByUri(book.uri)
        if (existing != null) return existing.id to false
        val id = insertBook(book)
        if (id > 0) insertProgress(BookProgressEntity(bookId = id))
        return id to (id > 0)
    }
}
