package com.example.tielink.data.repository

import com.example.tielink.data.local.db.dao.ProviderDao
import com.example.tielink.data.local.db.entity.ProviderEntity
import com.example.tielink.data.local.db.entity.ProviderModelEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProviderRepository @Inject constructor(
    private val dao: ProviderDao
) {
    // ── Provider ────────────────────────────────────────────────

    fun getAllFlow(): Flow<List<ProviderEntity>> = dao.getAllFlow()

    suspend fun getAll(): List<ProviderEntity> = dao.getAll()

    suspend fun getProviderById(id: Long): ProviderEntity? = dao.getById(id)

    suspend fun insertProvider(entity: ProviderEntity): Long = dao.insert(entity)

    suspend fun deleteProvider(entity: ProviderEntity) = dao.delete(entity)

    suspend fun deleteProviderWithModels(provider: ProviderEntity) = dao.deleteProviderWithModels(provider)

    // ── Provider Models ─────────────────────────────────────────

    fun getModelsByProviderIdFlow(providerId: Long): Flow<List<ProviderModelEntity>> =
        dao.getModelsByProviderIdFlow(providerId)

    suspend fun getModelsByProviderId(providerId: Long): List<ProviderModelEntity> =
        dao.getModelsByProviderId(providerId)

    suspend fun getModelById(modelId: Long): ProviderModelEntity? = dao.getModelById(modelId)

    suspend fun insertModel(entity: ProviderModelEntity): Long = dao.insertModel(entity)

    suspend fun deleteModelsByProviderId(providerId: Long) = dao.deleteModelsByProviderId(providerId)

    suspend fun deleteModel(entity: ProviderModelEntity) = dao.deleteModel(entity)
}
