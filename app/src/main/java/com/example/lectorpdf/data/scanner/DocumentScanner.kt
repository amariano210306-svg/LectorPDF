package com.example.lectorpdf.data.scanner

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.net.toUri
import com.example.lectorpdf.data.local.entity.BookEntity
import com.example.lectorpdf.data.preferences.SettingsRepository
import com.example.lectorpdf.data.repository.LibraryRepository
import com.example.lectorpdf.domain.model.BookFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.ArrayDeque
import java.util.Locale

data class StorageScanProgress(
    val inspected: Int = 0,
    val discovered: Int = 0,
    val source: String = "",
)

data class StorageScanResult(
    val inspected: Int = 0,
    val imported: Int = 0,
    val updated: Int = 0,
    val unavailable: Int = 0,
    val rejected: Int = 0,
    val messages: List<String> = emptyList(),
) {
    operator fun plus(other: StorageScanResult) = StorageScanResult(
        inspected = inspected + other.inspected,
        imported = imported + other.imported,
        updated = updated + other.updated,
        unavailable = unavailable + other.unavailable,
        rejected = rejected + other.rejected,
        messages = (messages + other.messages).take(8),
    )
}

class DocumentScanner(
    private val context: Context,
    private val libraryRepository: LibraryRepository,
    private val settingsRepository: SettingsRepository,
) {
    suspend fun scanAll(onProgress: (StorageScanProgress) -> Unit = {}): StorageScanResult {
        var result = scanMediaStore(onProgress)
        settingsRepository.settings.first().scanFolderUris.forEach { value ->
            result += scanTree(value.toUri(), onProgress)
        }
        return result
    }

    suspend fun scanMediaStore(onProgress: (StorageScanProgress) -> Unit = {}): StorageScanResult =
        withContext(Dispatchers.IO) {
            val volumes = if (Build.VERSION.SDK_INT >= 30) {
                MediaStore.getExternalVolumeNames(context).ifEmpty { setOf(MediaStore.VOLUME_EXTERNAL) }
            } else {
                setOf("external")
            }
            var total = StorageScanResult()
            volumes.forEach { volume -> total += scanMediaStoreVolume(volume, onProgress) }
            total
        }

    suspend fun scanTree(
        treeUri: Uri,
        onProgress: (StorageScanProgress) -> Unit = {},
    ): StorageScanResult = withContext(Dispatchers.IO) {
        val scanId = System.currentTimeMillis()
        var inspected = 0
        var imported = 0
        var updated = 0
        var rejected = 0
        var complete = true
        val messages = mutableListOf<String>()
        val rootId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrElse {
            return@withContext StorageScanResult(rejected = 1, messages = listOf("La carpeta seleccionada ya no está disponible."))
        }
        val pending = ArrayDeque<Pair<String, String>>()
        pending.add(rootId to "")
        while (pending.isNotEmpty()) {
            val (parentId, parentPath) = pending.removeFirst()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            try {
                context.contentResolver.query(childrenUri, DOCUMENT_PROJECTION, null, null, null)?.use { cursor ->
                    while (cursor.moveToNext()) {
                        inspected++
                        val documentId = cursor.string(DocumentsContract.Document.COLUMN_DOCUMENT_ID) ?: continue
                        val name = cursor.string(DocumentsContract.Document.COLUMN_DISPLAY_NAME) ?: continue
                        val mime = cursor.string(DocumentsContract.Document.COLUMN_MIME_TYPE).orEmpty()
                        val relativePath = if (parentPath.isBlank()) name else "$parentPath/$name"
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            pending.add(documentId to relativePath)
                        } else {
                            val format = formatFor(name, mime) ?: continue
                            val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                            val book = BookEntity(
                                uri = uri.toString(),
                                fileName = name,
                                title = cleanTitle(name),
                                format = format.name,
                                mimeType = normalizedMime(format, mime),
                                sizeBytes = cursor.long(DocumentsContract.Document.COLUMN_SIZE) ?: 0L,
                                lastModified = cursor.long(DocumentsContract.Document.COLUMN_LAST_MODIFIED),
                                sourceLabel = relativePath,
                                sourceType = SOURCE_SAF_TREE,
                                relativePath = parentPath.ifBlank { null },
                                scanRootUri = treeUri.toString(),
                                isAvailable = true,
                                lastSeenScanId = scanId,
                            )
                            runCatching { libraryRepository.syncBook(book) }
                                .onSuccess { (_, inserted) -> if (inserted) imported++ else updated++ }
                                .onFailure { rejected++; messages += "No se pudo indexar $name" }
                        }
                        if (inspected % 25 == 0) {
                            onProgress(StorageScanProgress(inspected, imported + updated, "Carpeta seleccionada"))
                        }
                    }
                } ?: run { complete = false; messages += "No se pudo leer una carpeta autorizada." }
            } catch (_: SecurityException) {
                complete = false
                messages += "Se perdió el permiso de una carpeta seleccionada."
            } catch (_: Exception) {
                complete = false
                messages += "Una subcarpeta no pudo examinarse."
            }
        }
        val unavailable = if (complete) {
            libraryRepository.markMissingFromScan(SOURCE_SAF_TREE, treeUri.toString(), scanId)
        } else {
            0
        }
        onProgress(StorageScanProgress(inspected, imported + updated, "Carpeta seleccionada"))
        StorageScanResult(inspected, imported, updated, unavailable, rejected, messages.distinct().take(8))
    }

    private suspend fun scanMediaStoreVolume(
        volume: String,
        onProgress: (StorageScanProgress) -> Unit,
    ): StorageScanResult {
        val scanId = System.currentTimeMillis()
        val root = "mediastore:$volume"
        var inspected = 0
        var imported = 0
        var updated = 0
        var rejected = 0
        val messages = mutableListOf<String>()
        val collection = MediaStore.Files.getContentUri(volume)
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.DATE_MODIFIED)
            add(MediaStore.MediaColumns.MIME_TYPE)
            if (Build.VERSION.SDK_INT >= 29) add(MediaStore.MediaColumns.RELATIVE_PATH)
        }.toTypedArray()
        val selection = "${MediaStore.MediaColumns.MIME_TYPE} IN (?, ?) OR " +
            "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? OR ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
        val arguments = arrayOf("application/pdf", "application/epub+zip", "%.pdf", "%.epub")
        try {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                arguments,
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    inspected++
                    val id = cursor.long(MediaStore.MediaColumns._ID) ?: continue
                    val name = cursor.string(MediaStore.MediaColumns.DISPLAY_NAME) ?: continue
                    val mime = cursor.string(MediaStore.MediaColumns.MIME_TYPE).orEmpty()
                    val format = formatFor(name, mime) ?: continue
                    val modifiedSeconds = cursor.long(MediaStore.MediaColumns.DATE_MODIFIED)
                    val relativePath = if (Build.VERSION.SDK_INT >= 29) cursor.string(MediaStore.MediaColumns.RELATIVE_PATH) else null
                    val uri = ContentUris.withAppendedId(collection, id)
                    val book = BookEntity(
                        uri = uri.toString(),
                        fileName = name,
                        title = cleanTitle(name),
                        format = format.name,
                        mimeType = normalizedMime(format, mime),
                        sizeBytes = cursor.long(MediaStore.MediaColumns.SIZE) ?: 0L,
                        lastModified = modifiedSeconds?.times(1_000L),
                        sourceLabel = relativePath,
                        sourceType = SOURCE_MEDIA_STORE,
                        mediaStoreId = id,
                        mediaStoreVolume = volume,
                        relativePath = relativePath,
                        scanRootUri = root,
                        isAvailable = true,
                        lastSeenScanId = scanId,
                    )
                    runCatching { libraryRepository.syncBook(book) }
                        .onSuccess { (_, inserted) -> if (inserted) imported++ else updated++ }
                        .onFailure { rejected++; messages += "No se pudo indexar $name" }
                    if (inspected % 25 == 0) {
                        onProgress(StorageScanProgress(inspected, imported + updated, "Almacenamiento del dispositivo"))
                    }
                }
            } ?: return StorageScanResult(messages = listOf("MediaStore no devolvió resultados para $volume."))
        } catch (_: SecurityException) {
            return StorageScanResult(
                rejected = 1,
                messages = listOf("Android no concedió acceso a los documentos de $volume."),
            )
        } catch (error: Exception) {
            return StorageScanResult(rejected = 1, messages = listOf(error.message ?: "No se pudo consultar MediaStore."))
        }
        val unavailable = libraryRepository.markMissingFromScan(SOURCE_MEDIA_STORE, root, scanId)
        onProgress(StorageScanProgress(inspected, imported + updated, "Almacenamiento del dispositivo"))
        return StorageScanResult(inspected, imported, updated, unavailable, rejected, messages.take(8))
    }

    private fun Cursor.string(column: String): String? {
        val index = getColumnIndex(column)
        return if (index >= 0 && !isNull(index)) getString(index) else null
    }

    private fun Cursor.long(column: String): Long? {
        val index = getColumnIndex(column)
        return if (index >= 0 && !isNull(index)) getLong(index) else null
    }

    private fun formatFor(name: String, mime: String): BookFormat? {
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when {
            mime.equals("application/pdf", true) || extension == "pdf" -> BookFormat.PDF
            mime.equals("application/epub+zip", true) || extension == "epub" -> BookFormat.EPUB
            else -> null
        }
    }

    private fun cleanTitle(name: String): String =
        name.substringBeforeLast('.').replace('_', ' ').trim().ifBlank { name }

    private fun normalizedMime(format: BookFormat, mime: String): String = when {
        mime.isNotBlank() -> mime
        format == BookFormat.PDF -> "application/pdf"
        else -> "application/epub+zip"
    }

    private companion object {
        const val SOURCE_MEDIA_STORE = "MEDIA_STORE"
        const val SOURCE_SAF_TREE = "SAF_TREE"
        val DOCUMENT_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
    }
}
