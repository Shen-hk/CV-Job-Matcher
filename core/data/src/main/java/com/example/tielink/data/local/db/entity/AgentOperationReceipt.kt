package com.example.tielink.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Committed in the same Room transaction as the business write. */
@Entity(tableName = "agent_operation_receipts")
data class AgentOperationReceipt(
    @PrimaryKey val operationId: String,
    val kind: String,
    val resultId: Long,
    val createdAt: Long = System.currentTimeMillis()
)
