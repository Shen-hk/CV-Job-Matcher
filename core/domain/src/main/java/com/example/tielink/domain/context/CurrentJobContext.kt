package com.example.tielink.domain.context

import com.example.tielink.domain.model.GlobalJdState
import kotlinx.coroutines.flow.StateFlow

interface CurrentJobContext {
    val state: StateFlow<GlobalJdState>

    fun setJd(rawText: String, structuredJson: String = "", companyName: String = "")
    fun setCompanyName(name: String)
    fun clearJd()
}
