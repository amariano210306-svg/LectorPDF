package com.example.lectorpdf.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.lectorpdf.data.local.entity.BookFolderEntity
import com.example.lectorpdf.data.local.entity.LibraryFolderEntity
import com.example.lectorpdf.data.local.entity.LibrarySourceEntity
import kotlinx.coroutines.flow.Flow

data class LibrarySourceRow(
    val id: Long,
    val displayName: String,
    val type: String,
    val rootFolderId: Long,
    val totalBooks: Int,
)

data class LibraryFolderRow(
    val id: Long,
    val sourceId: Long,
    val parentFolderId: Long?,
    val displayName: String,
    val relativePath: String,
    val depth: Int,
    val subfolderCount: Int,
    val directBookCount: Int,
    val totalBookCount: Int,
)

@Dao
interface FolderDao {
    @Query("SELECT * FROM library_sources WHERE stableKey = :stableKey LIMIT 1")
    suspend fun findSource(stableKey: String): LibrarySourceEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSource(source: LibrarySourceEntity): Long

    @Update suspend fun updateSource(source: LibrarySourceEntity)

    @Query("SELECT * FROM library_folders WHERE sourceId = :sourceId AND documentId = :documentId LIMIT 1")
    suspend fun findFolder(sourceId: Long, documentId: String): LibraryFolderEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFolder(folder: LibraryFolderEntity): Long

    @Update suspend fun updateFolder(folder: LibraryFolderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun linkBook(link: BookFolderEntity)

    @Query("UPDATE library_folders SET isAvailable = 0 WHERE sourceId = :sourceId AND (lastSeenScanId IS NULL OR lastSeenScanId != :scanId)")
    suspend fun markMissingFolders(sourceId: Long, scanId: Long): Int

    @Query(
        """
        SELECT s.id, s.displayName, s.type, root.id AS rootFolderId,
            (SELECT COUNT(*) FROM book_folder bf JOIN books b ON b.id = bf.bookId WHERE bf.sourceId = s.id AND b.isAvailable = 1) AS totalBooks
        FROM library_sources s
        JOIN library_folders root ON root.sourceId = s.id AND root.documentId = s.rootDocumentId
        WHERE s.isAvailable = 1 AND root.isAvailable = 1
        ORDER BY CASE s.type WHEN 'SAF_TREE' THEN 0 WHEN 'MEDIA_STORE' THEN 1 ELSE 2 END, s.displayName COLLATE NOCASE
        """,
    )
    fun observeSources(): Flow<List<LibrarySourceRow>>

    @Query(
        """
        SELECT f.id, f.sourceId, f.parentFolderId, f.displayName, f.relativePath, f.depth,
            (SELECT COUNT(*) FROM library_folders child WHERE child.parentFolderId = f.id AND child.isAvailable = 1) AS subfolderCount,
            (SELECT COUNT(*) FROM book_folder bf JOIN books b ON b.id = bf.bookId WHERE bf.folderId = f.id AND b.isAvailable = 1) AS directBookCount,
            (SELECT COUNT(*) FROM book_folder bf
                JOIN books b ON b.id = bf.bookId
                JOIN library_folders descendant ON descendant.id = bf.folderId
                WHERE descendant.sourceId = f.sourceId AND b.isAvailable = 1
                  AND (descendant.relativePath = f.relativePath OR descendant.relativePath LIKE f.relativePath || '/%')) AS totalBookCount
        FROM library_folders f
        WHERE f.parentFolderId = :parentFolderId AND f.isAvailable = 1
        ORDER BY f.displayName COLLATE NOCASE
        """,
    )
    fun observeChildren(parentFolderId: Long): Flow<List<LibraryFolderRow>>

    @Query("SELECT * FROM library_folders WHERE sourceId = :sourceId AND isAvailable = 1 ORDER BY depth, displayName COLLATE NOCASE")
    fun observeFolders(sourceId: Long): Flow<List<LibraryFolderEntity>>

    @Query(
        """
        WITH RECURSIVE ancestors AS (
            SELECT * FROM library_folders WHERE id = :folderId
            UNION ALL
            SELECT parent.* FROM library_folders parent
            JOIN ancestors child ON child.parentFolderId = parent.id
        )
        SELECT * FROM ancestors ORDER BY depth
        """,
    )
    fun observeBreadcrumb(folderId: Long): Flow<List<LibraryFolderEntity>>

    @Query("SELECT * FROM library_folders WHERE id = :folderId LIMIT 1")
    suspend fun findFolderById(folderId: Long): LibraryFolderEntity?

    @Query(
        """
        SELECT bf.bookId FROM book_folder bf
        JOIN library_folders currentFolder ON currentFolder.id = :folderId
        JOIN library_folders assigned ON assigned.id = bf.folderId
        WHERE bf.sourceId = currentFolder.sourceId
          AND (:includeDescendants = 1 AND (assigned.relativePath = currentFolder.relativePath OR assigned.relativePath LIKE currentFolder.relativePath || '/%')
               OR :includeDescendants = 0 AND assigned.id = currentFolder.id)
        """,
    )
    fun observeBookIds(folderId: Long, includeDescendants: Boolean): Flow<List<Long>>

    @Transaction
    suspend fun upsertSource(source: LibrarySourceEntity): Long {
        val existing = findSource(source.stableKey)
        if (existing != null) {
            updateSource(source.copy(id = existing.id))
            return existing.id
        }
        return insertSource(source)
    }

    @Transaction
    suspend fun upsertFolder(folder: LibraryFolderEntity): Long {
        val existing = findFolder(folder.sourceId, folder.documentId)
        if (existing != null) {
            updateFolder(folder.copy(id = existing.id))
            return existing.id
        }
        return insertFolder(folder)
    }
}
