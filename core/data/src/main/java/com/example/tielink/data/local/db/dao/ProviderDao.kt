package com.example.tielink.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.tielink.data.local.db.entity.ProviderEntity
import com.example.tielink.data.local.db.entity.ProviderModelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProviderDao {
    // ── Provider ────────────────────────────────────────────────

    @Query("SELECT * FROM providers ORDER BY createTime DESC")
    fun getAllFlow(): Flow<List<ProviderEntity>>

    @Query("SELECT * FROM providers ORDER BY createTime DESC")
    suspend fun getAll(): List<ProviderEntity>

    @Query("SELECT * FROM providers WHERE providerId = :id")
    suspend fun getById(id: Long): ProviderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ProviderEntity): Long

    @Delete
    suspend fun delete(entity: ProviderEntity)

    // ── Provider Models ─────────────────────────────────────────

    @Query("SELECT * FROM provider_models WHERE providerId = :providerId ORDER BY createTime DESC")
    fun getModelsByProviderIdFlow(providerId: Long): Flow<List<ProviderModelEntity>>

    @Query("SELECT * FROM provider_models WHERE providerId = :providerId ORDER BY createTime DESC")
    suspend fun getModelsByProviderId(providerId: Long): List<ProviderModelEntity>

    @Query("SELECT * FROM provider_models WHERE modelId = :modelId")
    suspend fun getModelById(modelId: Long): ProviderModelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(entity: ProviderModelEntity): Long

    @Query("DELETE FROM provider_models WHERE providerId = :providerId")
    suspend fun deleteModelsByProviderId(providerId: Long)

    @Delete
    suspend fun deleteModel(entity: ProviderModelEntity)

    // ── Transactional ───────────────────────────────────────────

    @Transaction
    suspend fun deleteProviderWithModels(provider: ProviderEntity) {
        deleteModelsByProviderId(provider.providerId)
        delete(provider)
    }
}
