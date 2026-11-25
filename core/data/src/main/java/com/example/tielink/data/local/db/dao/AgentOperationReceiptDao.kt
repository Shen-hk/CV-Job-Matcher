package com.example.tielink.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.tielink.data.local.db.entity.AgentOperationReceipt

@Dao
interface AgentOperationReceiptDao {
    @Query("SELECT * FROM agent_operation_receipts WHERE operationId = :operationId")
    suspend fun get(operationId: String): AgentOperationReceipt?

    @Insert
    suspend fun insert(receipt: AgentOperationReceipt)
}
