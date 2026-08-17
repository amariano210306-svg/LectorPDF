package com.example.lectorpdf.data.local

import android.content.ContentValues
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration3To4Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        LectorDatabase::class.java,
    )

    @Test
    fun migrationPreservesProgressAndDerivesCropMode() {
        helper.createDatabase(TEST_DB, 3).apply {
            insertBookAndProgress(this)
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 4, true, LectorDatabase.MIGRATION_3_4).use { database ->
            database.query("SELECT currentPage, cropMode, cropLeft FROM book_progress WHERE bookId = 1").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals(12, cursor.getInt(0))
                assertEquals("MANUAL", cursor.getString(1))
                assertEquals(.08f, cursor.getFloat(2), .0001f)
            }
        }
    }

    private fun insertBookAndProgress(database: SupportSQLiteDatabase) {
        database.insert("books", 0, ContentValues().apply {
            put("id", 1L)
            put("uri", "content://test/book.pdf")
            put("fileName", "book.pdf")
            put("title", "Book")
            put("format", "PDF")
            put("mimeType", "application/pdf")
            put("sizeBytes", 10L)
            put("dateAdded", 1L)
            put("isFavorite", false)
            put("sourceType", "SAF_FILE")
            put("isAvailable", true)
        })
        database.insert("book_progress", 0, ContentValues().apply {
            put("bookId", 1L)
            put("progress", .2f)
            put("currentPage", 12)
            put("totalReadingTimeMillis", 0L)
            put("status", "READING")
            put("pageOffsetFraction", .3f)
            put("fitMode", "WIDTH")
            put("direction", "CONTINUOUS")
            put("cropMargins", false)
            put("orientation", "AUTO")
            put("rotation", 0)
            put("pdfTheme", "DAY")
            put("cropLeft", .08f)
            put("cropTop", 0f)
            put("cropRight", .06f)
            put("cropBottom", 0f)
        })
    }

    private companion object { const val TEST_DB = "migration-3-4" }
}
