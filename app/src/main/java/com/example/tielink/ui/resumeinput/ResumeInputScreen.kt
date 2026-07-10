package com.example.tielink.ui.resumeinput

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tielink.data.local.db.entity.HistoryEntity
import com.example.tielink.domain.model.MatchLevel
import com.example.tielink.ui.components.ErrorBanner
import com.example.tielink.ui.components.ScoreRingChart
import com.example.tielink.ui.components.SectionCard
import com.example.tielink.ui.theme.TieLinkTheme

private val BrandBlue = Color(0xFF2563EB)
private val SuccessGreen = BrandBlue
private val DangerRed = Color(0xFFDC2626)
private val TextPrimary = Color(0xFF111827)
private val TextSecondary = Color(0xFF475569)
private val TextTertiary = Color(0xFF94A3B8)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResumeInputScreen(
    onNavigateBack: () -> Unit,
    onResumeSubmitted: (resumeText: String, jdRawText: String, jdStructuredJson: String,
                         templatePath: String?, sourceType: String, fullPolish: Boolean) -> Unit,
    flowMode: String = "legacy",  // "legacy" | "jd_optimize"
    viewModel: ResumeInputViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

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

    // ── Dialogs ───────────────────────────────────────────
    if (state.showHistoryPicker) {
        HistoryPickerDialog(
            items = state.historyItems,
            onSelect = { viewModel.selectHistoryVersion(it) },
            onDismiss = { viewModel.dismissHistoryPicker() }
        )
    }

    if (state.showMatchDialog) {
        MatchConfirmDialog(
            score = state.matchScore,
            matchedKeywords = state.matchedKeywords,
            missingKeywords = state.missingKeywords,
            suggestions = state.matchSuggestions,
            onConfirm = {
                viewModel.confirmPolish { resumeText, jdRawText, jdJson, tp, st, fp ->
                    onResumeSubmitted(resumeText, jdRawText, jdJson, tp, st, fp)
                }
            },
            onDismiss = { viewModel.dismissMatchDialog() }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (flowMode == "jd_optimize") "JD 优化 — 简历输入" else "简历输入") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (flowMode != "jd_optimize") {
                Text(text = "第 2 步：输入你的简历", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(4.dp))
            }
            Text(
                text = "粘贴简历文本，或上传 PDF / DOCX 文件自动提取",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Show JD summary
            state.jdStructured?.let { jd ->
                Spacer(modifier = Modifier.height(12.dp))
                SectionCard(title = "目标岗位: ${jd.jobTitle}") {
                    Text(
                        text = jd.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── File upload + History picker row ─────────
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        Text("上传文件")
                    }
                }

                if (state.resumeText.isNotBlank()) {
                    OutlinedButton(onClick = viewModel::clearResume) {
                        Text("清空")
                    }
                }
            }

            // ── JD优化: 历史版本选择 ──────────────────────
            if (flowMode == "jd_optimize") {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { viewModel.loadHistoryVersions() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.History, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("从历史润色版本选择")
                }
            }

            // Show file name if loaded
            state.fileName?.let { name ->
                Spacer(modifier = Modifier.height(4.dp))
                Text("已加载: $name", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Text input ──────────────────────────────────
            OutlinedTextField(
                value = state.resumeText,
                onValueChange = viewModel::updateResumeText,
                modifier = Modifier.fillMaxWidth().height(320.dp),
                placeholder = {
                    Text(
                        "在此粘贴你的简历...\n\n" +
                                "建议包含以下内容：\n" +
                                "• 个人信息（姓名、联系方式）\n" +
                                "• 工作经历（公司、职位、时间、职责、成果）\n" +
                                "• 项目经验（项目描述、你的角色、技术栈）\n" +
                                "• 技能清单\n" +
                                "• 教育背景\n\n" +
                                "或点击「上传文件」选择 PDF / DOCX"
                    )
                },
                maxLines = 25
            )

            Spacer(modifier = Modifier.height(8.dp))

            state.error?.let { error ->
                ErrorBanner(message = error)
                Spacer(modifier = Modifier.height(8.dp))
            }

            // ── Polish mode toggle ─────────────────────────
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Switch(
                    checked = state.fullPolish,
                    onCheckedChange = { viewModel.togglePolishMode() }
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (state.fullPolish) "全篇优化：根据JD深度改写简历" else "部分优化：仅调整关键词和措辞",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Submit button ──────────────────────────────
            Button(
                onClick = {
                    if (flowMode == "jd_optimize") {
                        viewModel.analyzeAndPrompt()
                    } else {
                        viewModel.submitResume { resumeText, jdRawText, jdJson, tp, st, fp ->
                            onResumeSubmitted(resumeText, jdRawText, jdJson, tp, st, fp)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.resumeText.isNotBlank() && !state.isAnalyzing
            ) {
                if (state.isAnalyzing) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("正在分析匹配度...")
                } else {
                    Text(if (flowMode == "jd_optimize") "开始匹配打分 →" else "开始润色 →")
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  历史版本选择弹窗
// ═══════════════════════════════════════════════════════════════

@Composable
private fun HistoryPickerDialog(
    items: List<HistoryEntity>,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("选择历史润色版本", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "关闭") }
                }
                Spacer(Modifier.height(12.dp))

                if (items.isEmpty()) {
                    Text("暂无历史润色记录", color = TextTertiary, modifier = Modifier.padding(vertical = 24.dp))
                } else {
                    LazyColumn(Modifier.height(320.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(items) { entity ->
                            HistoryItemRow(entity) { onSelect(entity.id) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryItemRow(entity: HistoryEntity, onClick: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFF8FAFC)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(entity.jdTitle, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                Spacer(Modifier.height(2.dp))
                Row {
                    Text("匹配 ${entity.matchScore}分", fontSize = 12.sp, color = if (entity.matchScore >= 70) SuccessGreen else TextSecondary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(entity.createdAt)),
                        fontSize = 12.sp, color = TextTertiary
                    )
                }
            }
            Icon(Icons.Default.CheckCircle, null, Modifier.size(20.dp), tint = BrandBlue)
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  匹配确认弹窗
// ═══════════════════════════════════════════════════════════════

@Composable
private fun MatchConfirmDialog(
    score: Int,
    matchedKeywords: List<String>,
    missingKeywords: List<String>,
    suggestions: List<String>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val level = when {
        score >= 80 -> MatchLevel.HIGH
        score >= 50 -> MatchLevel.MEDIUM
        else -> MatchLevel.LOW
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                // Title
                Text("匹配度分析", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text("基于 JD 与简历内容的智能匹配", fontSize = 13.sp, color = TextTertiary)

                Spacer(Modifier.height(20.dp))

                // Score ring
                ScoreRingChart(score = score, level = level, size = 80.dp)
                Spacer(Modifier.height(8.dp))
                Text(
                    when (level) {
                        MatchLevel.HIGH -> "匹配度较高 🔥"
                        MatchLevel.MEDIUM -> "匹配度一般 👍"
                        MatchLevel.LOW -> "匹配度偏低 💡"
                    },
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    color = when (level) {
                        MatchLevel.HIGH -> SuccessGreen
                        MatchLevel.MEDIUM -> Color(0xFFF59E0B)
                        MatchLevel.LOW -> DangerRed
                    }
                )

                Spacer(Modifier.height(16.dp))

                // Key stats
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatItem("已匹配", matchedKeywords.size.toString(), SuccessGreen)
                    StatItem("缺失", missingKeywords.size.toString(), if (missingKeywords.isEmpty()) SuccessGreen else DangerRed)
                }

                Spacer(Modifier.height(12.dp))

                // Missing keywords
                if (missingKeywords.isNotEmpty()) {
                    Text("JD 要求但简历中缺失:", fontSize = 11.sp, color = DangerRed, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(4.dp))
                    Text(
                        missingKeywords.joinToString(" · "),
                        fontSize = 12.sp, color = TextSecondary, lineHeight = 18.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    "是否对该简历进行 AI 深度优化？",
                    fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "AI 将根据 JD 要求自动改写简历内容，提升匹配度。",
                    fontSize = 12.sp, color = TextTertiary
                )

                Spacer(Modifier.height(16.dp))

                // Buttons
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("开始优化 →", fontSize = 15.sp)
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("暂不优化，稍后再试", color = TextTertiary)
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 11.sp, color = TextTertiary)
    }
}

// ─── Previews ──────────────────────────────────────────────────────────────

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun HistoryItemRowPreview() {
    TieLinkTheme {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HistoryItemRow(
                entity = HistoryEntity(
                    id = 1,
                    createdAt = System.currentTimeMillis(),
                    jdRawText = "岗位描述...",
                    jdTitle = "Android开发工程师 — 字节跳动",
                    originalResume = "原始简历...",
                    polishedResume = "优化后简历...",
                    jdSkills = "Kotlin,Jetpack Compose",
                    matchNote = "优化项目经历描述",
                    matchScore = 85,
                    matchedKeywords = "[\"Kotlin\",\"Android\"]",
                    missingKeywords = "[\"协程\"]",
                    suggestions = "[\"补充协程经验\"]"
                ),
                onClick = {}
            )
            HistoryItemRow(
                entity = HistoryEntity(
                    id = 2,
                    createdAt = System.currentTimeMillis() - 86400000,
                    jdRawText = "...",
                    jdTitle = "iOS开发工程师 — 腾讯",
                    originalResume = "...",
                    polishedResume = "...",
                    jdSkills = "Swift,SwiftUI",
                    matchNote = "",
                    matchScore = 55,
                    matchedKeywords = "[\"Swift\"]",
                    missingKeywords = "[\"SwiftUI\",\"Combine\"]",
                    suggestions = "[\"学习SwiftUI\"]"
                ),
                onClick = {}
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun MatchConfirmDialogPreview() {
    TieLinkTheme {
        MatchConfirmDialog(
            score = 72,
            matchedKeywords = listOf("Kotlin", "Android", "MVVM", "Git", "Gradle"),
            missingKeywords = listOf("Jetpack Compose", "协程", "性能优化"),
            suggestions = listOf("补充Compose经验", "展示协程项目"),
            onConfirm = {},
            onDismiss = {}
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ResumeInputScreenPreview() {
    // Preview of static layout — hiltViewModel() unavailable in preview
    TieLinkTheme {
        ResumeInputScreen(
            onNavigateBack = {},
            onResumeSubmitted = { _, _, _, _, _, _ -> }
        )
    }
}
