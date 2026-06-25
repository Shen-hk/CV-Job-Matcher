package com.example.tielink.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tielink.data.repository.ResumeVersionRepository
import com.example.tielink.data.repository.TrackingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    resumeVersionRepository: ResumeVersionRepository,
    trackingRepository: TrackingRepository
) : ViewModel() {

    val resumeVersionCount: StateFlow<Int> = resumeVersionRepository.getAllFlow()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val trackingCount: StateFlow<Int> = trackingRepository.getAllFlow()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
}
