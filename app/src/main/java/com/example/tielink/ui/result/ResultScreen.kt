package com.example.tielink.ui.result

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tielink.domain.model.ResumeData
import com.example.tielink.ui.components.ResumePreviewWebView
import com.example.tielink.ui.components.ScoreRingChart
import com.example.tielink.ui.theme.TieLinkTheme
import com.example.tielink.ui.theme.appMotionTween
import com.example.tielink.ui.theme.motionCollapseOut
import com.example.tielink.ui.theme.motionExpandIn
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

// ── Brand colors ────────────────────────────────────────────
private val BrandBlue = Color(0xFF2563EB)
private val BrandBlueLight = Color(0xFFEFF6FF)
private val SuccessGreen = BrandBlue
private val DangerRed = Color(0xFFDC2626)
private val TextPrimary = Color(0xFF111827)
private val TextSecondary = Color(0xFF475569)
private val TextTertiary = Color(0xFF94A3B8)
private val BorderLight = Color(0xFFE2E8F0)
private val BgWhite = Color(0xFFFFFFFF)
private val BgSurface = Color(0xFFF8FAFC)

// ═══════════════════════════════════════════════════════════════
//  MAIN SCREEN
// ═══════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    onNavigateBack: () -> Unit,
    onNavigateToHistory: () -> Unit,
    viewModel: ResultViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Full-screen preview dialog
    var showFullScreenPreview by rememberSaveable { mutableStateOf(false) }
    if (showFullScreenPreview && state.resumeData != null) {
        Dialog(
            onDismissRequest = { showFullScreenPreview = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = BgWhite) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().background(BrandBlueLight).padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("简历全屏预览", modifier = Modifier.padding(start = 8.dp), fontWeight = FontWeight.Medium)
                        IconButton(onClick = { showFullScreenPreview = false }) {
                            Icon(Icons.Default.Close, "关闭")
                        }
                    }
                    ResumePreviewWebView(
                        resumeData = state.resumeData!!,
                        useVibeTemplate = state.useVibeTemplate,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
                topBar = {
                    TopAppBar(
                    title = { Text("简历作品台", fontWeight = FontWeight.Medium) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回聊天")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = viewModel::exportPdf,
                            enabled = !state.isExporting && !state.isOriginalFile
                        ) {
                            if (state.isExporting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.Share, "导出PDF")
                        }
                        IconButton(onClick = onNavigateToHistory) {
                            Icon(Icons.Default.History, "历史")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = BgWhite)
                )
            },
            containerColor = BgWhite
        ) { padding ->
            when {
                state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.error != null -> Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                    com.example.tielink.ui.components.ErrorBanner(message = state.error!!)
                }
                else -> {
                    // ── UI-local sidebar state ─────────────────
                    var sidebarExpanded by rememberSaveable { mutableStateOf(false) }
                    var activeSidebarTab by rememberSaveable { mutableStateOf("ai") }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) {
                    if (!state.isOriginalFile) {
                        Card(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1220))
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = Color.White.copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            "VIBE RESUME",
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                            color = Color(0xFFBFDBFE),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.sp
                                        )
                                    }
                                    Spacer(Modifier.weight(1f))
                                    if (state.matchScore > 0) {
                                        Text(
                                            "${state.matchScore}% 匹配",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    state.jdTitle.ifBlank { "你的 Vibe 简历" },
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "结构化完成 · 可编辑 · 可导出 PDF",
                                    color = Color.White.copy(alpha = 0.68f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Spacer(Modifier.height(14.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { showFullScreenPreview = true },
                                        enabled = state.resumeData != null,
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFDBEAFE),
                                            contentColor = Color(0xFF0B1220)
                                        )
                                    ) {
                                        Icon(Icons.Default.Fullscreen, null, Modifier.size(17.dp))
                                        Spacer(Modifier.width(5.dp))
                                        Text("全屏看作品")
                                    }
                                    OutlinedButton(
                                        onClick = onNavigateBack,
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
                                    ) {
                                        Text("继续问 Agent")
                                    }
                                }
                            }
                        }
                        state.resumeData?.let { data ->
                            ResumeImpactStrip(
                                data = data,
                                polishedText = state.polishedResume,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                    }

                    if (state.isOriginalFile) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                            )
                        ) {
                            Column(Modifier.padding(20.dp)) {
                                Text(
                                    state.jdTitle,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "原始文件已完整保留",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = BrandBlue,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "当前未对 PDF/DOCX 的内容和排版做任何重组，也不会把全文放进个人信息。你可以直接查看原文件，或主动选择 AI 润色后生成 Vibe HTML 预览。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                                Spacer(Modifier.height(18.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedButton(
                                        onClick = viewModel::openOriginalFile,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("查看原文件")
                                    }
                                    Button(
                                        onClick = viewModel::polishOriginalResume,
                                        enabled = !state.isIterativePolishing,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        if (state.isIterativePolishing) {
                                            CircularProgressIndicator(
                                                Modifier.size(18.dp),
                                                strokeWidth = 2.dp,
                                                color = Color.White
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text("润色中...")
                                        } else {
                                            Icon(Icons.Default.AutoAwesome, null, Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("AI 润色")
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                    Row(Modifier.fillMaxWidth().weight(1f)) {
                        // ── Left Smart Sidebar ────────────────
                        SmartSidebar(
                            expanded = sidebarExpanded,
                            activeTab = activeSidebarTab,
                            onToggleExpanded = { sidebarExpanded = !sidebarExpanded },
                            onSelectTab = { tab ->
                                if (activeSidebarTab == tab && sidebarExpanded) {
                                    sidebarExpanded = false
                                } else {
                                    activeSidebarTab = tab
                                    sidebarExpanded = true
                                }
                            },
                            matchScore = state.matchScore,
                            matchLevel = state.matchLevel,
                            matchedKeywords = state.matchedKeywords,
                            missingKeywords = state.missingKeywords,
                            suggestions = state.suggestions,
                            iterativeHistory = state.iterativeHistory,
                            jdTitle = state.jdTitle
                        )

                        // ── Main content: 内容编辑 ────────────
                        ContentEditTab(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            resumeData = state.resumeData,
                            originalText = state.originalResume,
                            polishedText = state.polishedResume,
                            useVibeTemplate = state.useVibeTemplate,
                            expandedSection = state.expandedSection,
                            onToggleSection = { viewModel.setExpandedSection(it) },
                            onToggleVibe = viewModel::toggleVibeTemplate,
                            onFullScreen = { showFullScreenPreview = true },
                            onUpdatePersonalInfo = viewModel::updatePersonalInfo,
                            onUpdateExperience = viewModel::updateExperience,
                            onAddExperience = viewModel::addExperience,
                            onRemoveExperience = viewModel::removeExperience,
                            onUpdateEducation = viewModel::updateEducation,
                            onAddEducation = viewModel::addEducation,
                            onRemoveEducation = viewModel::removeEducation,
                            onUpdateProject = viewModel::updateProject,
                            onAddProject = viewModel::addProject,
                            onRemoveProject = viewModel::removeProject,
                            onUpdateSkills = viewModel::updateSkills,
                            onAddSkill = viewModel::addSkill,
                            onRemoveSkill = viewModel::removeSkill
                        )
                    }
                    }
                    }
                }
            }
        }

        // ── Full-screen celebration OVERLAY (above Scaffold/TopAppBar) ──
        CompletionCelebration(
            visible = state.showCompletionAnimation,
            onDismiss = { viewModel.dismissCompletionAnimation() }
        )
    }
}

@Composable
private fun ResumeImpactStrip(
    data: ResumeData,
    polishedText: String,
    modifier: Modifier = Modifier
) {
    val sectionCount = listOf(
        data.summary.isNotBlank(),
        data.experiences.isNotEmpty(),
        data.education.isNotEmpty(),
        data.projects.isNotEmpty(),
        data.skills.isNotEmpty()
    ).count { it }
    val quantifiedCount = remember(polishedText) {
        Regex("""\d+(?:\.\d+)?\s*(?:%|倍|万|千|人|项|个|次|天|月|年)""")
            .findAll(polishedText)
            .map { it.value }
            .distinct()
            .count()
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ResumeImpactMetric(
            modifier = Modifier.weight(1f),
            value = "$sectionCount/5",
            label = "结构板块",
            accent = BrandBlue
        )
        ResumeImpactMetric(
            modifier = Modifier.weight(1f),
            value = "${quantifiedCount}处",
            label = "量化表达",
            accent = Color(0xFF1D4ED8)
        )
        ResumeImpactMetric(
            modifier = Modifier.weight(1f),
            value = "${data.skills.size}项",
            label = "技能关键词",
            accent = Color(0xFF0B1220)
        )
    }
}

@Composable
private fun ResumeImpactMetric(
    modifier: Modifier,
    value: String,
    label: String,
    accent: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = accent.copy(alpha = 0.08f)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                value,
                color = accent,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp
            )
            Text(
                label,
                color = TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  SMART SIDEBAR (左侧可折叠栏)
// ═══════════════════════════════════════════════════════════════

@Composable
private fun SmartSidebar(
    expanded: Boolean,
    activeTab: String,
    onToggleExpanded: () -> Unit,
    onSelectTab: (String) -> Unit,
    matchScore: Int,
    matchLevel: com.example.tielink.domain.model.MatchLevel,
    matchedKeywords: List<String>,
    missingKeywords: List<String>,
    suggestions: List<String>,
    iterativeHistory: List<String>,
    jdTitle: String
) {
    Column(
        Modifier
            .fillMaxHeight()
            .background(BgSurface)
            .border(1.dp, BorderLight)
            .animateContentSize()
            .widthIn(min = 44.dp, max = if (expanded) 260.dp else 44.dp)
    ) {
        if (expanded) {
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        when (activeTab) {
                            "ai" -> "AI 建议"
                            "keywords" -> "关键词"
                            "history" -> "历史版本"
                            else -> "设置"
                        },
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                    IconButton(onClick = onToggleExpanded, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, "收起", Modifier.size(16.dp))
                    }
                }
                androidx.compose.material3.HorizontalDivider(color = BorderLight)

                when (activeTab) {
                    "ai" -> AiSuggestionsPanel(suggestions, matchedKeywords, missingKeywords, jdTitle)
                    "keywords" -> KeywordsDetailPanel(matchScore, matchLevel, matchedKeywords, missingKeywords)
                    "history" -> HistoryPanel(iterativeHistory)
                }
            }
        } else {
            Column(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SidebarIcon(Icons.Default.AutoAwesome, "AI", activeTab == "ai") { onSelectTab("ai") }
                SidebarIcon(Icons.Default.Fullscreen, "关键词", activeTab == "keywords") { onSelectTab("keywords") }
                SidebarIcon(Icons.Default.History, "历史", activeTab == "history") { onSelectTab("history") }
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SidebarIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        Modifier
            .width(36.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) BrandBlue.copy(alpha = 0.1f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, label, Modifier.size(18.dp), tint = if (selected) BrandBlue else TextTertiary)
    }
}

// ─── Previews ──────────────────────────────────────────────────────────────

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun SidebarIconPreview() {
    TieLinkTheme {
        Column(
            Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SidebarIcon(Icons.Default.AutoAwesome, "AI", selected = true) {}
            SidebarIcon(Icons.Default.Fullscreen, "关键词", selected = false) {}
            SidebarIcon(Icons.Default.History, "历史", selected = false) {}
            SidebarIcon(Icons.Default.AutoAwesome, "AI助手", selected = false) {}
        }
    }
}
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ExpandableSectionCardPreview() {
    TieLinkTheme {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ExpandableSectionCard(
                icon = "👤",
                title = "个人信息",
                summary = "张三 · Android开发工程师",
                isExpanded = false,
                onToggle = {}
            ) {
                Text("展开后的内容区域")
            }
            ExpandableSectionCard(
                icon = "💼",
                title = "实习工作经历",
                summary = "2项 · 字节跳动, 腾讯",
                isExpanded = true,
                onToggle = {}
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = BgWhite)) {
                        Column(Modifier.padding(10.dp)) {
                            Text("字节跳动", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Android开发实习生 · 2023.06 - 2024.03", fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = BgWhite)) {
                        Column(Modifier.padding(10.dp)) {
                            Text("腾讯", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("客户端开发实习生 · 2022.07 - 2023.05", fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ResultScreenPreview() {
    // Preview of static layout — hiltViewModel() unavailable in preview
    TieLinkTheme {
        ResultScreen(onNavigateBack = {}, onNavigateToHistory = {})
    }
}

@Composable
private fun AiSuggestionsPanel(
    suggestions: List<String>,
    matched: List<String>,
    missing: List<String>,
    jdTitle: String
) {
    Column(Modifier.padding(8.dp)) {
        if (missing.isNotEmpty()) {
            Card(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2))
            ) {
                Column(Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Close, null, Modifier.size(14.dp), tint = DangerRed)
                        Spacer(Modifier.width(4.dp))
                        Text("技能缺失", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = DangerRed)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "JD 要求 ${missing.joinToString(" · ")}，当前简历未覆盖。",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        suggestions.take(3).forEachIndexed { i, s ->
            Card(
                Modifier.fillMaxWidth().padding(bottom = 6.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = BrandBlueLight)
            ) {
                Column(Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, null, Modifier.size(14.dp), tint = BrandBlue)
                        Spacer(Modifier.width(4.dp))
                        Text("建议 ${i + 1}", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = BrandBlue)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(s, fontSize = 11.sp, color = TextPrimary, lineHeight = 16.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeywordsDetailPanel(
    score: Int,
    level: com.example.tielink.domain.model.MatchLevel,
    matched: List<String>,
    missing: List<String>
) {
    Column(Modifier.padding(8.dp)) {
        Text("匹配度 $score%", fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(BorderLight)) {
            Box(
                Modifier.fillMaxWidth(score / 100f).height(6.dp).clip(RoundedCornerShape(3.dp))
                    .background(
                        when (level) {
                            com.example.tielink.domain.model.MatchLevel.HIGH -> SuccessGreen
                            com.example.tielink.domain.model.MatchLevel.MEDIUM -> Color(0xFFF59E0B)
                            com.example.tielink.domain.model.MatchLevel.LOW -> DangerRed
                        }
                    )
            )
        }
        Spacer(Modifier.height(12.dp))

        Text("已匹配 (${matched.size})", fontSize = 12.sp, color = SuccessGreen, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            matched.forEach { kw ->
                Surface(shape = RoundedCornerShape(4.dp), color = BrandBlueLight) {
                    Text(kw, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 11.sp, color = SuccessGreen)
                }
            }
        }

        if (missing.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("未匹配 (${missing.size})", fontSize = 12.sp, color = DangerRed, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                missing.forEach { kw ->
                    Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFFEE2E2)) {
                        Text(kw, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 11.sp, color = DangerRed)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryPanel(history: List<String>) {
    Column(Modifier.padding(8.dp)) {
        if (history.isEmpty()) {
            Text("暂无修改记录", fontSize = 12.sp, color = TextTertiary, modifier = Modifier.padding(8.dp))
        } else {
            history.reversed().forEachIndexed { i, h ->
                Card(
                    Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    shape = RoundedCornerShape(6.dp),
                    colors = CardDefaults.cardColors(containerColor = BgWhite),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, BorderLight)
                ) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("v${history.size - i}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BrandBlue)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            h.removePrefix("→ "),
                            fontSize = 11.sp, color = TextSecondary,
                            maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  内容编辑 TAB (主区域)
// ═══════════════════════════════════════════════════════════════

@Composable
private fun ContentEditTab(
    modifier: Modifier = Modifier,
    resumeData: ResumeData?,
    originalText: String,
    polishedText: String,
    useVibeTemplate: Boolean,
    expandedSection: String?,
    onToggleSection: (String?) -> Unit,
    onToggleVibe: (Boolean) -> Unit,
    onFullScreen: () -> Unit,
    onUpdatePersonalInfo: (String?, String?, String?, String?, Uri?, List<ResumeData.SocialLink>) -> Unit,
    onUpdateExperience: (Int, ResumeData.Experience) -> Unit,
    onAddExperience: () -> Unit,
    onRemoveExperience: (Int) -> Unit,
    onUpdateEducation: (Int, ResumeData.Education) -> Unit,
    onAddEducation: () -> Unit,
    onRemoveEducation: (Int) -> Unit,
    onUpdateProject: (Int, ResumeData.Project) -> Unit,
    onAddProject: () -> Unit,
    onRemoveProject: (Int) -> Unit,
    onUpdateSkills: (List<String>) -> Unit,
    onAddSkill: (String) -> Unit,
    onRemoveSkill: (String) -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier
    ) {
        // Scrollable content
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // ── HTML Preview (collapsible) ──────────────────
            HtmlPreviewCard(
                resumeData = resumeData,
                useVibeTemplate = useVibeTemplate,
                onToggleVibe = onToggleVibe,
                onFullScreen = onFullScreen
            )

            Spacer(Modifier.height(12.dp))

            if (
                originalText.isNotBlank() &&
                polishedText.isNotBlank() &&
                originalText.trim() != polishedText.trim()
            ) {
                ResumeTransformationCard(
                    originalText = originalText,
                    polishedText = polishedText
                )
                Spacer(Modifier.height(12.dp))
            }

            if (resumeData != null) {
                // ── 个人信息 ─────────────────────────────────
                PersonalInfoEditCard(
                    data = resumeData,
                    isExpanded = expandedSection == "personal",
                    onToggle = { onToggleSection(if (expandedSection == "personal") null else "personal") },
                    onSave = { name, pos, phone, email, photoUri, links ->
                        onUpdatePersonalInfo(name, pos, phone, email, photoUri, links)
                    }
                )

                Spacer(Modifier.height(8.dp))

                // ── 项目经历 ─────────────────────────────────
                ProjectEditCard(
                    projects = resumeData.projects,
                    isExpanded = expandedSection == "projects",
                    onToggle = { onToggleSection(if (expandedSection == "projects") null else "projects") },
                    onUpdate = onUpdateProject,
                    onAdd = onAddProject,
                    onRemove = onRemoveProject
                )

                Spacer(Modifier.height(8.dp))

                // ── 教育背景 ─────────────────────────────────
                EducationEditCard(
                    education = resumeData.education,
                    isExpanded = expandedSection == "education",
                    onToggle = { onToggleSection(if (expandedSection == "education") null else "education") },
                    onUpdate = onUpdateEducation,
                    onAdd = onAddEducation,
                    onRemove = onRemoveEducation
                )

                Spacer(Modifier.height(8.dp))

                // ── 实习工作经历 ─────────────────────────────
                ExperienceEditCard(
                    experiences = resumeData.experiences,
                    isExpanded = expandedSection == "experience",
                    onToggle = { onToggleSection(if (expandedSection == "experience") null else "experience") },
                    onUpdate = onUpdateExperience,
                    onAdd = onAddExperience,
                    onRemove = onRemoveExperience
                )

                Spacer(Modifier.height(8.dp))

                // ── 专业技能 ─────────────────────────────────
                SkillsEditCard(
                    skills = resumeData.skills,
                    isExpanded = expandedSection == "skills",
                    onToggle = { onToggleSection(if (expandedSection == "skills") null else "skills") },
                    onUpdate = onUpdateSkills,
                    onAdd = onAddSkill,
                    onRemove = onRemoveSkill
                )
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ResumeTransformationCard(
    originalText: String,
    polishedText: String
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = BgWhite),
        border = BorderStroke(1.dp, BorderLight)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(BrandBlueLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        null,
                        Modifier.size(18.dp),
                        tint = BrandBlue
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "本次优化变化",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        "查看 AI 如何重组表达，而不只是看最终稿",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
                Text(
                    if (expanded) "收起" else "展开",
                    color = BrandBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                    HorizontalDivider(color = BorderLight)
                    Spacer(Modifier.height(12.dp))
                    TransformationTextBlock(
                        label = "原始表达",
                        text = originalText,
                        background = BgSurface,
                        labelColor = TextSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                    TransformationTextBlock(
                        label = "Vibe 优化后",
                        text = polishedText,
                        background = BrandBlueLight,
                        labelColor = BrandBlue
                    )
                }
            }
        }
    }
}

@Composable
private fun TransformationTextBlock(
    label: String,
    text: String,
    background: Color,
    labelColor: Color
) {
    Surface(shape = RoundedCornerShape(10.dp), color = background) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                label.uppercase(),
                color = labelColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.sp
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = text.trim().take(700),
                color = TextPrimary,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                maxLines = 12,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  1. HTML PREVIEW CARD
// ═══════════════════════════════════════════════════════════════

@Composable
private fun HtmlPreviewCard(
    resumeData: ResumeData?,
    useVibeTemplate: Boolean,
    onToggleVibe: (Boolean) -> Unit,
    onFullScreen: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = BgWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight)
    ) {
        Row(
            Modifier.fillMaxWidth().background(BgSurface).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📄 HTML 预览", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
                Spacer(Modifier.width(12.dp))
                TextButton(onClick = { onToggleVibe(!useVibeTemplate) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                    Text(
                        if (useVibeTemplate) "Vibe 风格" else "经典风格",
                        fontSize = 11.sp,
                        color = if (useVibeTemplate) BrandBlue else TextTertiary
                    )
                }
            }
            IconButton(onClick = onFullScreen, modifier = Modifier.size(28.dp), enabled = resumeData != null) {
                Icon(Icons.Default.Fullscreen, "全屏", Modifier.size(16.dp), tint = BrandBlue)
            }
        }
        if (resumeData != null) {
            ResumePreviewWebView(
                resumeData = resumeData,
                useVibeTemplate = useVibeTemplate,
                modifier = Modifier.height(240.dp)
            )
        } else {
            Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                Text("（简历数据不可用）", color = TextTertiary, fontSize = 13.sp)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  2. 个人信息编辑卡片
// ═══════════════════════════════════════════════════════════════

@Composable
private fun PersonalInfoEditCard(
    data: ResumeData,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onSave: (name: String, position: String, phone: String, email: String, photoUri: Uri?, links: List<ResumeData.SocialLink>) -> Unit
) {
    // Extract phone & email from contact string
    val existingPhone = Regex("""[\d\-\s()]{7,}""").find(data.contact)?.value?.trim() ?: ""
    val existingEmail = Regex("""[\w.\-]+@[\w.\-]+\.\w+""").find(data.contact)?.value?.trim() ?: ""

    var editName by rememberSaveable { mutableStateOf(data.name) }
    var editPosition by rememberSaveable { mutableStateOf(data.targetPosition) }
    var editPhone by rememberSaveable { mutableStateOf(existingPhone) }
    var editEmail by rememberSaveable { mutableStateOf(existingEmail) }
    var photoUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var photoBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val context = LocalContext.current

    // 社交链接列表（本地编辑副本）
    val editLinks = remember(data.links, isExpanded) {
        mutableStateListOf<ResumeData.SocialLink>().also { list ->
            list.addAll(data.links.map { it.copy() })
            // 如果原来为空，给一个空位让用户填写
            if (list.isEmpty()) {
                list.add(ResumeData.SocialLink("", ""))
            }
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        photoUri = uri
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                photoBitmap = BitmapFactory.decodeStream(inputStream, null, opts)
                inputStream?.close()
            } catch (_: Exception) { photoBitmap = null }
        } else {
            photoBitmap = null
        }
    }

    // 加载已保存的照片缩略图
    LaunchedEffect(data.photoBase64, isExpanded) {
        if (isExpanded && photoUri == null && data.photoBase64.isNotBlank()) {
            try {
                val bytes = android.util.Base64.decode(data.photoBase64, android.util.Base64.DEFAULT)
                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                photoBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            } catch (_: Exception) { photoBitmap = null }
        }
    }

    // 链接标签预设
    val linkPresets = listOf("GitHub", "博客", "LinkedIn", "个人网站", "其他")

    ExpandableSectionCard(
        icon = "👤",
        title = "个人信息",
        summary = buildString {
            if (data.name.isNotBlank()) append(data.name)
            if (data.targetPosition.isNotBlank()) {
                if (isNotEmpty()) append(" · ")
                append(data.targetPosition)
            }
            if (isEmpty()) append("未填写")
        },
        isExpanded = isExpanded,
        onToggle = onToggle
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Photo upload
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(64.dp).clip(CircleShape).background(if (photoBitmap != null) Color.Transparent else BgSurface).border(1.dp, if (photoBitmap != null) BrandBlue.copy(alpha = 0.5f) else BorderLight, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (photoBitmap != null) {
                        Image(
                            bitmap = photoBitmap!!.asImageBitmap(),
                            contentDescription = "个人照片",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(Icons.Default.CameraAlt, "上传照片", Modifier.size(28.dp), tint = TextTertiary)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("个人照片", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                    TextButton(
                        onClick = { photoPicker.launch("image/*") },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                    ) {
                        Text(if (photoUri != null) "重新选择" else "上传照片", fontSize = 12.sp, color = BrandBlue)
                    }
                }
            }

            OutlinedTextField(value = editName, onValueChange = { editName = it }, label = { Text("姓名") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = editPosition, onValueChange = { editPosition = it }, label = { Text("应聘职位") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = editPhone, onValueChange = { editPhone = it }, label = { Text("电话") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = editEmail, onValueChange = { editEmail = it }, label = { Text("邮箱") }, singleLine = true, modifier = Modifier.weight(1f))
            }

            // ── 社交链接 ──
            HorizontalDivider(color = BorderLight)
            Text("社交链接", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextSecondary)

            editLinks.forEachIndexed { index, link ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 标签下拉
                    var labelExpanded by remember { mutableStateOf(false) }
                    Box(Modifier.width(100.dp)) {
                        OutlinedTextField(
                            value = if (linkPresets.contains(link.label)) link.label else link.label.ifBlank { "" },
                            onValueChange = { newVal ->
                                editLinks[index] = link.copy(label = newVal)
                            },
                            label = { Text("标签") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            readOnly = false
                        )
                        // 预设快捷选择
                        TextButton(
                            onClick = { labelExpanded = true },
                            modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Text("▾", fontSize = 10.sp, color = TextTertiary)
                        }
                        DropdownMenu(expanded = labelExpanded, onDismissRequest = { labelExpanded = false }) {
                            linkPresets.forEach { preset ->
                                DropdownMenuItem(
                                    text = { Text(preset) },
                                    onClick = {
                                        editLinks[index] = link.copy(label = preset)
                                        labelExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    // URL 输入
                    OutlinedTextField(
                        value = link.url,
                        onValueChange = { newUrl ->
                            editLinks[index] = link.copy(url = newUrl)
                        },
                        label = { Text("URL") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    // 删除按钮
                    if (editLinks.size > 1) {
                        IconButton(onClick = { editLinks.removeAt(index) }) {
                            Icon(Icons.Default.Close, "删除", Modifier.size(18.dp), tint = TextTertiary)
                        }
                    }
                }
            }

            // 添加链接按钮
            TextButton(
                onClick = { editLinks.add(ResumeData.SocialLink("", "")) },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp, vertical = 0.dp)
            ) {
                Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("添加链接", fontSize = 12.sp, color = BrandBlue)
            }

            // Action row
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onToggle) { Text("取消", color = TextSecondary) }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        // 过滤掉空链接
                        val validLinks = editLinks.filter { it.label.isNotBlank() && it.url.isNotBlank() }
                        onSave(editName, editPosition, editPhone, editEmail, photoUri, validLinks)
                        onToggle()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("保存")
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  3. 项目经历编辑卡片
// ═══════════════════════════════════════════════════════════════

@Composable
private fun ProjectEditCard(
    projects: List<ResumeData.Project>,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onUpdate: (Int, ResumeData.Project) -> Unit,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit
) {
    val count = projects.size
    ExpandableSectionCard(
        icon = "📁",
        title = "项目经历",
        summary = if (count > 0) "${count}项 · ${projects.take(2).joinToString(", ") { it.name.take(12) }}" else "暂无",
        isExpanded = isExpanded,
        onToggle = onToggle
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            projects.forEachIndexed { index, proj ->
                ProjectItemEditor(
                    project = proj,
                    index = index,
                    onUpdate = { onUpdate(index, it) },
                    onRemove = { onRemove(index) }
                )
            }
            OutlinedButton(
                onClick = onAdd,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("添加项目")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = onToggle,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("完成") }
            }
        }
    }
}

@Composable
private fun ProjectItemEditor(
    project: ResumeData.Project,
    index: Int,
    onUpdate: (ResumeData.Project) -> Unit,
    onRemove: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf(project.name) }
    var period by rememberSaveable { mutableStateOf(project.period) }
    var desc by rememberSaveable { mutableStateOf(project.description) }
    var tech by rememberSaveable { mutableStateOf(project.technologies.joinToString(", ")) }

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = BgSurface),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, BorderLight)
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("项目 ${index + 1}", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextTertiary)
                IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, "删除", Modifier.size(14.dp), tint = DangerRed)
                }
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; onUpdate(project.copy(name = name, period = period, description = desc, technologies = tech.split(",", "，").map { it.trim() }.filter { it.isNotBlank() })) },
                label = { Text("项目名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = period,
                onValueChange = { period = it; onUpdate(project.copy(name = name, period = period, description = desc, technologies = tech.split(",", "，").map { it.trim() }.filter { it.isNotBlank() })) },
                label = { Text("时间") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = desc,
                onValueChange = { desc = it; onUpdate(project.copy(name = name, period = period, description = desc, technologies = tech.split(",", "，").map { it.trim() }.filter { it.isNotBlank() })) },
                label = { Text("项目描述") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                maxLines = 4,
                textStyle = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = tech,
                onValueChange = { tech = it; onUpdate(project.copy(name = name, period = period, description = desc, technologies = tech.split(",", "，").map { it.trim() }.filter { it.isNotBlank() })) },
                label = { Text("技术栈（逗号分隔）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodySmall
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  4. 教育背景编辑卡片
// ═══════════════════════════════════════════════════════════════

@Composable
private fun EducationEditCard(
    education: List<ResumeData.Education>,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onUpdate: (Int, ResumeData.Education) -> Unit,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit
) {
    val count = education.size
    ExpandableSectionCard(
        icon = "🎓",
        title = "教育背景",
        summary = if (count > 0) "${count}项 · ${education.take(2).joinToString(", ") { it.school.take(10) }}" else "暂无",
        isExpanded = isExpanded,
        onToggle = onToggle
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            education.forEachIndexed { index, edu ->
                EducationItemEditor(
                    education = edu,
                    index = index,
                    onUpdate = { onUpdate(index, it) },
                    onRemove = { onRemove(index) }
                )
            }
            OutlinedButton(
                onClick = onAdd,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("添加教育经历")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = onToggle,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("完成") }
            }
        }
    }
}

@Composable
private fun EducationItemEditor(
    education: ResumeData.Education,
    index: Int,
    onUpdate: (ResumeData.Education) -> Unit,
    onRemove: () -> Unit
) {
    var school by rememberSaveable { mutableStateOf(education.school) }
    var degree by rememberSaveable { mutableStateOf(education.degree) }
    var period by rememberSaveable { mutableStateOf(education.period) }

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = BgSurface),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, BorderLight)
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("教育经历 ${index + 1}", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextTertiary)
                IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, "删除", Modifier.size(14.dp), tint = DangerRed)
                }
            }
            OutlinedTextField(
                value = school, onValueChange = { school = it; onUpdate(education.copy(school = school, degree = degree, period = period)) },
                label = { Text("学校") }, singleLine = true, modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = degree, onValueChange = { degree = it; onUpdate(education.copy(school = school, degree = degree, period = period)) },
                label = { Text("专业 / 学位") }, singleLine = true, modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = period, onValueChange = { period = it; onUpdate(education.copy(school = school, degree = degree, period = period)) },
                label = { Text("时间") }, singleLine = true, modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodySmall
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  5. 实习工作经历编辑卡片
// ═══════════════════════════════════════════════════════════════

@Composable
private fun ExperienceEditCard(
    experiences: List<ResumeData.Experience>,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onUpdate: (Int, ResumeData.Experience) -> Unit,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit
) {
    val count = experiences.size
    ExpandableSectionCard(
        icon = "💼",
        title = "实习工作经历",
        summary = if (count > 0) "${count}项 · ${experiences.take(2).joinToString(", ") { it.company.take(10) }}" else "暂无",
        isExpanded = isExpanded,
        onToggle = onToggle
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            experiences.forEachIndexed { index, exp ->
                ExperienceItemEditor(
                    experience = exp,
                    index = index,
                    onUpdate = { onUpdate(index, it) },
                    onRemove = { onRemove(index) }
                )
            }
            OutlinedButton(
                onClick = onAdd,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("添加经历")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = onToggle,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("完成") }
            }
        }
    }
}

@Composable
private fun ExperienceItemEditor(
    experience: ResumeData.Experience,
    index: Int,
    onUpdate: (ResumeData.Experience) -> Unit,
    onRemove: () -> Unit
) {
    var company by rememberSaveable { mutableStateOf(experience.company) }
    var title by rememberSaveable { mutableStateOf(experience.title) }
    var period by rememberSaveable { mutableStateOf(experience.period) }
    var desc by rememberSaveable { mutableStateOf(experience.description) }

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = BgSurface),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, BorderLight)
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("经历 ${index + 1}", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextTertiary)
                IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, "删除", Modifier.size(14.dp), tint = DangerRed)
                }
            }
            OutlinedTextField(
                value = company, onValueChange = { company = it; onUpdate(experience.copy(company = company, title = title, period = period, description = desc)) },
                label = { Text("公司") }, singleLine = true, modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = title, onValueChange = { title = it; onUpdate(experience.copy(company = company, title = title, period = period, description = desc)) },
                label = { Text("职位") }, singleLine = true, modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = period, onValueChange = { period = it; onUpdate(experience.copy(company = company, title = title, period = period, description = desc)) },
                label = { Text("时间") }, singleLine = true, modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = desc, onValueChange = { desc = it; onUpdate(experience.copy(company = company, title = title, period = period, description = desc)) },
                label = { Text("工作描述") }, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp), maxLines = 4, textStyle = MaterialTheme.typography.bodySmall
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  6. 专业技能编辑卡片
// ═══════════════════════════════════════════════════════════════

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SkillsEditCard(
    skills: List<String>,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onUpdate: (List<String>) -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    ExpandableSectionCard(
        icon = "🛠️",
        title = "专业技能",
        summary = if (skills.isNotEmpty()) "${skills.size}项 · ${skills.take(4).joinToString(", ") { it.take(8) }}" else "暂无",
        isExpanded = isExpanded,
        onToggle = onToggle
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Editable chips
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                skills.forEach { skill ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = BrandBlueLight,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BrandBlue.copy(alpha = 0.2f))
                    ) {
                        Row(
                            Modifier.padding(start = 10.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(skill, fontSize = 12.sp, color = BrandBlue)
                            Spacer(Modifier.width(2.dp))
                            Icon(
                                Icons.Default.Close, "删除$skill",
                                Modifier.size(16.dp).clickable { onRemove(skill) },
                                tint = BrandBlue.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            // Add new skill
            var newSkill by rememberSaveable { mutableStateOf("") }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newSkill,
                    onValueChange = { newSkill = it },
                    label = { Text("新技能") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodySmall
                )
                Button(
                    onClick = {
                        if (newSkill.isNotBlank()) {
                            onAdd(newSkill.trim())
                            newSkill = ""
                        }
                    },
                    enabled = newSkill.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(52.dp)
                ) {
                    Text("添加", fontSize = 13.sp)
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = onToggle,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("完成") }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  可展开卡片容器
// ═══════════════════════════════════════════════════════════════

@Composable
private fun ExpandableSectionCard(
    icon: String,
    title: String,
    summary: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        Modifier
            .fillMaxWidth()
            .animateContentSize(appMotionTween()),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isExpanded) BrandBlueLight.copy(alpha = 0.3f) else BgWhite
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isExpanded) BrandBlue.copy(alpha = 0.3f) else BorderLight
        )
    ) {
        Column {
            // Header (always visible, clickable)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(icon, fontSize = 20.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text(summary, fontSize = 12.sp, color = TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                // Expand/collapse indicator
                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.Edit,
                    contentDescription = if (isExpanded) "收起" else "编辑",
                    Modifier.size(20.dp),
                    tint = if (isExpanded) BrandBlue else TextTertiary
                )
            }

            // Expanded content
            AnimatedVisibility(
                visible = isExpanded,
                enter = motionExpandIn(),
                exit = motionCollapseOut()
            ) {
                Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                    content()
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  完成庆祝动画 (Konfetti 撒花)
// ═══════════════════════════════════════════════════════════════

@Composable
private fun CompletionCelebration(
    visible: Boolean,
    onDismiss: () -> Unit
) {
    // Auto-dismiss after particles finish
    LaunchedEffect(visible) {
        if (visible) {
            delay(1800)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = motionExpandIn(),
        exit = motionCollapseOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(interactionSource = null, indication = null, onClick = onDismiss)
        ) {
            val confettiColors = listOf(
                0xfce18a, 0xff726d, 0xf4306d, 0xb48def,
                0xFF4ECDC4.toInt(), 0xFF55EFC4.toInt(), 0xFF74B9FF.toInt(),
                0xFFFF7979.toInt(), 0xFFFDCB6E.toInt(), 0xFFE17055.toInt(),
                0xFF6C5CE7.toInt(), 0xFF00B894.toInt(), 0xFFE84393.toInt(),
                0xFF0984E3.toInt(), 0xFFFAB1A0.toInt(), 0xFF81ECEC.toInt(),
                0xFFFD79A8.toInt(), 0xFF00CEC9.toInt(), 0xFFF8A5C2.toInt()
            )

            // Dense grid across full top edge: 13 points, spread=90 → continuous curtain
            val parties = remember {
                (0..12).map { i ->
                    val x = i / 12.0  // 0.0, 0.083, 0.167, ... 1.0
                    nl.dionsegijn.konfetti.core.Party(
                        angle = 90,
                        spread = 90,
                        speed = 50f,
                        maxSpeed = 100f,
                        damping = 0.93f,       // keeps 93% velocity per frame → long gentle fall
                        colors = confettiColors,
                        position = nl.dionsegijn.konfetti.core.Position.Relative(x, 0.0),
                        emitter = nl.dionsegijn.konfetti.core.emitter.Emitter(
                            duration = 200,
                            java.util.concurrent.TimeUnit.MILLISECONDS
                        ).max(25),
                        timeToLive = 2800,
                        fadeOutEnabled = true
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                shadowElevation = 6.dp,
                modifier = Modifier.align(Alignment.Center)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = BrandBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("优化已完成", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text("结果已更新，可继续查看和编辑", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
            }
        }
    }
}

