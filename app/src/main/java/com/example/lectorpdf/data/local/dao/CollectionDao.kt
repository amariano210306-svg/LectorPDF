package com.example.lectorpdf.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.lectorpdf.data.local.entity.BookCollectionCrossRef
import com.example.lectorpdf.data.local.entity.CollectionEntity
import kotlinx.coroutines.flow.Flow

data class CollectionSummary(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val colorArgb: Long?,
    val bookCount: Int,
)

@Dao
interface CollectionDao {
    @Query(
        """
        SELECT c.id, c.name, c.createdAt, c.colorArgb, COUNT(bc.bookId) AS bookCount
        FROM collections c LEFT JOIN book_collection bc ON bc.collectionId = c.id
        GROUP BY c.id ORDER BY c.name COLLATE NOCASE
        """,
    )
    fun observeCollections(): Flow<List<CollectionSummary>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun create(collection: CollectionEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addBook(crossRef: BookCollectionCrossRef)

    @Query("DELETE FROM book_collection WHERE bookId = :bookId AND collectionId = :collectionId")
    suspend fun removeBook(bookId: Long, collectionId: Long)

    @Query("DELETE FROM collections WHERE id = :collectionId")
    suspend fun delete(collectionId: Long)
}
