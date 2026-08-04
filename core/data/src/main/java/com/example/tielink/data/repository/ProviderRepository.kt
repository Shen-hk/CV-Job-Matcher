package com.example.tielink.data.repository

import com.example.tielink.data.local.SecretCipher
import com.example.tielink.data.local.db.dao.ProviderDao
import com.example.tielink.data.local.db.entity.ProviderEntity
import com.example.tielink.data.local.db.entity.ProviderModelEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProviderRepository @Inject constructor(
    private val dao: ProviderDao,
    private val secretCipher: SecretCipher
) {
    // ── Provider ────────────────────────────────────────────────

    fun getAllFlow(): Flow<List<ProviderEntity>> = dao.getAllFlow().map { providers ->
        providers.map(::decryptProvider)
    }

    suspend fun getAll(): List<ProviderEntity> = dao.getAll().map(::decryptProvider)

    suspend fun getProviderById(id: Long): ProviderEntity? = dao.getById(id)?.let(::decryptProvider)

    suspend fun insertProvider(entity: ProviderEntity): Long = dao.insert(encryptProvider(entity))

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

    private fun encryptProvider(entity: ProviderEntity): ProviderEntity = entity.copy(
        apiKey = secretCipher.encrypt(entity.apiKey)
    )

    private fun decryptProvider(entity: ProviderEntity): ProviderEntity = entity.copy(
        // Legacy rows stay usable; saving the provider migrates its key to encrypted storage.
        apiKey = secretCipher.decrypt(entity.apiKey).orEmpty()
    )
}
