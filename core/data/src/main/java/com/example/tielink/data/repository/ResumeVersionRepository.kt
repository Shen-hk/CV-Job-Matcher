package com.example.tielink.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.example.tielink.data.local.db.AppDatabase
import com.example.tielink.data.local.db.entity.AgentOperationReceipt
import com.example.tielink.data.local.db.dao.ResumeVersionDao
import com.example.tielink.data.local.db.entity.ResumeVersionEntity
import com.example.tielink.domain.model.ResumeVersion
import com.example.tielink.util.FileParser
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ResumeVersionRepository @Inject constructor(
    private val dao: ResumeVersionDao,
    private val moshi: Moshi,
    @param:ApplicationContext private val appContext: Context,
    private val database: AppDatabase
) {
    private val stringListType = Types.newParameterizedType(List::class.java, String::class.java)

    fun getAllFlow(): Flow<List<ResumeVersion>> = dao.getAllFlow().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun getAll(): List<ResumeVersion> = dao.getAll().map { it.toDomain() }

    suspend fun getById(id: Long): ResumeVersion? = hydrateContentIfNeeded(dao.getById(id)?.toDomain())

    suspend fun getActive(): ResumeVersion? = hydrateContentIfNeeded(dao.getActive()?.toDomain())

    fun getActiveFlow(): Flow<ResumeVersion?> = dao.getActiveFlow().map { it?.toDomain() }

    suspend fun save(version: ResumeVersion) {
        val entity = version.toEntity()
        if (version.id > 0) {
            dao.update(entity)
        } else {
            dao.insert(entity)
        }
    }

    suspend fun insertAndActivate(version: ResumeVersion): Long {
        return database.withTransaction {
            dao.deactivateAll()
            dao.insert(version.copy(isActive = true).toEntity())
        }
    }

    suspend fun insertAndActivateOnce(operationId: String, version: ResumeVersion, expectedInputs: com.example.tielink.domain.model.CareerTaskInputs): Long = database.withTransaction {
        val receipts = database.agentOperationReceiptDao()
        val existing = receipts.get(operationId)
        if (existing != null) {
            require(existing.kind == "resume" && dao.getById(existing.resultId) != null) { "已保存的简历已删除，请重新生成" }
            existing.resultId
        } else {
            val source = dao.getActive()
            require(source?.id == expectedInputs.resumeVersionId) { "当前简历版本已变化，请重新生成" }
            val sourceText = source?.let { if (it.isPolished) it.cleanedText.ifBlank { it.rawText } else it.rawText.ifBlank { it.cleanedText } }.orEmpty()
            require(com.example.tielink.domain.agent.CareerInputIdentity.fingerprint(sourceText) == expectedInputs.resumeFingerprint) { "原简历内容已变化，请重新生成" }
            dao.deactivateAll()
            val id = dao.insert(version.copy(id = 0, isActive = true).toEntity())
            receipts.insert(AgentOperationReceipt(operationId, "resume", id))
            id
        }
    }

    suspend fun committedVersionId(operationId: String): Long? {
        val receipt = database.agentOperationReceiptDao().get(operationId)?.takeIf { it.kind == "resume" } ?: return null
        return receipt.resultId.takeIf { dao.getById(it) != null }
    }

    suspend fun setActive(id: Long) {
        database.withTransaction {
            dao.deactivateAll()
            dao.setActive(id)
        }
    }

    suspend fun delete(version: ResumeVersion) {
        dao.delete(version.toEntity())
    }

    private fun ResumeVersionEntity.toDomain(): ResumeVersion {
        val tags: List<String> = try {
            moshi.adapter<List<String>>(stringListType).fromJson(this.tags) ?: emptyList()
        } catch (_: Exception) { emptyList() }
        return ResumeVersion(
            id = id, name = name, rawText = rawText, cleanedText = cleanedText,
            jdMatchedWith = jdMatchedWith, matchScore = matchScore, tags = tags,
            isActive = isActive, originalFilePath = originalFilePath,
            originalMimeType = originalMimeType, isPolished = isPolished,
            createdAt = createdAt, updatedAt = updatedAt
        )
    }

    private fun ResumeVersion.toEntity(): ResumeVersionEntity {
        val tagsJson = moshi.adapter<List<String>>(stringListType).toJson(tags)
        return ResumeVersionEntity(
            id = id, name = name, rawText = rawText, cleanedText = cleanedText,
            jdMatchedWith = jdMatchedWith, matchScore = matchScore, tags = tagsJson,
            isActive = isActive, originalFilePath = originalFilePath,
            originalMimeType = originalMimeType, isPolished = isPolished,
            createdAt = createdAt, updatedAt = updatedAt
        )
    }

    private suspend fun hydrateContentIfNeeded(version: ResumeVersion?): ResumeVersion? {
        if (version == null) return null
        val existingText = version.rawText.ifBlank { version.cleanedText }.trim()
        if (existingText.isNotBlank() || version.originalFilePath.isBlank()) return version

        val extractedText = withContext(Dispatchers.IO) {
            FileParser.extractTextFromFile(
                context = appContext,
                filePath = version.originalFilePath,
                mimeType = version.originalMimeType.ifBlank { null }
            ).getOrNull()?.trim().orEmpty()
        }
        if (extractedText.isBlank()) return version

        val hydrated = version.copy(
            rawText = extractedText,
            cleanedText = extractedText,
            updatedAt = System.currentTimeMillis()
        )
        dao.update(hydrated.toEntity())
        return hydrated
    }
}
