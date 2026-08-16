package com.example.lectorpdf.data.local.model

data class BookRow(
    val id: Long,
    val uri: String,
    val fileName: String,
    val title: String,
    val author: String?,
    val format: String,
    val sizeBytes: Long,
    val dateAdded: Long,
    val lastModified: Long?,
    val coverPath: String?,
    val isFavorite: Boolean,
    val progress: Float,
    val currentPage: Int?,
    val pageCount: Int?,
    val lastOpenedAt: Long?,
    val totalReadingTimeMillis: Long,
    val status: String,
)
