package com.example.tielink.data.repository

import com.example.tielink.data.local.AppPreferences
import com.example.tielink.domain.model.AgentContext
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Agent 上下文仓库
 * 管理 AgentContext 的持久化和访问
 */
@Singleton
class AgentContextRepository @Inject constructor(
    private val appPreferences: AppPreferences
) {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val adapter = moshi.adapter(AgentContext::class.java)

    /**
     * 获取当前 Agent 上下文
     */
    suspend fun getAgentContext(): AgentContext {
        val json = appPreferences.getAgentContextJson()
        return if (json.isNotBlank()) {
            try {
                adapter.fromJson(json) ?: AgentContext()
            } catch (e: Exception) {
                AgentContext()
            }
        } else {
            AgentContext()
        }
    }

    /**
     * 观察 Agent 上下文变化
     */
    fun observeAgentContext(): Flow<AgentContext> {
        // 简化实现，不观察变化，只在需要时获取
        return kotlinx.coroutines.flow.flow { emit(getAgentContext()) }
    }

    /**
     * 保存 Agent 上下文
     */
    suspend fun saveAgentContext(context: AgentContext) {
        val json = adapter.toJson(context)
        appPreferences.setAgentContextJson(json)
    }

    /**
     * 更新部分上下文
     */
    suspend fun updateAgentContext(
        currentJdId: Long? = null,
        currentJdText: String? = null,
        currentJdCompany: String? = null,
        currentResumeVersionId: Long? = null,
        activeInterviewSessionId: Long? = null,
        activeDebriefSessionId: Long? = null,
        conversationSummary: String? = null
    ) {
        val current = getAgentContext()
        val updated = current.copy(
            currentJdId = currentJdId ?: current.currentJdId,
            currentJdText = currentJdText ?: current.currentJdText,
            currentJdCompany = currentJdCompany ?: current.currentJdCompany,
            currentResumeVersionId = currentResumeVersionId ?: current.currentResumeVersionId,
            activeInterviewSessionId = activeInterviewSessionId ?: current.activeInterviewSessionId,
            activeDebriefSessionId = activeDebriefSessionId ?: current.activeDebriefSessionId,
            conversationSummary = conversationSummary ?: current.conversationSummary,
            lastActiveTime = System.currentTimeMillis()
        )
        saveAgentContext(updated)
    }

    /**
     * 清空上下文
     */
    suspend fun clearAgentContext() {
        saveAgentContext(AgentContext())
    }
}
