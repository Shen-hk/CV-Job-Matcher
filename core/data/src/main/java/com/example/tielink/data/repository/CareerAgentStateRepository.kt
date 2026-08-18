package com.example.tielink.data.repository

import com.example.tielink.data.local.AppPreferences
import com.example.tielink.domain.model.CareerAgentState
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
        appPreferences.setCareerAgentStateJson(adapter.toJson(state))
    }

    suspend fun clear() {
        saveState(CareerAgentState())
    }

    private fun decode(json: String): CareerAgentState {
        if (json.isBlank()) return CareerAgentState()
        return runCatching { adapter.fromJson(json) ?: CareerAgentState() }
            .getOrDefault(CareerAgentState())
    }
}
