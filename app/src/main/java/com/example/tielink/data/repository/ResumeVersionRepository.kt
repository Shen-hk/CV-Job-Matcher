package com.example.tielink.data.repository

import com.example.tielink.data.local.db.dao.ResumeVersionDao
import com.example.tielink.data.local.db.entity.ResumeVersionEntity
import com.example.tielink.domain.model.ResumeVersion
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ResumeVersionRepository @Inject constructor(
    private val dao: ResumeVersionDao,
    private val moshi: Moshi
) {
    private val stringListType = Types.newParameterizedType(List::class.java, String::class.java)

    fun getAllFlow(): Flow<List<ResumeVersion>> = dao.getAllFlow().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun getAll(): List<ResumeVersion> = dao.getAll().map { it.toDomain() }

    suspend fun getById(id: Long): ResumeVersion? = dao.getById(id)?.toDomain()

    suspend fun getActive(): ResumeVersion? = dao.getActive()?.toDomain()

    fun getActiveFlow(): Flow<ResumeVersion?> = dao.getActiveFlow().map { it?.toDomain() }

    suspend fun save(version: ResumeVersion) {
        val entity = version.toEntity()
        if (version.id > 0) {
            dao.update(entity)
        } else {
            dao.insert(entity)
        }
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
            isActive = isActive, createdAt = createdAt, updatedAt = updatedAt
        )
    }

    private fun ResumeVersion.toEntity(): ResumeVersionEntity {
        val tagsJson = moshi.adapter<List<String>>(stringListType).toJson(tags)
        return ResumeVersionEntity(
            id = id, name = name, rawText = rawText, cleanedText = cleanedText,
            jdMatchedWith = jdMatchedWith, matchScore = matchScore, tags = tagsJson,
            isActive = isActive, createdAt = createdAt, updatedAt = updatedAt
        )
    }
}
