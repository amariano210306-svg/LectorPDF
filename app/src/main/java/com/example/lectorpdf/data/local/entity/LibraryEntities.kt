package com.example.lectorpdf.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "books",
    indices = [Index(value = ["uri"], unique = true), Index("title"), Index("author")],
)
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val fileName: String,
    val title: String,
    val author: String? = null,
    val format: String,
    val mimeType: String,
    val sizeBytes: Long = 0,
    val dateAdded: Long = System.currentTimeMillis(),
    val lastModified: Long? = null,
    val coverPath: String? = null,
    val sourceLabel: String? = null,
    val isFavorite: Boolean = false,
)

@Entity(
    tableName = "book_progress",
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("lastOpenedAt"), Index("status")],
)
data class BookProgressEntity(
    @PrimaryKey val bookId: Long,
    val progress: Float = 0f,
    val currentPage: Int? = null,
    val pageCount: Int? = null,
    val locatorJson: String? = null,
    val zoom: Float? = null,
    val lastOpenedAt: Long? = null,
    val totalReadingTimeMillis: Long = 0,
    val status: String = "UNREAD",
)

@Entity(tableName = "collections", indices = [Index(value = ["name"], unique = true)])
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val colorArgb: Long? = null,
)

@Entity(
    tableName = "book_collection",
    primaryKeys = ["bookId", "collectionId"],
    foreignKeys = [
        ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CollectionEntity::class, parentColumns = ["id"], childColumns = ["collectionId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("collectionId")],
)
data class BookCollectionCrossRef(
    val bookId: Long,
    val collectionId: Long,
)
