package com.example.tielink.data.repository

import android.content.Context
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
    @param:ApplicationContext private val appContext: Context
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
        dao.deactivateAll()
        return dao.insert(version.copy(isActive = true).toEntity())
    }

    suspend fun setActive(id: Long) {
        dao.deactivateAll()
        dao.setActive(id)
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
