package com.example.lectorpdf.data.scanner

internal class FolderVisitTracker {
    private val documentIds = mutableSetOf<String>()
    fun register(documentId: String): Boolean = documentIds.add(documentId)
    fun count(): Int = documentIds.size
}

internal fun childLogicalPath(parentPath: String, childName: String): String =
    if (parentPath.isBlank()) childName else "$parentPath/$childName"
