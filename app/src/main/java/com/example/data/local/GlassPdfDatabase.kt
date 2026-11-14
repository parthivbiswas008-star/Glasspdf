package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "pdf_documents")
data class PdfDocumentEntity(
    @PrimaryKey val id: String,
    val title: String,
    val filePath: String,
    val pageCount: Int,
    val currentPage: Int = 0,
    val fileSizeBytes: Long = 0L,
    val isStarred: Boolean = false,
    val categoryTag: String = "Architecture",
    val lastOpenedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val annotationsJson: String = ""
)

@Entity(tableName = "saved_signatures")
data class SavedSignatureEntity(
    @PrimaryKey val id: String,
    val label: String,
    val isTyped: Boolean,
    val typedText: String = "",
    val fontFamilyId: String = "dancing",
    val colorHex: String = "#1C1E21",
    val pointsJson: String = "[]",
    val imagePath: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface GlassPdfDao {
    @Query("SELECT * FROM pdf_documents ORDER BY lastOpenedAt DESC")
    fun observeAllDocuments(): Flow<List<PdfDocumentEntity>>

    @Query("SELECT * FROM pdf_documents WHERE id = :id LIMIT 1")
    suspend fun getDocumentById(id: String): PdfDocumentEntity?

    @Query("SELECT COUNT(*) FROM pdf_documents")
    suspend fun getDocumentCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: PdfDocumentEntity)

    @Update
    suspend fun updateDocument(document: PdfDocumentEntity)

    @Query("DELETE FROM pdf_documents WHERE id = :id")
    suspend fun deleteDocumentById(id: String)

    @Query("SELECT * FROM saved_signatures ORDER BY createdAt DESC")
    fun observeSignatures(): Flow<List<SavedSignatureEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSignature(signature: SavedSignatureEntity)

    @Query("DELETE FROM saved_signatures WHERE id = :id")
    suspend fun deleteSignatureById(id: String)
}

@Database(
    entities = [PdfDocumentEntity::class, SavedSignatureEntity::class],
    version = 1,
    exportSchema = false
)
abstract class GlassPdfDatabase : RoomDatabase() {
    abstract fun glassPdfDao(): GlassPdfDao

    companion object {
        @Volatile
        private var INSTANCE: GlassPdfDatabase? = null

        fun getInstance(context: Context): GlassPdfDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GlassPdfDatabase::class.java,
                    "glass_pdf_pro.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class GlassPdfRepository(private val dao: GlassPdfDao) {
    val allDocuments: Flow<List<PdfDocumentEntity>> = dao.observeAllDocuments()
    val savedSignatures: Flow<List<SavedSignatureEntity>> = dao.observeSignatures()

    suspend fun getDocumentCount(): Int = dao.getDocumentCount()
    suspend fun getDocumentById(id: String): PdfDocumentEntity? = dao.getDocumentById(id)
    suspend fun upsertDocument(doc: PdfDocumentEntity) = dao.insertDocument(doc)
    suspend fun deleteDocument(id: String) = dao.deleteDocumentById(id)
    suspend fun insertSignature(sig: SavedSignatureEntity) = dao.insertSignature(sig)
    suspend fun deleteSignature(id: String) = dao.deleteSignatureById(id)
}
