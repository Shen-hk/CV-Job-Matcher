package com.example.tielink.ui.agent

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tielink.domain.model.AgentMessage
import com.example.tielink.domain.model.AgentMessageRole
import com.example.tielink.domain.model.AgentProcessStage
import com.example.tielink.domain.model.AgentProcessState
import com.example.tielink.domain.model.ContextBarState
import com.example.tielink.domain.model.DynamicCardAction
import com.example.tielink.ui.theme.TieLinkTheme

@Composable
fun WelcomePage(
    contextBar: ContextBarState,
    prompts: List<String>,
    onPromptClick: (String) -> Unit,
    onOpenJd: () -> Unit,
    onOpenResume: () -> Unit,
    onUploadResume: () -> Unit,
    onOpenTracking: () -> Unit
) {
    val hasJd = contextBar.jdTitle != null
    val hasResume = contextBar.resumeVersionName != null
    val readyCount = listOf(hasJd, hasResume).count { it }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
    ) {
        Spacer(Modifier.height(10.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            color = Color.Transparent
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF102A43), Color(0xFF174C5B), Color(0xFF16756B))
                        )
                    )
                    .padding(20.dp)
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color.White.copy(alpha = 0.12f)
                        ) {
                            Row(
                                Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    null,
                                    Modifier.size(14.dp),
                                    tint = Color(0xFF7EE7D8)
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    "CAREER AGENT",
                                    color = Color(0xFFD7FFF8),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            "$readyCount / 2 已就绪",
                            color = Color.White.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(
                        "把岗位变成\n你的下一次机会",
                        color = Color.White,
                        fontSize = 28.sp,
                        lineHeight = 34.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.6).sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "岗位理解、简历重塑、投递节奏，由一个 Agent 串成完整闭环。",
                        color = Color.White.copy(alpha = 0.74f),
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 20.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            WorkspaceContextCard(
                modifier = Modifier.weight(1f),
                eyebrow = "目标岗位",
                value = contextBar.jdTitle ?: "还未选择 JD",
                detail = contextBar.jdCompany ?: if (hasJd) "岗位信息已连接" else "导入后可分析匹配度",
                icon = Icons.Default.Work,
                ready = hasJd,
                onClick = onOpenJd
            )
            WorkspaceContextCard(
                modifier = Modifier.weight(1f),
                eyebrow = "当前简历",
                value = contextBar.resumeVersionName ?: "还未上传简历",
                detail = if (hasResume) "简历版本已连接" else "原格式保存，按需润色",
                icon = Icons.Default.Description,
                ready = hasResume,
                onClick = onOpenResume
            )
        }

        Spacer(Modifier.height(14.dp))
        Text(
            "开始一条工作流",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WorkspaceAction(
                modifier = Modifier.weight(1f),
                title = "岗位雷达",
                caption = "导入 JD",
                icon = Icons.Default.Radar,
                color = Color(0xFF0E7490),
                onClick = onOpenJd
            )
            WorkspaceAction(
                modifier = Modifier.weight(1f),
                title = "简历重塑",
                caption = "上传原件",
                icon = Icons.Default.UploadFile,
                color = Color(0xFFB45309),
                onClick = onUploadResume
            )
            WorkspaceAction(
                modifier = Modifier.weight(1f),
                title = "投递节奏",
                caption = "查看看板",
                icon = Icons.Default.CheckCircle,
                color = Color(0xFF047857),
                onClick = onOpenTracking
            )
        }

        Spacer(Modifier.height(18.dp))

        if (prompts.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "让 Agent 接着做",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                prompts.take(3).forEach { prompt ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPromptClick(prompt) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = prompt,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                Icons.Default.NorthEast,
                                null,
                                Modifier.size(17.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun WorkspaceContextCard(
    modifier: Modifier,
    eyebrow: String,
    value: String,
    detail: String,
    icon: ImageVector,
    ready: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    null,
                    Modifier.size(17.dp),
                    tint = if (ready) Color(0xFF0F766E) else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    eyebrow.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.7.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(9.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun WorkspaceAction(
    modifier: Modifier,
    title: String,
    caption: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.08f)
    ) {
        Column(Modifier.padding(12.dp)) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, Modifier.size(18.dp), tint = Color.White)
            }
            Spacer(Modifier.height(10.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
            Text(
                caption,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
fun MessageRow(
    message: AgentMessage,
    inlineProcessState: AgentProcessState? = null,
    onCancelInlineProcess: () -> Unit = {},
    onNavigateToResumePreview: (Long) -> Unit = {},
    onNavigateToResumeLibrary: () -> Unit = {},
    onRequestResumeUpload: () -> Unit = {},
    onDynamicAction: (DynamicCardAction) -> Unit = {}
) {
    when {
        message.toolLoadingName != null -> ToolLoadingBubble(message)
        message.card != null -> {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                UiCardComposable(
                    card = message.card,
                    modifier = Modifier.fillMaxWidth(),
                    onNavigateToResumePreview = onNavigateToResumePreview,
                    onNavigateToResumeLibrary = onNavigateToResumeLibrary,
                    onRequestResumeUpload = onRequestResumeUpload,
                    onDynamicAction = onDynamicAction
                )
            }
        }
        message.role == AgentMessageRole.USER -> UserBubble(message)
        else -> AgentBubble(message, inlineProcessState, onCancelInlineProcess)
    }
}

@Composable
fun AgentBubble(
    message: AgentMessage,
    inlineProcessState: AgentProcessState? = null,
    onCancelInlineProcess: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        if (!message.thinkingContent.isNullOrBlank()) {
            ThinkingPanel(thinkingContent = message.thinkingContent, isStreaming = message.isStreaming)
            Spacer(Modifier.height(6.dp))
        }

        if (message.content.isNotBlank() || message.isStreaming || inlineProcessState?.isActive == true) {
            Surface(
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(12.dp)) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .heightIn(min = 24.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        if (inlineProcessState?.isActive == true) {
                            InlineProcessStatus(
                                processState = inlineProcessState,
                                onCancel = onCancelInlineProcess
                            )
                            if (message.content.isNotBlank() || message.isStreaming) {
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                        if (message.isStreaming && message.content.isBlank()) {
                            ThinkingDotsIndicator()
                        } else {
                            MarkdownText(
                                text = message.content + if (message.isStreaming) "..." else "",
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InlineProcessStatus(
    processState: AgentProcessState,
    onCancel: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val accent = when (processState.stage) {
        AgentProcessStage.THINKING -> MaterialTheme.colorScheme.tertiary
        AgentProcessStage.RETRIEVING -> MaterialTheme.colorScheme.primary
        AgentProcessStage.DRAWING -> MaterialTheme.colorScheme.secondary
        AgentProcessStage.TEXT_GENERATION -> MaterialTheme.colorScheme.primary
        AgentProcessStage.INTERRUPTED -> MaterialTheme.colorScheme.outline
        AgentProcessStage.IDLE -> MaterialTheme.colorScheme.outline
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = accent.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.16f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.animateContentSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (processState.stage != AgentProcessStage.INTERRUPTED) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.6.dp,
                        color = accent
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = processState.title.ifBlank { "正在处理" },
                        style = MaterialTheme.typography.labelMedium,
                        color = accent
                    )
                    Text(
                        text = buildString {
                            append(processState.detail.ifBlank { "正在继续生成内容" })
                            processState.sourceLabel?.takeIf { it.isNotBlank() }?.let {
                                append(" · ")
                                append(it)
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) 6 else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (processState.canCancel) {
                    IconButton(onClick = onCancel, modifier = Modifier.size(24.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "停止生成",
                            modifier = Modifier.size(14.dp),
                            tint = accent
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedVisibility(visible = expanded && processState.sourceBreakdown.isNotEmpty()) {
                Column(modifier = Modifier.padding(start = 30.dp, end = 10.dp, bottom = 8.dp)) {
                    Text(
                        text = processState.sourceBreakdown.joinToString(" / "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun UserBubble(message: AgentMessage) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 300.dp),
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 6.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
            color = Color.Transparent
        ) {
            Box(
                modifier = Modifier.background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary
                        )
                    )
                )
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)
                )
            }
        }
    }
}

@Composable
fun ToolLoadingBubble(message: AgentMessage) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.8.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    message.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ThinkingPanel(thinkingContent: String, isStreaming: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.animateContentSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isStreaming) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = if (isStreaming) "思考中..." else "思考完成",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = FontStyle.Italic
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        text = thinkingContent,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Default,
                            fontSize = 12.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp),
                        fontStyle = FontStyle.Italic
                    )
                }
            }
        }
    }
}

@Composable
fun ThinkingDotsIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        label = "dotsAlpha",
        animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse)
    )
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(2.dp)) {
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .alpha(
                        when (index) {
                            0 -> alpha
                            1 -> (alpha + 0.2f).coerceAtMost(1f)
                            else -> (alpha + 0.4f).coerceAtMost(1f)
                        }
                    )
                    .background(MaterialTheme.colorScheme.onSurfaceVariant)
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun AgentChatMessagesPreview() {
    TieLinkTheme {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            WelcomePage(
                contextBar = ContextBarState(
                    jdTitle = "Android 高级工程师",
                    jdCompany = "示例科技",
                    resumeVersionName = "移动端主简历"
                ),
                prompts = listOf("帮我优化简历", "分析岗位匹配度"),
                onPromptClick = {},
                onOpenJd = {},
                onOpenResume = {},
                onUploadResume = {},
                onOpenTracking = {}
            )
            MessageRow(
                message = AgentMessage(
                    role = AgentMessageRole.USER,
                    content = "帮我优化简历"
                )
            )
            MessageRow(
                message = AgentMessage(
                    role = AgentMessageRole.AGENT,
                    content = "我先帮你梳理一下简历结构。",
                    thinkingContent = "正在生成建议"
                )
            )
            MessageRow(
                message = AgentMessage(
                    role = AgentMessageRole.AGENT,
                    content = "正在分析匹配度...",
                    toolLoadingName = "match_analysis"
                )
            )
        }
    }
}
