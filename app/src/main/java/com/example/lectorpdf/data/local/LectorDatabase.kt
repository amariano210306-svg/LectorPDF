package com.example.lectorpdf.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.lectorpdf.data.local.dao.BookDao
import com.example.lectorpdf.data.local.dao.CollectionDao
import com.example.lectorpdf.data.local.dao.ReadingDao
import com.example.lectorpdf.data.local.entity.BookCollectionCrossRef
import com.example.lectorpdf.data.local.entity.BookEntity
import com.example.lectorpdf.data.local.entity.BookProgressEntity
import com.example.lectorpdf.data.local.entity.BookmarkEntity
import com.example.lectorpdf.data.local.entity.CollectionEntity
import com.example.lectorpdf.data.local.entity.HighlightEntity
import com.example.lectorpdf.data.local.entity.NoteEntity
import com.example.lectorpdf.data.local.entity.ReadingGoalEntity
import com.example.lectorpdf.data.local.entity.ReadingSessionEntity

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
    ],
    version = 2,
    exportSchema = true,
)
abstract class LectorDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun collectionDao(): CollectionDao
    abstract fun readingDao(): ReadingDao

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
    }
}
