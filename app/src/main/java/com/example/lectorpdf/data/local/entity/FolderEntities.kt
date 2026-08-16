package com.example.lectorpdf.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "library_sources",
    indices = [Index(value = ["stableKey"], unique = true), Index("type"), Index("isAvailable")],
)
data class LibrarySourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val stableKey: String,
    val type: String,
    val displayName: String,
    val treeUri: String? = null,
    val rootDocumentId: String,
    val isAvailable: Boolean = true,
    val lastScannedAt: Long? = null,
)

@Entity(
    tableName = "library_folders",
    foreignKeys = [
        ForeignKey(
            entity = LibrarySourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LibraryFolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentFolderId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["sourceId", "documentId"], unique = true),
        Index("sourceId"),
        Index("parentFolderId"),
        Index("relativePath"),
        Index("isAvailable"),
    ],
)
data class LibraryFolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val parentFolderId: Long? = null,
    val documentId: String,
    val documentUri: String? = null,
    val displayName: String,
    val depth: Int,
    val relativePath: String,
    val isAvailable: Boolean = true,
    val lastSeenScanId: Long? = null,
)

@Entity(
    tableName = "book_folder",
    foreignKeys = [
        ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = LibraryFolderEntity::class, parentColumns = ["id"], childColumns = ["folderId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = LibrarySourceEntity::class, parentColumns = ["id"], childColumns = ["sourceId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("folderId"), Index("sourceId")],
)
data class BookFolderEntity(
    @PrimaryKey val bookId: Long,
    val folderId: Long,
    val sourceId: Long,
)
