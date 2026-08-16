package com.example.lectorpdf.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
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
    version = 1,
    exportSchema = true,
)
abstract class LectorDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun collectionDao(): CollectionDao
    abstract fun readingDao(): ReadingDao
}
