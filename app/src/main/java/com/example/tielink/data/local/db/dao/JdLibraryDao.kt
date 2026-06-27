package com.example.tielink.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.tielink.data.local.db.entity.JdLibraryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface JdLibraryDao {
    @Query("SELECT * FROM jd_library ORDER BY created_at DESC")
    fun getAllFlow(): Flow<List<JdLibraryEntity>>

    @Query("SELECT * FROM jd_library ORDER BY created_at DESC")
    suspend fun getAll(): List<JdLibraryEntity>

    @Query("SELECT * FROM jd_library WHERE id = :id")
    suspend fun getById(id: Long): JdLibraryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: JdLibraryEntity): Long

    @Delete
    suspend fun delete(entity: JdLibraryEntity)
}
