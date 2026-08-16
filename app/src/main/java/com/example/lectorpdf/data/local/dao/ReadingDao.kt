package com.example.lectorpdf.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.lectorpdf.data.local.entity.BookmarkEntity
import com.example.lectorpdf.data.local.entity.HighlightEntity
import com.example.lectorpdf.data.local.entity.NoteEntity
import com.example.lectorpdf.data.local.entity.ReadingGoalEntity
import com.example.lectorpdf.data.local.entity.ReadingSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingDao {
    @Insert suspend fun insertBookmark(bookmark: BookmarkEntity): Long
    @Query("DELETE FROM bookmarks WHERE id = :bookmarkId")
    suspend fun deleteBookmark(bookmarkId: Long)

    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY createdAt ASC")
    suspend fun getBookmarks(bookId: Long): List<BookmarkEntity>

    @Query("SELECT * FROM highlights WHERE bookId = :bookId ORDER BY createdAt ASC")
    suspend fun getHighlights(bookId: Long): List<HighlightEntity>

    @Query("SELECT * FROM notes WHERE bookId = :bookId ORDER BY createdAt ASC")
    suspend fun getNotes(bookId: Long): List<NoteEntity>
    @Insert suspend fun insertHighlight(highlight: HighlightEntity): Long
    @Insert suspend fun insertNote(note: NoteEntity): Long
    @Insert suspend fun insertSession(session: ReadingSessionEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertGoal(goal: ReadingGoalEntity): Long

    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY createdAt DESC")
    fun observeBookmarks(bookId: Long): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM highlights WHERE bookId = :bookId ORDER BY createdAt DESC")
    fun observeHighlights(bookId: Long): Flow<List<HighlightEntity>>

    @Query("SELECT COALESCE(SUM(durationMillis), 0) FROM reading_sessions WHERE startedAt >= :fromMillis")
    fun observeReadingTimeSince(fromMillis: Long): Flow<Long>

    @Query("SELECT COUNT(*) FROM book_progress WHERE status = :status")
    fun observeStatusCount(status: String): Flow<Int>
}
