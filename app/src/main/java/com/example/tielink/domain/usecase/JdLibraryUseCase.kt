package com.example.tielink.domain.usecase

import com.example.tielink.data.repository.JdLibraryRepository
import com.example.tielink.domain.agent.AgentChatGateway
import com.example.tielink.domain.agent.AgentChatMessage
import com.example.tielink.domain.agent.AgentChatRequest
import com.example.tielink.domain.model.NewJobDescription
import com.example.tielink.domain.model.SavedJobDescription
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Coordinates JD persistence and optional AI-based extraction for presentation callers. */
@Singleton
class JdLibraryUseCase @Inject constructor(
    private val repository: JdLibraryRepository,
    private val agentChatGateway: AgentChatGateway,
    private val moshi: Moshi
) {
    fun observeAll(): Flow<List<SavedJobDescription>> = repository.getAllFlow()

    suspend fun getById(id: Long): SavedJobDescription? = repository.getById(id)

    suspend fun delete(id: Long) = repository.delete(id)

    suspend fun saveFromText(rawText: String, sourceType: String): Long = withContext(Dispatchers.IO) {
        val extraction = runCatching { extract(rawText) }.getOrDefault(JdExtraction())
        repository.insert(
            NewJobDescription(
                companyName = extraction.company,
                positionName = extraction.position,
                salary = extraction.salary,
                rawText = rawText,
                skills = extraction.skills.joinToString(","),
                sourceType = sourceType
            )
        )
    }

    private suspend fun extract(text: String): JdExtraction {
        val response = agentChatGateway.complete(
            AgentChatRequest(
                messages = listOf(
                    AgentChatMessage("system", EXTRACTION_PROMPT),
                    AgentChatMessage("user", "请提取: ${text.take(MAX_EXTRACTION_INPUT_LENGTH)}")
                ),
                temperature = 0.3,
                maxTokens = 300
            )
        )
        val json = response.content
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        return moshi.adapter(JdExtraction::class.java).fromJson(json) ?: JdExtraction()
    }

    private companion object {
        const val MAX_EXTRACTION_INPUT_LENGTH = 2_000
        val EXTRACTION_PROMPT = """你是一位招聘专家。请从以下岗位描述中提取信息，只返回JSON格式：
{"company":"公司名（如未提及则为空字符串）","position":"职位名称（如未提及则为空字符串）","salary":"薪资范围（如20k-40k，未提及则为空字符串）","skills":["技能1","技能2","技能3"]}"""
    }
}

@JsonClass(generateAdapter = false)
data class JdExtraction(
    val company: String = "",
    val position: String = "",
    val salary: String = "",
    val skills: List<String> = emptyList()
)
