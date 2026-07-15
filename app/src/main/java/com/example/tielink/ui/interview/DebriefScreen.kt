package com.example.tielink.ui.interview

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tielink.data.local.db.entity.JdLibraryEntity
import com.example.tielink.domain.model.ResumeVersion
import com.example.tielink.ui.components.AppMetricBar
import com.example.tielink.ui.components.AppStatusPill
import com.example.tielink.ui.components.VoiceInputButton
import com.example.tielink.ui.theme.ActionBlue
import com.example.tielink.ui.theme.AppSpacing
import com.example.tielink.ui.theme.CardLight
import com.example.tielink.ui.theme.FocusCyan
import com.example.tielink.ui.theme.MatchGreen
import com.example.tielink.ui.theme.MissRed
import com.example.tielink.ui.theme.SignalMint
import com.example.tielink.ui.theme.SurfaceLight
import com.example.tielink.ui.theme.TextSecondary
import com.example.tielink.ui.theme.WarningOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebriefScreen(
    onNavigateBack: () -> Unit,
    viewModel: DebriefViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val mimeType = context.contentResolver.getType(uri)
        val name = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
        } ?: uri.lastPathSegment ?: "面试材料"
        viewModel.attachRecording(name, mimeType, uri.toString())
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("真实面试复盘") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        containerColor = SurfaceLight
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .background(
                    Brush.verticalGradient(
                        listOf(SurfaceLight, MaterialTheme.colorScheme.surfaceContainerLow)
                    )
                ),
            contentPadding = PaddingValues(AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            item {
                DebriefStatusCard(state = state)
            }
            item {
                UploadAndContextCard(
                    state = state,
                    onUpload = { filePicker.launch(arrayOf("audio/*", "video/*")) },
                    onSelectJd = viewModel::selectJd,
                    onSelectResume = viewModel::selectResume
                )
            }
            item {
                TranscriptCard(
                    transcript = state.transcript,
                    onTranscriptChange = viewModel::updateTranscript,
                    onVoiceText = viewModel::appendRecognizedText,
                    onVoiceError = { viewModel.updateTranscript(state.transcript) }
                )
            }
            item {
                Button(
                    onClick = viewModel::analyzeDebrief,
                    enabled = !state.isAnalyzing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Analytics, contentDescription = null)
                    Spacer(Modifier.width(AppSpacing.xs))
                    Text(if (state.isAnalyzing) "分析中..." else "生成真实面试复盘")
                }
            }
            state.report?.let { report ->
                item { DebriefReportCard(report) }
                item { QuestionSlicesCard(report.questions) }
                item { FindingsCard("表达复盘", report.expressionFindings, FocusCyan) }
                item { FindingsCard("内容风险", report.contentRisks, MissRed) }
                item { FindingsCard("视频/行为提醒", report.behaviorNotes, WarningOrange) }
                item { FindingsCard("下一轮训练重点", report.nextPracticeFocus, MatchGreen) }
                item { FindingsCard("联动简历优化", report.resumeSuggestions, ActionBlue) }
                item { FindingsCard("联动打招呼语", report.greetingSuggestions, SignalMint) }
            }
        }
    }
}

@Composable
private fun DebriefStatusCard(state: DebriefUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = CardLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                Icon(Icons.Default.Analytics, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f)) {
                    Text("复盘工作台", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(state.statusText, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
            if (state.isAnalyzing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun UploadAndContextCard(
    state: DebriefUiState,
    onUpload: () -> Unit,
    onSelectJd: (Long?) -> Unit,
    onSelectResume: (Long?) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = CardLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("面试材料", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = state.recordingName.ifBlank { "未选择录音/视频" },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(onClick = onUpload) {
                    Icon(Icons.Default.UploadFile, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("上传")
                }
            }
            ContextDropdown(
                title = "关联岗位",
                icon = Icons.Default.Work,
                selectedLabel = state.selectedJdLabel(),
                emptyLabel = "不关联岗位",
                items = state.jds,
                itemLabel = { it.optionLabel() },
                onSelect = { onSelectJd(it?.id) }
            )
            ContextDropdown(
                title = "关联简历",
                icon = Icons.Default.Description,
                selectedLabel = state.selectedResumeLabel(),
                emptyLabel = "不关联简历",
                items = state.resumes,
                itemLabel = { it.optionLabel() },
                onSelect = { onSelectResume(it?.id) }
            )
        }
    }
}

@Composable
private fun TranscriptCard(
    transcript: String,
    onTranscriptChange: (String) -> Unit,
    onVoiceText: (String) -> Unit,
    onVoiceError: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = CardLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("转写文本", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("可粘贴记录，也可边播放录音边用麦克风听写", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                VoiceInputButton(
                    onTextRecognized = onVoiceText,
                    onError = onVoiceError,
                    label = "听写",
                    prompt = "请播放或朗读面试录音内容"
                )
            }
            OutlinedTextField(
                value = transcript,
                onValueChange = onTranscriptChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 8,
                placeholder = { Text("粘贴真实面试转写，或点击“听写”逐段追加。") }
            )
        }
    }
}

@Composable
private fun DebriefReportCard(report: DebriefReport) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = CardLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(AppSpacing.md), verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            Text("复盘总览", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(report.summary, style = MaterialTheme.typography.bodyMedium)
            AppMetricBar("表达", report.expressionScore, color = FocusCyan)
            AppMetricBar("内容", report.contentScore, color = MatchGreen)
            AppMetricBar("行为", report.behaviorScore, color = WarningOrange)
        }
    }
}

@Composable
private fun QuestionSlicesCard(questions: List<DebriefQuestionSlice>) {
    FindingsShell(title = "问题切片", color = SignalMint) {
        if (questions.isEmpty()) {
            Text("暂未识别出明确问题。", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                questions.forEach { item ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(item.evidence, style = MaterialTheme.typography.bodySmall)
                        Text(item.suggestion, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun FindingsCard(title: String, items: List<String>, color: Color) {
    FindingsShell(title = title, color = color) {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            items.forEach {
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs), verticalAlignment = Alignment.Top) {
                    AppStatusPill(text = "", color = color, modifier = Modifier.size(14.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun FindingsShell(title: String, color: Color, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = CardLight),
        border = BorderStroke(1.dp, color.copy(alpha = 0.18f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(AppSpacing.md), verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(4.dp), color = color.copy(alpha = 0.16f)) {
                    Spacer(Modifier.size(18.dp))
                }
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            content()
        }
    }
}

@Composable
private fun <T> ContextDropdown(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selectedLabel: String,
    emptyLabel: String,
    items: List<T>,
    itemLabel: (T) -> String,
    onSelect: (T?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(modifier = Modifier.padding(AppSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(AppSpacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Text(selectedLabel, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = { expanded = true }) { Text("选择") }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text(emptyLabel) }, onClick = {
                    onSelect(null)
                    expanded = false
                })
                items.forEach { item ->
                    DropdownMenuItem(text = { Text(itemLabel(item), maxLines = 1, overflow = TextOverflow.Ellipsis) }, onClick = {
                        onSelect(item)
                        expanded = false
                    })
                }
            }
        }
    }
}

private fun DebriefUiState.selectedJdLabel(): String {
    return selectedJdId?.let { id -> jds.firstOrNull { it.id == id }?.optionLabel() } ?: "未关联岗位"
}

private fun DebriefUiState.selectedResumeLabel(): String {
    return selectedResumeId?.let { id -> resumes.firstOrNull { it.id == id }?.optionLabel() } ?: "未关联简历"
}

private fun JdLibraryEntity.optionLabel(): String {
    return listOf(companyName, positionName).filter { it.isNotBlank() }.joinToString(" · ")
        .ifBlank { rawText.take(24).ifBlank { "未命名岗位" } }
}

private fun ResumeVersion.optionLabel(): String {
    val tagText = tags.take(2).joinToString(" / ")
    return if (tagText.isBlank()) name else "$name · $tagText"
}
