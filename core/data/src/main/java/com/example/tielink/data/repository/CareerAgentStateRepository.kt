package com.example.tielink.data.repository

import com.example.tielink.data.local.AppPreferences
import com.example.tielink.domain.model.CareerAgentState
import com.example.tielink.domain.model.CareerCompletionPolicy
import com.example.tielink.domain.model.CareerTaskKind
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CareerAgentStateRepository @Inject constructor(
    private val appPreferences: AppPreferences
) {
    private val adapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter(CareerAgentState::class.java)

    suspend fun getState(): CareerAgentState = decode(appPreferences.getCareerAgentStateJson())

    fun observeState(): Flow<CareerAgentState> =
        appPreferences.getCareerAgentStateJsonFlow().map(::decode)

    suspend fun saveState(state: CareerAgentState) {
        updateState { current ->
            require(current.revision == state.revision) { "计划状态已更新，请重试" }
            state
        }
    }

    /** The transformation runs inside DataStore's serialized read/modify/write transaction. */
    suspend fun updateState(transform: (CareerAgentState) -> CareerAgentState): CareerAgentState = decode(
        appPreferences.updateCareerAgentStateJson { json ->
            val current = decode(json)
            val updated = transform(current)
            if (updated == current) json else adapter.toJson(updated.copy(revision = current.revision + 1))
        }
    )

    suspend fun clear() {
        updateState { CareerAgentState() }
    }

    private fun decode(json: String): CareerAgentState {
        if (json.isBlank()) return CareerAgentState()
        return runCatching {
            val state = adapter.fromJson(json) ?: CareerAgentState()
            state.copy(activePlan = state.activePlan?.let { plan -> plan.copy(tasks = plan.tasks.map { task ->
                if (task.kind in setOf(CareerTaskKind.RESUME_STRATEGY, CareerTaskKind.INTERVIEW_PRACTICE, CareerTaskKind.FOLLOW_UP))
                    task.copy(completionPolicy = CareerCompletionPolicy.USER_CONFIRMATION) else task
            }) })
        }
            .getOrDefault(CareerAgentState())
    }
}
