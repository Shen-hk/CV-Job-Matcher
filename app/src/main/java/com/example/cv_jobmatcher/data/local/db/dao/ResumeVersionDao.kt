package com.example.cv_jobmatcher.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.cv_jobmatcher.data.local.db.entity.ResumeVersionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ResumeVersionDao {
    @Query("SELECT * FROM resume_versions ORDER BY updated_at DESC")
    fun getAllFlow(): Flow<List<ResumeVersionEntity>>

    @Query("SELECT * FROM resume_versions ORDER BY updated_at DESC")
    suspend fun getAll(): List<ResumeVersionEntity>

    @Query("SELECT * FROM resume_versions WHERE id = :id")
    suspend fun getById(id: Long): ResumeVersionEntity?

    @Query("SELECT * FROM resume_versions WHERE is_active = 1 LIMIT 1")
    suspend fun getActive(): ResumeVersionEntity?

    @Query("SELECT * FROM resume_versions WHERE is_active = 1 LIMIT 1")
    fun getActiveFlow(): Flow<ResumeVersionEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ResumeVersionEntity): Long

    @Update
    suspend fun update(entity: ResumeVersionEntity)

    @Delete
    suspend fun delete(entity: ResumeVersionEntity)

    @Query("UPDATE resume_versions SET is_active = 0")
    suspend fun deactivateAll()

    @Query("UPDATE resume_versions SET is_active = 1 WHERE id = :id")
    suspend fun setActive(id: Long)

    @Query("DELETE FROM resume_versions")
    suspend fun deleteAll()
}
