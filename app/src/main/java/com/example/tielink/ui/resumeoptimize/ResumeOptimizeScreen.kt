package com.example.tielink.ui.resumeoptimize

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.tielink.ui.LocalGlobalJdViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResumeOptimizeScreen(
    onNavigateBack: () -> Unit,
    onNavigateToInterview: () -> Unit,
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

    // File upload launcher
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
                    // Version selector
                    Box {
                        OutlinedButton(onClick = viewModel::toggleVersionSelector) {
                            Text("版本 ▾")
                        }
                        DropdownMenu(
                            expanded = state.isVersionSelectorOpen,
                            onDismissRequest = { viewModel.toggleVersionSelector() }
                        ) {
                            state.versions.forEach { v ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (v.isActive) {
                                                Icon(
                                                    Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                            }
                                            Text(v.name)
                                        }
                                    },
                                    onClick = { viewModel.loadVersion(v.id) }
                                )
                            }
                            if (state.versions.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("暂无版本，保存后出现") },
                                    onClick = { },
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
            // ── JD Status Bar ──────────────────────────────
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNavigateToJdInput),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = jdState.displayLabel,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "更换 →",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Resume Input ────────────────────────────────
            Text("简历内容", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))

            // File upload button
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
                Text("已加载: $name", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = state.resumeText,
                onValueChange = viewModel::updateResumeText,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                placeholder = { Text("在此粘贴简历文本…\n\n支持：文本粘贴 / PDF上传 / DOCX上传 / 图片OCR识别") },
                maxLines = 20
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── AI Actions ─────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        onNavigateToPolish(
                            state.resumeText,
                            jdState.rawText,
                            jdState.structuredJson,
                            null,
                            state.sourceType,
                            true
                        )
                    },
                    enabled = state.resumeText.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("AI 深度润色")
                }
                OutlinedButton(
                    onClick = {
                        viewModel.analyzeMatch(jdState.rawText, jdState.structuredJson)
                    },
                    enabled = state.resumeText.isNotBlank() && jdState.isSet,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("匹配度分析")
                }
            }

            // Error
            state.error?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // ── Match Score Detail ─────────────────────────
            if (state.showMatchDetail && state.matchScore > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                MatchScoreCard(state)
            }

            // ── Polished Result ────────────────────────────
            if (state.polishedText.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("AI 润色结果", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Text(
                        text = state.polishedText,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                if (state.optimizationNote.isNotBlank()) {
                    Text(
                        text = "📝 ${state.optimizationNote}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp /*, start = 4.dp, end = 4.dp */)
                    )
                }
            }

            // ── Suggestion list ────────────────────────────
            if (state.suggestions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                state.suggestions.forEach { suggestion ->
                    Row(
                        modifier = Modifier.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text("• ", style = MaterialTheme.typography.bodySmall)
                        Text(suggestion, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // ── Bottom Actions ─────────────────────────────
            Spacer(modifier = Modifier.height(16.dp))

            if (state.resumeText.isNotBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { versionNameDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("保存版本")
                    }
                    Button(
                        onClick = onNavigateToInterview,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("用这份简历去面试 →")
                    }
                }
            }

            // ── Version Name Dialog (simplified inline) ────
            if (versionNameDialog) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("保存为新版本", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newVersionName,
                            onValueChange = { newVersionName = it },
                            placeholder = { Text("版本名称，如：技术岗主版本") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    versionNameDialog = false
                                    newVersionName = ""
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("取消") }
                            Button(
                                onClick = {
                                    viewModel.saveVersion(newVersionName)
                                    versionNameDialog = false
                                    newVersionName = ""
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("保存") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchScoreCard(state: ResumeOptimizeUiState) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "匹配度评分",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                // Score circle placeholder
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { state.matchScore / 100f },
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 4.dp
                    )
                    Text(
                        text = "${state.matchScore}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Dimension bars
            ScoreBar("关键词覆盖", state.keywordCoverage)
            ScoreBar("技能契合", state.skillFit)
            ScoreBar("经验相关", state.experienceRelevance)

            if (state.missingSkills.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "缺失技能: ${state.missingSkills.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun ScoreBar(label: String, score: Float) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(72.dp)
        )
        LinearProgressIndicator(
            progress = { score },
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .padding(end = 8.dp),
        )
        Text(
            text = "${(score * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall
        )
    }
}
