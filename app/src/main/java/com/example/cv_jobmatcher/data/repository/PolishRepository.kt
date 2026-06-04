package com.example.cv_jobmatcher.data.repository

import android.util.Log
import com.example.cv_jobmatcher.data.remote.DeepSeekApiService
import com.example.cv_jobmatcher.data.remote.dto.DeepSeekRequest
import com.example.cv_jobmatcher.data.remote.dto.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PolishRepository @Inject constructor(
    private val apiService: DeepSeekApiService
) {
    companion object {
        private const val TAG = "PolishRepo"
        private const val MAX_JD_LENGTH = 3000
        private const val MAX_RESUME_LENGTH = 5000

        private val PROMPT_FULL = """
你是一个资深简历优化顾问。根据JD对简历进行优化，提高ATS匹配度。

【润色规则】
1. 不编造经历/项目/数据
2. 用JD关键词替换原文措辞
3. 匹配JD的经验和技能提前、重点描述
4. 量化已有成果

【排版格式（严格遵守）】
第一行：只写姓名
第二行：目标职位
第三行：联系方式，用逗号分隔
之后按顺序：个人总结 → 工作经历 → 教育背景 → 项目经历 → 技能列表
- 模块标题独占一行，例如：工作经历
- 工作/项目/教育条目：主体 | 时间 | 地点（用竖线分隔）
- 描述要点：以 - 开头
- 技能：逗号分隔写在一行
- 模块之间空一行

输出格式：润色后简历全文，末尾：
### 优化说明
(100字内)

### 匹配分析
{"score":85,"suggestions":["建议1","建议2"]}
""".trimIndent()

        private val PROMPT_PARTIAL = """
你是一个简历微调顾问。只对简历进行最小化修改，使其更匹配JD。

【微调规则】
1. 只修改与JD要求明显不匹配的措辞和关键词
2. 绝对不编造经历/项目/数据
3. 保持原文段落结构和顺序完全不变
4. 保持所有非技能类的描述文字不变
5. 仅替换技术栈关键词、添加JD要求的技能术语、调整动词使其更贴合JD
6. 如果原文已匹配JD，不要做任何改动

【排版格式（严格遵守）】
完全保持原文的段落结构和顺序。
第一行：只写姓名
第二行：目标职位（如果原文没有，根据JD添加）
第三行：联系方式，用逗号分隔
模块标题独占一行。工作/项目条目：主体 | 时间 | 地点。
描述要点：以 - 开头。技能：逗号分隔。

输出格式：微调后简历全文，末尾：
### 优化说明
(50字内，说明修改了什么)

### 匹配分析
{"score":85,"suggestions":["建议1"]}
""".trimIndent()
    }

    suspend fun polishResume(jdText: String, resumeText: String, fullPolish: Boolean = true): Result<String> =
        withContext(Dispatchers.IO) {
            val mode = if (fullPolish) "全篇优化" else "部分优化"
            Log.d(TAG, "polishResume: $mode, JD=${jdText.length}, Resume=${resumeText.length}")
            try {
                val prompt = if (fullPolish) PROMPT_FULL else PROMPT_PARTIAL
                val userMsg = "【JD】\n${jdText.take(MAX_JD_LENGTH)}\n\n【简历】\n${resumeText.take(MAX_RESUME_LENGTH)}"
                val req = DeepSeekRequest(
                    messages = listOf(Message("system", prompt), Message("user", userMsg)),
                    temperature = if (fullPolish) 0.7 else 0.3, maxTokens = 4096
                )
                val content = apiService.chatCompletion(req).choices.firstOrNull()?.message?.content
                    ?: return@withContext Result.failure(Exception("API 返回为空"))
                Log.i(TAG, "$mode 完成: ${content.length} chars")
                Result.success(content)
            } catch (e: Exception) {
                Log.e(TAG, "润色失败: ${e.message}", e)
                Result.failure(e)
            }
        }
}
