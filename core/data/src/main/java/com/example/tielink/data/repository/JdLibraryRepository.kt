package com.example.tielink.data.repository

import com.example.tielink.data.local.db.dao.JdLibraryDao
import com.example.tielink.data.local.db.entity.JdLibraryEntity
import com.example.tielink.domain.model.NewJobDescription
import com.example.tielink.domain.model.SavedJobDescription
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JdLibraryRepository @Inject constructor(
    private val dao: JdLibraryDao
) {
    fun getAllFlow(): Flow<List<SavedJobDescription>> = dao.getAllFlow().map { entities ->
        entities.map(::toDomain)
    }

    suspend fun getAll(): List<SavedJobDescription> = dao.getAll().map(::toDomain)

    suspend fun getById(id: Long): SavedJobDescription? = dao.getById(id)?.let(::toDomain)

    suspend fun insert(item: NewJobDescription): Long = dao.insert(item.toEntity())

    suspend fun delete(id: Long) {
        dao.getById(id)?.let { entity -> dao.delete(entity) }
    }

    /**
     * 批量保存 AI 提取的 JD，自动去重（相同公司+职位覆盖旧记录）。
     */
    suspend fun saveFromAi(companyName: String, positionName: String, rawText: String, structuredJson: String, skills: List<String>, salary: String = ""): Long {
        val entity = NewJobDescription(
            companyName = companyName,
            positionName = positionName,
            salary = salary,
            rawText = rawText,
            structuredJson = structuredJson,
            skills = skills.joinToString(","),
            sourceType = "ai_auto"
        )
        return insert(entity)
    }

    private fun toDomain(entity: JdLibraryEntity) = SavedJobDescription(
        id = entity.id,
        companyName = entity.companyName,
        positionName = entity.positionName,
        rawText = entity.rawText,
        structuredJson = entity.structuredJson,
        skills = entity.skills,
        salary = entity.salary,
        sourceType = entity.sourceType,
        createdAt = entity.createdAt
    )

    private fun NewJobDescription.toEntity() = JdLibraryEntity(
        companyName = companyName,
        positionName = positionName,
        rawText = rawText,
        structuredJson = structuredJson,
        skills = skills,
        salary = salary,
        sourceType = sourceType
    )
}
