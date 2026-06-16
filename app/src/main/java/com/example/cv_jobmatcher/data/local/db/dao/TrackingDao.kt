package com.example.cv_jobmatcher.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.cv_jobmatcher.data.local.db.entity.TrackingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackingDao {
    @Query("SELECT * FROM tracking ORDER BY updated_at DESC")
    fun getAllFlow(): Flow<List<TrackingEntity>>

    @Query("SELECT * FROM tracking ORDER BY updated_at DESC")
    suspend fun getAll(): List<TrackingEntity>

    @Query("SELECT * FROM tracking WHERE id = :id")
    suspend fun getById(id: Long): TrackingEntity?

    @Query("SELECT * FROM tracking WHERE status = :status ORDER BY updated_at DESC")
    fun getByStatus(status: String): Flow<List<TrackingEntity>>

    @Query("SELECT COUNT(*) FROM tracking")
    fun countFlow(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TrackingEntity): Long

    @Update
    suspend fun update(entity: TrackingEntity)

    @Delete
    suspend fun delete(entity: TrackingEntity)

    @Query("DELETE FROM tracking")
    suspend fun deleteAll()
}
