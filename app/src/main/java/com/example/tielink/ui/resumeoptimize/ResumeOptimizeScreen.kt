package com.example.tielink.ui.resumeoptimize

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.tielink.domain.model.SkillImportance
import com.example.tielink.ui.LocalGlobalJdViewModel
import com.example.tielink.ui.theme.TieLinkTheme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ResumeOptimizeScreen(
    onNavigateBack: () -> Unit,
    onNavigateToJdInput: () -> Unit,
    onNavigateToPolish: (resumeText: String, jdRawText: String, jdStructuredJson: String,
                          templatePath: String?, sourceType: String, fullPolish: Boolean) -> Unit = { _, _, _, _, _, _ -> },
    viewModel: ResumeOptimizeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val globalJdVm = LocalGlobalJdViewModel.current
    val jdState by globalJdVm.state.collectAsState()
    val context = LocalContext.current
    var versionNameDialog by remember { mutableStateOf(false) }
    var newVersionName by remember { mutableStateOf("") }
    var starInputText by remember { mutableStateOf("") }
    var showStarInputDialog by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val mimeType = context.contentResolver.getType(it)
            var fileName = "文件"
            context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) {
                    fileName = cursor.getString(nameIndex)
                }
            }
            viewModel.processFile(context, it, mimeType, fileName)
        }
    }

    // STAR result dialog
    if (state.showStarResult && state.starResult != null) {
        StarResultDialog(
            result = state.starResult!!,
            onApply = { viewModel.applyStarResult() },
            onDismiss = { viewModel.dismissStarResult() }
        )
    }

    // STAR input dialog
    if (showStarInputDialog) {
        StarInputDialog(
            initialText = state.polishedText.ifBlank { state.resumeText }.take(500),
            onConfirm = { text ->
                showStarInputDialog = false
                starInputText = ""
                viewModel.formatAsStar(text)
            },
            onDismiss = { showStarInputDialog = false }
        )
    }

    // Version compare dialog
    if (state.showVersionCompare && state.compareVersion != null) {
        VersionCompareDialog(
            currentText = state.polishedText.ifBlank { state.resumeText },
            compareVersion = state.compareVersion!!,
            onDismiss = { viewModel.dismissVersionCompare() }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("简历优化") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    Box {
                        OutlinedButton(onClick = viewModel::toggleVersionSelector) {
                            Text(state.currentVersion?.name?.take(8) ?: "版本 ▾")
                        }
                        DropdownMenu(
                            expanded = state.isVersionSelectorOpen,
                            onDismissRequest = { viewModel.toggleVersionSelector() }
                        ) {
                            state.versions.forEach { v ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (v.isActive) {
                                                    Icon(
                                                        Icons.Default.CheckCircle,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(14.dp),
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                    Spacer(Modifier.width(4.dp))
                                                }
                                                Text(v.name, style = MaterialTheme.typography.bodyMedium)
                                            }
                                            if (v.tags.isNotEmpty()) {
                                                Text(
                                                    v.tags.joinToString(" · "),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    },
                                    trailingIcon = {
                                        if (v.id != (state.currentVersion?.id ?: -1L)) {
                                            TextButton(onClick = { viewModel.showVersionCompare(v) }) {
                                                Text("对比", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    },
                                    onClick = { viewModel.loadVersion(v.id) }
                                )
                            }
                            if (state.versions.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("暂无版本，保存后出现", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    onClick = {},
                                    enabled = false
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // ── JD Status Bar ──────────────────────────────────
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNavigateToJdInput),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = jdState.displayLabel,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Text("更换 →", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Resume Input ───────────────────────────────────
            Text("简历内容", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { filePickerLauncher.launch(arrayOf(
                        "application/pdf",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    )) },
                    enabled = !state.isFileProcessing,
                    modifier = Modifier.weight(1f)
                ) {
                    if (state.isFileProcessing) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("解析中...")
                    } else {
                        Icon(Icons.Default.PictureAsPdf, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("上传简历文件")
                    }
                }
                if (state.resumeText.isNotBlank()) {
                    OutlinedButton(onClick = viewModel::clearResume) { Text("清空") }
                }
            }
            state.fileName?.let { name ->
                Spacer(Modifier.height(4.dp))
                Text("已保存原文件: $name", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }

            Spacer(Modifier.height(8.dp))

            if (state.originalFilePath.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "原始排版已保留",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "当前不会把提取文字拆进个人信息。AI 润色后才会转换为 Vibe HTML 简历。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onClick = { viewModel.openOriginalFile(context) }) {
                            Text("查看原文件")
                        }
                    }
                }
            } else {
                OutlinedTextField(
                    value = state.resumeText,
                    onValueChange = viewModel::updateResumeText,
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    placeholder = { Text("在此粘贴简历文本…\n\n支持：文本粘贴 / PDF上传 / DOCX上传 / 图片OCR识别") },
                    maxLines = 20
                )
            }

            Spacer(Modifier.height(8.dp))

            // ── AI Actions Row ────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        viewModel.prepareForPolish(context) { resumeText ->
                            onNavigateToPolish(
                                resumeText,
                                jdState.rawText,
                                jdState.structuredJson,
                                state.originalFilePath.ifBlank { null },
                                state.sourceType,
                                true
                            )
                        }
                    },
                    enabled = (state.resumeText.isNotBlank() || state.originalFilePath.isNotBlank()) &&
                        !state.isFileProcessing,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.AutoAwesome, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("AI 深度润色")
                }
                OutlinedButton(
                    onClick = { viewModel.analyzeMatch(jdState.rawText, jdState.structuredJson) },
                    enabled = state.resumeText.isNotBlank() &&
                        state.originalFilePath.isBlank() &&
                        jdState.isSet,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("匹配度分析")
                }
            }

            // ── AI Tools Row (Sprint 2.3 & 2.4) ──────────────
            if (state.resumeText.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.detectAndSuggestQuantification() },
                        enabled = !state.isQuantifying,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (state.isQuantifying) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(4.dp))
                            Text("分析中...", style = MaterialTheme.typography.labelSmall)
                        } else {
                            Icon(Icons.AutoMirrored.Filled.TrendingUp, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("量化助手", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    OutlinedButton(
                        onClick = { showStarInputDialog = true },
                        enabled = !state.isStarFormatting,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (state.isStarFormatting) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(4.dp))
                            Text("格式化中...", style = MaterialTheme.typography.labelSmall)
                        } else {
                            Icon(Icons.Default.FormatListNumbered, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("STAR格式化", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // ── Error ─────────────────────────────────────────
            state.error?.let { error ->
                Spacer(Modifier.height(8.dp))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // ── Match Score Detail (Sprint 2.1) ───────────────
            if (state.showMatchDetail && state.matchScore > 0) {
                Spacer(Modifier.height(12.dp))
                MatchScoreCard(state, viewModel)
            }

            // ── Skill Gap List (Sprint 2.2) ───────────────────
            if (state.showSkillGaps && state.skillGaps.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                SkillGapCard(state, viewModel)
            }

            // ── Quantify Suggestions (Sprint 2.3) ────────────
            if (state.showQuantifySuggestions && state.quantifySuggestions.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                QuantifySuggestionsCard(state, viewModel)
            }

            // ── Polished Result ───────────────────────────────
            if (state.polishedText.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text("AI 润色结果", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Text(state.polishedText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
                }
                if (state.optimizationNote.isNotBlank()) {
                    Text(
                        text = "📝 ${state.optimizationNote}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // ── Suggestions ───────────────────────────────────
            if (state.suggestions.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                state.suggestions.forEach { suggestion ->
                    Row(modifier = Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
                        Text("• ", style = MaterialTheme.typography.bodySmall)
                        Text(suggestion, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Bottom Actions ────────────────────────────────
            if (state.resumeText.isNotBlank()) {
                OutlinedButton(
                    onClick = { versionNameDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("保存版本") }
            }

            // ── Save Version Dialog (Sprint 2.5) ──────────────
            if (versionNameDialog) {
                Spacer(Modifier.height(8.dp))
                SaveVersionCard(
                    versionName = newVersionName,
                    selectedTags = state.selectedTags,
                    onNameChange = { newVersionName = it },
                    onTagToggle = { viewModel.toggleSelectedTag(it) },
                    onSave = {
                        viewModel.saveVersion(newVersionName, state.selectedTags)
                        viewModel.clearSelectedTags()
                        versionNameDialog = false
                        newVersionName = ""
                    },
                    onCancel = {
                        viewModel.clearSelectedTags()
                        versionNameDialog = false
                        newVersionName = ""
                    }
                )
            }
        }
    }
}

// ── Sprint 2.1: Match Score Card with 4 dimensions ────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MatchScoreCard(state: ResumeOptimizeUiState, viewModel: ResumeOptimizeViewModel) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("匹配度评分", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { state.matchScore / 100f },
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 5.dp,
                        color = when {
                            state.matchScore >= 80 -> MaterialTheme.colorScheme.primary
                            state.matchScore >= 50 -> Color(0xFFF59E0B)
                            else -> MaterialTheme.colorScheme.error
                        }
                    )
                    Text(
                        text = "${state.matchScore}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            ScoreBar("关键词覆盖", state.keywordCoverage)
            ScoreBar("技能契合", state.skillFit)
            ScoreBar("经验相关", state.experienceRelevance)
            ScoreBar("学历匹配", state.educationMatch)

            if (state.suggestions.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(6.dp))
                Text("优化建议", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                state.suggestions.take(3).forEach { s ->
                    Row(Modifier.padding(vertical = 1.dp), verticalAlignment = Alignment.Top) {
                        Text("• ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        Text(s, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreBar(label: String, score: Float) {
    val color = when {
        score >= 0.8f -> MaterialTheme.colorScheme.primary
        score >= 0.5f -> Color(0xFFF59E0B)
        else -> MaterialTheme.colorScheme.error
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(72.dp))
        LinearProgressIndicator(
            progress = { score.coerceIn(0f, 1f) },
            modifier = Modifier.weight(1f).height(6.dp).padding(end = 8.dp),
            color = color
        )
        Text("${(score * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(32.dp))
    }
}

// ─── Previews ──────────────────────────────────────────────────────────────

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ScoreBarPreview() {
    TieLinkTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            ScoreBar("关键词覆盖", 0.85f)
            ScoreBar("技能契合", 0.60f)
            ScoreBar("经验相关", 0.45f)
            ScoreBar("学历匹配", 0.90f)
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun SkillGapChipPreview() {
    TieLinkTheme {
        // SkillGapChip requires viewModel in the actual impl — show static representation
        Column(modifier = Modifier.padding(8.dp)) {
            AssistChip(
                onClick = {},
                label = { Text("Kotlin Coroutines", style = MaterialTheme.typography.labelSmall) },
                leadingIcon = { Icon(Icons.Default.Add, "添加", Modifier.size(14.dp)) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ResumeOptimizeScreenPreview() {
    // Preview of static layout — hiltViewModel() unavailable in preview
    TieLinkTheme {
        ResumeOptimizeScreen(
            onNavigateBack = {},
            onNavigateToJdInput = {}
        )
    }
}

// ── Sprint 2.2: Skill Gap Card ─────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SkillGapCard(state: ResumeOptimizeUiState, viewModel: ResumeOptimizeViewModel) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("缺失技能 (${state.skillGaps.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { viewModel.toggleSkillGaps() }) {
                    Text("收起", style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(6.dp))

            // Group by importance
            val required = state.skillGaps.filter { it.importance == SkillImportance.REQUIRED }
            val preferred = state.skillGaps.filter { it.importance == SkillImportance.PREFERRED }
            val normal = state.skillGaps.filter { it.importance == SkillImportance.NORMAL }

            if (required.isNotEmpty()) {
                Text("必须掌握", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    required.forEach { gap -> SkillGapChip(gap, viewModel, chipColor = MaterialTheme.colorScheme.errorContainer) }
                }
                Spacer(Modifier.height(6.dp))
            }
            if (preferred.isNotEmpty()) {
                Text("优先考虑", style = MaterialTheme.typography.labelSmall, color = Color(0xFFF59E0B), fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    preferred.forEach { gap -> SkillGapChip(gap, viewModel, chipColor = Color(0xFFFEF3C7)) }
                }
                Spacer(Modifier.height(6.dp))
            }
            if (normal.isNotEmpty()) {
                Text("加分项", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    normal.take(8).forEach { gap -> SkillGapChip(gap, viewModel) }
                }
            }
        }
    }
}

@Composable
private fun SkillGapChip(gap: com.example.tielink.domain.model.SkillGap, viewModel: ResumeOptimizeViewModel, chipColor: Color = Color.Transparent) {
    AssistChip(
        onClick = { viewModel.addSkillToResume(gap.skill) },
        label = { Text(gap.skill, style = MaterialTheme.typography.labelSmall) },
        leadingIcon = { Icon(Icons.Default.Add, contentDescription = "添加到简历", modifier = Modifier.size(14.dp)) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (chipColor == Color.Transparent) MaterialTheme.colorScheme.surface else chipColor
        )
    )
}

// ── Sprint 2.3: Quantify Suggestions Card ─────────────────────────────────

@Composable
private fun QuantifySuggestionsCard(state: ResumeOptimizeUiState, viewModel: ResumeOptimizeViewModel) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("量化建议 (${state.quantifySuggestions.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { viewModel.dismissAllQuantifySuggestions() }) {
                    Text("全部忽略", style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(6.dp))

            state.quantifySuggestions.forEach { suggestion ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = suggestion.original,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textDecoration = TextDecoration.LineThrough,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = suggestion.quantified,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = { viewModel.dismissQuantifySuggestion(suggestion) },
                                modifier = Modifier.weight(1f)
                            ) { Text("忽略", style = MaterialTheme.typography.labelSmall) }
                            Button(
                                onClick = { viewModel.applyQuantifySuggestion(suggestion) },
                                modifier = Modifier.weight(1f)
                            ) { Text("采用", style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
            }
        }
    }
}

// ── Sprint 2.4: STAR Input / Result Dialogs ────────────────────────────────

@Composable
private fun StarInputDialog(
    initialText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialText) }
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("STAR 格式化", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("粘贴或编辑要格式化的经历描述", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    placeholder = { Text("粘贴工作经历或项目描述...") }
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
                    Button(
                        onClick = { onConfirm(text) },
                        enabled = text.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) { Text("格式化") }
                }
            }
        }
    }
}

@Composable
private fun StarResultDialog(
    result: com.example.tielink.domain.usecase.StarResult,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("STAR 格式化结果", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "关闭") }
                }
                Spacer(Modifier.height(8.dp))

                listOf(
                    "背景 (Situation)" to result.situation,
                    "任务 (Task)" to result.task,
                    "行动 (Action)" to result.action,
                    "结果 (Result)" to result.result
                ).filter { (_, v) -> v.isNotBlank() }.forEach { (label, value) ->
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(2.dp))
                    Text(value, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                }

                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("放弃") }
                    Button(onClick = onApply, modifier = Modifier.weight(1f)) { Text("追加到简历") }
                }
            }
        }
    }
}

// ── Sprint 2.5: Save Version Card with Tags ────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SaveVersionCard(
    versionName: String,
    selectedTags: List<String>,
    onNameChange: (String) -> Unit,
    onTagToggle: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Card(shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("保存为新版本", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = versionName,
                onValueChange = onNameChange,
                placeholder = { Text("版本名称，如：技术岗主版本") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            Text("添加标签（可多选）", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                VERSION_TAG_PRESETS.forEach { tag ->
                    FilterChip(
                        selected = selectedTags.contains(tag),
                        onClick = { onTagToggle(tag) },
                        label = { Text(tag, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("取消") }
                Button(onClick = onSave, modifier = Modifier.weight(1f)) { Text("保存") }
            }
        }
    }
}

// ── Sprint 2.5: Version Compare Dialog ────────────────────────────────────

@Composable
private fun VersionCompareDialog(
    currentText: String,
    compareVersion: com.example.tielink.domain.model.ResumeVersion,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("版本对比", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
                }
                Spacer(Modifier.height(4.dp))
                Text("${compareVersion.name}  vs  当前编辑", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(compareVersion.name, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(4.dp))
                        Text(compareVersion.rawText, style = MaterialTheme.typography.bodySmall, maxLines = 80)
                    }
                    VerticalDivider()
                    Column(
                        modifier = Modifier.weight(1f).fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text("当前编辑", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(4.dp))
                        Text(currentText, style = MaterialTheme.typography.bodySmall, maxLines = 80)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("关闭") }
            }
        }
    }
}
