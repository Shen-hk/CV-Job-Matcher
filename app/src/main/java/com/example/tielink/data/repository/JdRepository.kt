package com.example.tielink.data.repository

import android.util.Log
import com.example.tielink.data.remote.DeepSeekApiService
import com.example.tielink.data.remote.dto.DeepSeekRequest
import com.example.tielink.data.remote.dto.Message
import com.example.tielink.domain.model.JobDescription
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JdRepository @Inject constructor(
    private val apiService: DeepSeekApiService,
    private val moshi: Moshi
) {
    companion object {
        private const val TAG = "JdRepo"
        private const val MAX_JD_LENGTH = 3000

        private val JD_EXTRACTION_PROMPT = """
你是一个专业的岗位需求分析助手。用户会提供一段招聘JD文本，请从中提取以下信息并以JSON格式输出。

注意：
1. 只输出JSON，不要输出任何其他文字
2. skills 字段列出所有技术栈、工具、专业能力关键词
3. requirements 列出硬性要求（学历、年限、语言等）
4. responsibilities 列出岗位职责
5. nice_to_have 列出加分项/优先条件
6. summary 用一句话概括这个岗位要找什么样的人

JSON格式：
{
  "job_title": "岗位名称",
  "requirements": ["要求1", "要求2"],
  "skills": ["技能1", "技能2"],
  "responsibilities": ["职责1", "职责2"],
  "nice_to_have": ["加分项1"],
  "summary": "一句话总结"
}
""".trimIndent()
    }

    suspend fun extractJobDescription(rawJdText: String): Result<JobDescription> = withContext(Dispatchers.IO) {
        Log.d(TAG, "extractJobDescription: 开始, text长度=${rawJdText.length}")
        try {
            val truncatedText = if (rawJdText.length > MAX_JD_LENGTH) {
                Log.d(TAG, "JD 文本截断: ${rawJdText.length} -> $MAX_JD_LENGTH")
                rawJdText.take(MAX_JD_LENGTH)
            } else rawJdText

            val request = DeepSeekRequest(
                messages = listOf(
                    Message(role = "system", content = JD_EXTRACTION_PROMPT),
                    Message(role = "user", content = truncatedText)
                ),
                temperature = 0.3
            )

            Log.d(TAG, "调用 DeepSeek API (JD 提取)...")
            val response = apiService.chatCompletion(request)
            val content = response.choices.firstOrNull()?.message?.content
                ?: return@withContext Result.failure(Exception("API 返回为空"))
            Log.d(TAG, "API 返回: ${content.length} 字符")

            val jsonStr = extractJson(content)
            Log.d(TAG, "提取的 JSON: ${jsonStr.take(200)}...")
            val adapter = moshi.adapter(JobDescription::class.java)
            val jobDescription = adapter.fromJson(jsonStr)
                ?: return@withContext Result.failure(Exception("JSON 解析失败"))

            Log.i(TAG, "JD 提取完成: title=${jobDescription.jobTitle}, skills=${jobDescription.skills.size}, reqs=${jobDescription.requirements.size}")
            Result.success(jobDescription)
        } catch (e: Exception) {
            Log.e(TAG, "JD 提取失败: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun extractJson(content: String): String {
        val trimmed = content.trim()
        return when {
            trimmed.startsWith("```json") -> trimmed.removePrefix("```json").removeSuffix("```").trim()
            trimmed.startsWith("```") -> trimmed.removePrefix("```").removeSuffix("```").trim()
            else -> trimmed
        }
    }
}
