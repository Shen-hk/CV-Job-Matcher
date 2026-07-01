package com.example.tielink.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * AI 模型提供商 — 支持 DeepSeek、Ollama 等自定义 API。
 */
@Entity(tableName = "providers")
data class ProviderEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "providerId")
    val providerId: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "baseUrl")
    val baseUrl: String,

    @ColumnInfo(name = "apiKey")
    val apiKey: String,

    @ColumnInfo(name = "apiFormat")
    val apiFormat: String,

    @ColumnInfo(name = "createTime")
    val createTime: Long = System.currentTimeMillis()
)
