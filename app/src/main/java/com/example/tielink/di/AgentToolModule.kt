package com.example.tielink.di

import com.example.tielink.domain.usecase.AgentTool
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

/**
 * 声明空的工具集合，让业务模块可以用 @IntoSet 按需贡献自定义 AgentTool。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AgentToolModule {
    @Multibinds
    abstract fun bindAgentTools(): Set<AgentTool>
}
