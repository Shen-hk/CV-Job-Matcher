package com.example.cv_jobmatcher.ui.result

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cv_jobmatcher.domain.model.MatchLevel
import com.example.cv_jobmatcher.ui.components.ScoreRingChart
import com.example.cv_jobmatcher.ui.components.ErrorBanner
import com.example.cv_jobmatcher.ui.components.SectionCard
import com.example.cv_jobmatcher.util.PdfGenerator
import com.example.cv_jobmatcher.ui.theme.MatchGreen
import com.example.cv_jobmatcher.ui.theme.MissRed
import com.example.cv_jobmatcher.util.DocxFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ResultScreen(
    onNavigateBack: () -> Unit,
    onNavigateToHistory: () -> Unit,
    viewModel: ResultViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var iterativeInstruction by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("润色结果") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(Icons.Default.History, contentDescription = "历史记录")
                    }
                }
            )
        }
    ) { padding ->
        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.error != null -> {
                Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                    ErrorBanner(message = state.error!!)
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    // ═══ Score Ring Card ══════════════════════
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = when (state.matchLevel) {
                                MatchLevel.HIGH   -> Color(0xFFE8F5E9)
                                MatchLevel.MEDIUM -> Color(0xFFFFF3E0)
                                MatchLevel.LOW    -> Color(0xFFFFEBEE)
                            }
                        )
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            ScoreRingChart(
                                score = state.matchScore,
                                level = state.matchLevel,
                                size = 160.dp
                            )
                            if (state.jdTitle.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "目标岗位: ${state.jdTitle}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // ═══ Keywords Panel ════════════════════════
                    if (state.matchedKeywords.isNotEmpty() || state.missingKeywords.isNotEmpty()) {
                        KeywordsPanel(
                            matched = state.matchedKeywords,
                            missing = state.missingKeywords
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "注：关键词来自 JD 结构化提取的技能列表，通过文本匹配计算（非 AI 猜测）。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                    }

                    // ═══ Suggestions ═══════════════════════════
                    if (state.suggestions.isNotEmpty()) {
                        SuggestionsPanel(suggestions = state.suggestions)
                        Spacer(Modifier.height(16.dp))
                    }

                    // ═══ Optimization Note ═════════════════════
                    if (state.optimizationNote.isNotBlank()) {
                        Card(
                            Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MatchGreen.copy(alpha = 0.08f))
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text("优化说明", style = MaterialTheme.typography.labelLarge, color = MatchGreen)
                                Spacer(Modifier.height(4.dp))
                                Text(state.optimizationNote, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    // ═══ Iterative Polish ══════════════════════
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("继续优化", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "告诉 AI 你还想怎么改，它会基于当前简历继续调整",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))

                            if (state.iterativeHistory.isNotEmpty()) {
                                state.iterativeHistory.forEach { h ->
                                    Text(
                                        h,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.height(2.dp))
                                }
                                Spacer(Modifier.height(4.dp))
                            }

                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = iterativeInstruction,
                                    onValueChange = { iterativeInstruction = it },
                                    placeholder = { Text("例：把项目经历中的 Spring 改成 Spring Boot") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    enabled = !state.isIterativePolishing
                                )
                                IconButton(
                                    onClick = {
                                        if (iterativeInstruction.isNotBlank()) {
                                            viewModel.iterativePolish(iterativeInstruction)
                                            iterativeInstruction = ""
                                        }
                                    },
                                    enabled = !state.isIterativePolishing && iterativeInstruction.isNotBlank()
                                ) {
                                    if (state.isIterativePolishing) {
                                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(
                                            Icons.AutoMirrored.Filled.Send,
                                            contentDescription = "发送",
                                            tint = if (iterativeInstruction.isNotBlank())
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // ═══ Template Selector ══════════════════════
                    Text("选择导出模板", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))

                    // DOCX 模板选择
                    Text("DOCX格式", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DocxFormatter.Template.entries.forEach { tpl ->
                            FilterChip(
                                selected = state.selectedDocxTemplate == tpl,
                                onClick = { viewModel.selectDocxTemplate(tpl) },
                                label = { Text(tpl.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // PDF 模板选择
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("PDF格式", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Text("HTML渲染", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Switch(
                            checked = state.useHtmlPdf,
                            onCheckedChange = viewModel::toggleHtmlPdf
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    if (state.useHtmlPdf) {
                        Card(
                            Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f))
                        ) {
                            Text(
                                "HTML渲染模式：使用 WebView 渲染网页简历后导出 PDF，排版更精美，接近网页预览效果",
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PdfGenerator.Template.entries.forEach { tpl ->
                                FilterChip(
                                    selected = state.selectedPdfTemplate == tpl,
                                    onClick = { viewModel.selectPdfTemplate(tpl) },
                                    label = { Text(tpl.label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer
                                    )
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // ═══ Export Button ══════════════════════════
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = viewModel::exportDocx,
                            modifier = Modifier.weight(1f),
                            enabled = !state.isExporting
                        ) {
                            if (state.isExporting) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text("导出 DOCX")
                            }
                        }

                        Button(
                            onClick = viewModel::exportPdf,
                            modifier = Modifier.weight(1f),
                            enabled = !state.isExporting
                        ) {
                            if (state.isExporting) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text(if (state.useHtmlPdf) "导出 HTML PDF" else "导出 PDF")
                            }
                        }

                        state.exportFile?.let {
                            OutlinedButton(
                                onClick = { viewModel.shareFile(context) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Share, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("分享")
                            }
                        }
                    }

                    Spacer(Modifier.height(32.dp))

                    // ═══ Polished Resume ════════════════════════
                    SectionCard(title = "润色后简历") {
                        Text(state.polishedResume.ifBlank { "（暂无内容）" }, style = MaterialTheme.typography.bodyMedium)
                    }

                    Spacer(Modifier.height(32.dp))

                    // ═══ Original (collapsed) ═══════════════════
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    Text("原始简历", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        state.originalResume.take(500) + if (state.originalResume.length > 500) "\n..." else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun KeywordsPanel(matched: List<String>, missing: List<String>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("关键词对比", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(12.dp))

            if (matched.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = MatchGreen, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("已匹配 (${matched.size})", style = MaterialTheme.typography.labelMedium, color = MatchGreen)
                }
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    matched.forEach { kw ->
                        Box(
                            Modifier
                                .background(MatchGreen.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                .border(1.dp, MatchGreen.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(kw, style = MaterialTheme.typography.bodySmall, color = MatchGreen)
                        }
                    }
                }
                if (missing.isNotEmpty()) Spacer(Modifier.height(12.dp))
            }

            if (missing.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = MissRed, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("缺失 (${missing.size})", style = MaterialTheme.typography.labelMedium, color = MissRed)
                }
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    missing.forEach { kw ->
                        Box(
                            Modifier
                                .background(MissRed.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                .border(1.dp, MissRed.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(kw, style = MaterialTheme.typography.bodySmall, color = MissRed)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionsPanel(suggestions: List<String>) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("改进建议", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            suggestions.forEachIndexed { i, s ->
                Row {
                    Text("${i + 1}.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(6.dp))
                    Text(s, style = MaterialTheme.typography.bodyMedium)
                }
                if (i < suggestions.size - 1) Spacer(Modifier.height(4.dp))
            }
        }
    }
}
