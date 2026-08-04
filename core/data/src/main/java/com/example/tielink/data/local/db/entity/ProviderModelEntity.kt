package com.example.tielink.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 每个提供商下的可用模型列表。
 */
@Entity(
    tableName = "provider_models",
    foreignKeys = [
        ForeignKey(
            entity = ProviderEntity::class,
            parentColumns = ["providerId"],
            childColumns = ["providerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("providerId")]
)
data class ProviderModelEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "modelId")
    val modelId: Long = 0,

    @ColumnInfo(name = "providerId")
    val providerId: Long,

    @ColumnInfo(name = "modelName")
    val modelName: String,

    @ColumnInfo(name = "createTime")
    val createTime: Long = System.currentTimeMillis()
)
