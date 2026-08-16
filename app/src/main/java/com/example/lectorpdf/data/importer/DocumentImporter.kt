package com.example.lectorpdf.data.importer

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import com.example.lectorpdf.data.local.entity.BookEntity
import com.example.lectorpdf.data.repository.LibraryRepository
import com.example.lectorpdf.domain.model.BookFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

data class ImportResult(
    val imported: Int,
    val duplicates: Int,
    val rejected: Int,
    val messages: List<String>,
)

class DocumentImporter(
    private val context: Context,
    private val libraryRepository: LibraryRepository,
) {
    suspend fun import(uris: List<Uri>): ImportResult = withContext(Dispatchers.IO) {
        var imported = 0
        var duplicates = 0
        var rejected = 0
        val messages = mutableListOf<String>()

        uris.distinct().forEach { uri ->
            runCatching {
                persistReadAccess(uri)
                val metadata = readMetadata(uri) ?: error("No se pudo leer el archivo")
                val (_, wasInserted) = libraryRepository.importBook(metadata)
                if (wasInserted) imported++ else duplicates++
            }.onFailure { error ->
                rejected++
                messages += error.message ?: "No se pudo importar ${uri.lastPathSegment.orEmpty()}"
            }
        }
        ImportResult(imported, duplicates, rejected, messages)
    }

    private fun persistReadAccess(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Algunos proveedores ofrecen acceso de lectura estable sin permisos persistibles.
        }
    }

    private fun readMetadata(uri: Uri): BookEntity? {
        val resolver = context.contentResolver
        val values = resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use(::readOpenableColumns)
        val displayName = values?.first ?: uri.lastPathSegment?.substringAfterLast('/') ?: return null
        val extension = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val mimeType = resolver.getType(uri)?.lowercase(Locale.ROOT).orEmpty()
        val format = when {
            mimeType == "application/pdf" || extension == "pdf" -> BookFormat.PDF
            mimeType == "application/epub+zip" || extension == "epub" -> BookFormat.EPUB
            else -> error("Formato no compatible: $displayName")
        }
        val cleanTitle = displayName.substringBeforeLast('.').replace('_', ' ').trim().ifBlank { displayName }
        return BookEntity(
            uri = uri.toString(),
            fileName = displayName,
            title = cleanTitle,
            format = format.name,
            mimeType = mimeType.ifBlank { if (format == BookFormat.PDF) "application/pdf" else "application/epub+zip" },
            sizeBytes = values?.second ?: 0,
        )
    }

    private fun readOpenableColumns(cursor: Cursor): Pair<String, Long>? {
        if (!cursor.moveToFirst()) return null
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
        val name = if (nameIndex >= 0 && !cursor.isNull(nameIndex)) cursor.getString(nameIndex) else return null
        val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else 0L
        return name to size
    }
}
