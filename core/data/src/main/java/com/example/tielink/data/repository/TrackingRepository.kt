package com.example.tielink.data.repository

import androidx.room.withTransaction
import com.example.tielink.data.local.db.AppDatabase
import com.example.tielink.data.local.db.entity.AgentOperationReceipt

import com.example.tielink.data.local.db.dao.TrackingDao
import com.example.tielink.data.local.db.entity.TrackingEntity
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class TrackingItem(
    val id: Long = 0,
    val companyName: String,
    val positionName: String,
    val status: String = "已投",
    val resumeVersionId: Long? = null,
    val jdRawText: String = "",
    val notes: String = "",
    val timeline: List<TimelineEvent> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class TimelineEvent(
    val status: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Singleton
class TrackingRepository @Inject constructor(
    private val dao: TrackingDao,
    private val moshi: Moshi,
    private val database: AppDatabase
) {
    fun getAllFlow(): Flow<List<TrackingItem>> = dao.getAllFlow().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun getAll(): List<TrackingItem> = dao.getAll().map { it.toDomain() }

    suspend fun getById(id: Long): TrackingItem? = dao.getById(id)?.toDomain()

    fun getByStatus(status: String): Flow<List<TrackingItem>> =
        dao.getByStatus(status).map { list -> list.map { it.toDomain() } }

    fun countFlow(): Flow<Int> = dao.countFlow()

    suspend fun insert(item: TrackingItem): Long {
        // Append initial timeline event if empty
        val timeline = if (item.timeline.isEmpty()) {
            listOf(TimelineEvent(status = item.status, timestamp = item.createdAt))
        } else item.timeline
        return dao.insert(item.copy(timeline = timeline).toEntity())
    }

    suspend fun insertOnce(operationId: String, item: TrackingItem): Long = database.withTransaction {
        val receipts = database.agentOperationReceiptDao()
        val receipt = receipts.get(operationId)
        if (receipt != null) {
            require(receipt.kind == "tracking" && dao.getById(receipt.resultId) != null) { "已保存的投递记录已删除" }
            receipt.resultId
        } else {
            val existing = dao.getAll().firstOrNull {
                it.companyName == item.companyName && it.positionName == item.positionName && it.jdRawText == item.jdRawText &&
                    it.resumeVersionId == item.resumeVersionId
            }
            val id = existing?.id ?: insert(item)
            if (existing?.status == "待投" && item.status == "已投") updateStatus(id, "已投")
            receipts.insert(AgentOperationReceipt(operationId, "tracking", id))
            id
        }
    }

    suspend fun committedItemId(operationId: String): Long? {
        val receipt = database.agentOperationReceiptDao().get(operationId)?.takeIf { it.kind == "tracking" } ?: return null
        return receipt.resultId.takeIf { dao.getById(it) != null }
    }

    suspend fun update(item: TrackingItem) {
        dao.update(item.toEntity())
    }

    suspend fun updateStatus(id: Long, newStatus: String) {
        val entity = dao.getById(id) ?: return
        val timeline = parseTimeline(entity.timeline).toMutableList()
        timeline.add(TimelineEvent(status = newStatus))
        val timelineJson = moshi.adapter<List<Map<String, Any>>>(List::class.java)
            .toJson(timeline.map { mapOf("status" to it.status, "timestamp" to it.timestamp) })
        dao.update(entity.copy(
            status = newStatus,
            timeline = timelineJson,
            updatedAt = System.currentTimeMillis()
        ))
    }

    suspend fun delete(item: TrackingItem) {
        dao.delete(item.toEntity())
    }

    private fun TrackingEntity.toDomain(): TrackingItem = TrackingItem(
        id = id, companyName = companyName, positionName = positionName,
        status = status, resumeVersionId = resumeVersionId, jdRawText = jdRawText,
        notes = notes, timeline = parseTimeline(timeline),
        createdAt = createdAt, updatedAt = updatedAt
    )

    private fun parseTimeline(json: String): List<TimelineEvent> {
        return try {
            val listType = com.squareup.moshi.Types.newParameterizedType(
                List::class.java, Map::class.java, String::class.java, Any::class.java
            )
            val raw: List<Map<String, Any>>? = moshi.adapter<List<Map<String, Any>>>(listType).fromJson(json)
            raw?.mapNotNull { entry ->
                val status = entry["status"] as? String ?: return@mapNotNull null
                val ts = (entry["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
                TimelineEvent(status, ts)
            } ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    private fun TrackingItem.toEntity(): TrackingEntity {
        val timelineJson = try {
            moshi.adapter<List<Map<String, Any>>>(List::class.java)
                .toJson(timeline.map { mapOf("status" to it.status, "timestamp" to it.timestamp) })
        } catch (_: Exception) { "[]" }
        return TrackingEntity(
            id = id, companyName = companyName, positionName = positionName,
            status = status, resumeVersionId = resumeVersionId, jdRawText = jdRawText,
            notes = notes, timeline = timelineJson,
            createdAt = createdAt, updatedAt = updatedAt
        )
    }
}
