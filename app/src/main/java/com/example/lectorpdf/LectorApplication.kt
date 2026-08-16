package com.example.lectorpdf

import android.app.Application
import androidx.room.Room
import com.example.lectorpdf.data.importer.DocumentImporter
import com.example.lectorpdf.data.local.LectorDatabase
import com.example.lectorpdf.data.preferences.SettingsRepository
import com.example.lectorpdf.data.repository.LibraryRepository
import com.example.lectorpdf.data.scanner.DocumentScanner
import com.example.lectorpdf.reader.pdf.PdfDocumentEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class LectorApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

class AppContainer(private val application: Application) {
    internal val autoResumeCoordinator = AutoResumeCoordinator()
    private val database: LectorDatabase = Room.databaseBuilder(
        application,
        LectorDatabase::class.java,
        "lector.db",
    ).addMigrations(LectorDatabase.MIGRATION_1_2, LectorDatabase.MIGRATION_2_3).build()

    val settingsRepository = SettingsRepository(application)
    val libraryRepository = LibraryRepository(database.bookDao(), database.collectionDao())
    val documentImporter = DocumentImporter(application, libraryRepository, database.folderDao())
    val documentScanner = DocumentScanner(application, libraryRepository, settingsRepository, database.folderDao())
    val readingDao = database.readingDao()
    val bookDao = database.bookDao()
    val folderDao = database.folderDao()
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    fun createPdfEngine() = PdfDocumentEngine(application)
}
