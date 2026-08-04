package com.example.tielink.di

import com.example.tielink.data.remote.AgentChatGatewayImpl
import com.example.tielink.data.remote.AgentPromptSourceImpl
import com.example.tielink.domain.agent.AgentChatGateway
import com.example.tielink.domain.agent.AgentPromptSource
import com.example.tielink.domain.context.CurrentJobContext
import com.example.tielink.ui.CurrentJobContextStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AgentGatewayModule {
    @Binds
    abstract fun bindAgentChatGateway(impl: AgentChatGatewayImpl): AgentChatGateway

    @Binds
    abstract fun bindAgentPromptSource(impl: AgentPromptSourceImpl): AgentPromptSource

    @Binds
    abstract fun bindCurrentJobContext(impl: CurrentJobContextStore): CurrentJobContext
}
