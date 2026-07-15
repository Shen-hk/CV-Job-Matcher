package com.example.tielink.data.repository

import com.example.tielink.data.local.db.dao.JdLibraryDao
import com.example.tielink.data.local.db.entity.JdLibraryEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JdLibraryRepository @Inject constructor(
    private val dao: JdLibraryDao
) {
    fun getAllFlow(): Flow<List<JdLibraryEntity>> = dao.getAllFlow()

    suspend fun getAll(): List<JdLibraryEntity> = dao.getAll()

    suspend fun getById(id: Long): JdLibraryEntity? = dao.getById(id)

    suspend fun insert(entity: JdLibraryEntity): Long = dao.insert(entity)

    suspend fun delete(entity: JdLibraryEntity) = dao.delete(entity)

    /**
     * 批量保存 AI 提取的 JD，自动去重（相同公司+职位覆盖旧记录）。
     */
    suspend fun saveFromAi(companyName: String, positionName: String, rawText: String, structuredJson: String, skills: List<String>, salary: String = ""): Long {
        val entity = JdLibraryEntity(
            companyName = companyName,
            positionName = positionName,
            salary = salary,
            rawText = rawText,
            structuredJson = structuredJson,
            skills = skills.joinToString(","),
            sourceType = "ai_auto"
        )
        return dao.insert(entity)
    }
}
