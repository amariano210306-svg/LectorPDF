package com.example.lectorpdf.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.lectorpdf.data.local.dao.BookDao
import com.example.lectorpdf.data.local.dao.CollectionDao
import com.example.lectorpdf.data.local.dao.ReadingDao
import com.example.lectorpdf.data.local.dao.FolderDao
import com.example.lectorpdf.data.local.entity.BookCollectionCrossRef
import com.example.lectorpdf.data.local.entity.BookEntity
import com.example.lectorpdf.data.local.entity.BookProgressEntity
import com.example.lectorpdf.data.local.entity.BookmarkEntity
import com.example.lectorpdf.data.local.entity.CollectionEntity
import com.example.lectorpdf.data.local.entity.HighlightEntity
import com.example.lectorpdf.data.local.entity.NoteEntity
import com.example.lectorpdf.data.local.entity.ReadingGoalEntity
import com.example.lectorpdf.data.local.entity.ReadingSessionEntity
import com.example.lectorpdf.data.local.entity.LibrarySourceEntity
import com.example.lectorpdf.data.local.entity.LibraryFolderEntity
import com.example.lectorpdf.data.local.entity.BookFolderEntity

@Database(
    entities = [
        BookEntity::class,
        BookProgressEntity::class,
        CollectionEntity::class,
        BookCollectionCrossRef::class,
        BookmarkEntity::class,
        HighlightEntity::class,
        NoteEntity::class,
        ReadingSessionEntity::class,
        ReadingGoalEntity::class,
        LibrarySourceEntity::class,
        LibraryFolderEntity::class,
        BookFolderEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class LectorDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun collectionDao(): CollectionDao
    abstract fun readingDao(): ReadingDao
    abstract fun folderDao(): FolderDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN sourceType TEXT NOT NULL DEFAULT 'SAF_FILE'")
                db.execSQL("ALTER TABLE books ADD COLUMN mediaStoreId INTEGER")
                db.execSQL("ALTER TABLE books ADD COLUMN mediaStoreVolume TEXT")
                db.execSQL("ALTER TABLE books ADD COLUMN relativePath TEXT")
                db.execSQL("ALTER TABLE books ADD COLUMN scanRootUri TEXT")
                db.execSQL("ALTER TABLE books ADD COLUMN isAvailable INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE books ADD COLUMN lastSeenScanId INTEGER")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_books_mediaStoreVolume_mediaStoreId ON books(mediaStoreVolume, mediaStoreId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_books_isAvailable ON books(isAvailable)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_books_scanRootUri ON books(scanRootUri)")
                db.execSQL("ALTER TABLE book_progress ADD COLUMN pageOffsetFraction REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE book_progress ADD COLUMN fitMode TEXT NOT NULL DEFAULT 'WIDTH'")
                db.execSQL("ALTER TABLE book_progress ADD COLUMN direction TEXT NOT NULL DEFAULT 'CONTINUOUS'")
                db.execSQL("ALTER TABLE book_progress ADD COLUMN cropMargins INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE book_progress ADD COLUMN orientation TEXT NOT NULL DEFAULT 'AUTO'")
                db.execSQL("ALTER TABLE book_progress ADD COLUMN rotation INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS library_sources (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, stableKey TEXT NOT NULL, type TEXT NOT NULL, displayName TEXT NOT NULL, treeUri TEXT, rootDocumentId TEXT NOT NULL, isAvailable INTEGER NOT NULL, lastScannedAt INTEGER)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_library_sources_stableKey ON library_sources(stableKey)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_library_sources_type ON library_sources(type)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_library_sources_isAvailable ON library_sources(isAvailable)")
                db.execSQL("CREATE TABLE IF NOT EXISTS library_folders (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sourceId INTEGER NOT NULL, parentFolderId INTEGER, documentId TEXT NOT NULL, documentUri TEXT, displayName TEXT NOT NULL, depth INTEGER NOT NULL, relativePath TEXT NOT NULL, isAvailable INTEGER NOT NULL, lastSeenScanId INTEGER, FOREIGN KEY(sourceId) REFERENCES library_sources(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(parentFolderId) REFERENCES library_folders(id) ON UPDATE NO ACTION ON DELETE SET NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_library_folders_sourceId_documentId ON library_folders(sourceId, documentId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_library_folders_sourceId ON library_folders(sourceId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_library_folders_parentFolderId ON library_folders(parentFolderId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_library_folders_relativePath ON library_folders(relativePath)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_library_folders_isAvailable ON library_folders(isAvailable)")
                db.execSQL("CREATE TABLE IF NOT EXISTS book_folder (bookId INTEGER NOT NULL, folderId INTEGER NOT NULL, sourceId INTEGER NOT NULL, PRIMARY KEY(bookId), FOREIGN KEY(bookId) REFERENCES books(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(folderId) REFERENCES library_folders(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(sourceId) REFERENCES library_sources(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_book_folder_folderId ON book_folder(folderId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_book_folder_sourceId ON book_folder(sourceId)")
                db.execSQL("ALTER TABLE book_progress ADD COLUMN pdfTheme TEXT NOT NULL DEFAULT 'DAY'")
                db.execSQL("ALTER TABLE book_progress ADD COLUMN cropLeft REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE book_progress ADD COLUMN cropTop REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE book_progress ADD COLUMN cropRight REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE book_progress ADD COLUMN cropBottom REAL NOT NULL DEFAULT 0")
            }
        }
    }
}
