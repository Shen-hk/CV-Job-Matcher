package com.example.tielink.ui.agent

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.tielink.R
import com.example.tielink.domain.model.AgentMessage
import com.example.tielink.domain.model.AgentMessageRole
import com.example.tielink.ui.theme.TieLinkTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentChatScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToResumeOptimize: () -> Unit,
    onNavigateToMockInterview: () -> Unit,
    onNavigateToTracking: () -> Unit,
    viewModel: AgentViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val context = androidx.compose.ui.platform.LocalContext.current

    // 新消息时立即滚动到底部（不使用动画，避免与 streaming 更新冲突）
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.scrollToItem(state.messages.size - 1)
        }
    }
    // streaming 过程中跟随内容增长滚到底部
    LaunchedEffect(state.isStreaming) {
        if (state.isStreaming) {
            snapshotFlow { state.messages.size }
                .collect { listState.scrollToItem(maxOf(0, it - 1)) }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val mimeType = context.contentResolver.getType(it)
            var fileName = "文件"
            context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) fileName = cursor.getString(nameIndex)
            }
            viewModel.attachFile(context, it, mimeType, fileName)
        }
    }

    // 收集卡片内"上传简历"按钮触发的事件
    LaunchedEffect(Unit) {
        viewModel.openFilePicker.collect {
            filePickerLauncher.launch(arrayOf(
                "application/pdf",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "text/plain", "image/*"
            ))
        }
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size - 1)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("TieLink", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Menu, contentDescription = "菜单")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            Column {
                QuickActionsBar(
                    onResumeOptimize = onNavigateToResumeOptimize,
                    onMockInterview = onNavigateToMockInterview,
                    onTracking = onNavigateToTracking
                )
                if (state.pendingAttachmentName != null || state.isParsingFile) {
                    AttachmentBar(
                        fileName = state.pendingAttachmentName,
                        isParsing = state.isParsingFile,
                        onClear = { viewModel.clearAttachment() }
                    )
                }
                InputArea(
                    text = state.inputText,
                    isStreaming = state.isLoading,
                    hasAttachment = state.pendingAttachmentText != null,
                    onTextChange = { viewModel.updateInputText(it) },
                    onSend = { viewModel.sendMessage() },
                    onCancel = { viewModel.cancelStream() },
                    onAttach = {
                        filePickerLauncher.launch(arrayOf(
                            "application/pdf",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            "text/plain", "image/*"
                        ))
                    }
                )
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {

            // Error banner
            AnimatedVisibility(
                visible = state.error != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                state.error?.let { error ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(error, color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            IconButton(onClick = { viewModel.dismissError() }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "关闭",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (state.messages.isEmpty()) {
                    // ── 欢迎页（Gemini 风格）─────────────────────────────────────
                    WelcomePage(
                        prompts = state.suggestedPrompts,
                        onPromptClick = { viewModel.sendPrompt(it) }
                    )
                } else {
                    // ── 聊天视图 ───────────────────────────────────────────────
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        item { Spacer(modifier = Modifier.height(4.dp)) }

                        items(
                            items = state.messages,
                            key = { it.id },
                            contentType = { msg ->
                                when {
                                    msg.toolLoadingName != null -> "tool_loading"
                                    msg.card != null -> "card"
                                    msg.role == AgentMessageRole.USER -> "user"
                                    else -> "agent"
                                }
                            }
                        ) { message ->
                            MessageRow(message = message)
                        }

                        item { Spacer(modifier = Modifier.height(4.dp)) }
                    }

                    // 底部渐变遮罩
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.surface
                                    )
                                )
                            )
                    )
                }
            }
        }
    }
}

// ─── Welcome page ────────────────────────────────────────────────────────────

@Composable
private fun WelcomePage(
    prompts: List<String>,
    onPromptClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.Start
    ) {
        // 上方 30% 留白，让内容落在视觉中偏上位置
        Spacer(Modifier.fillMaxSize(0.15f))

//        // 应用图标
//        androidx.compose.foundation.Image(
//            painter = painterResource(R.mipmap.ic_launcher_round),
//            contentDescription = null,
//            modifier = Modifier.size(52.dp)
//        )

//        Spacer(Modifier.height(16.dp))

        Text(
            text = "我是 TieLink",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = "分析匹配度、优化简历、模拟面试、追踪投递",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        // 建议 chips — 单列，宽度自适应文字
        if (prompts.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                prompts.forEach { prompt ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.clickable { onPromptClick(prompt) }
                    ) {
                        Text(
                            text = prompt,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))
    }
}

// ─── Message row — dispatches to the right layout ─────────────────────────────

@Composable
private fun MessageRow(message: AgentMessage) {
    when {
        // Tool loading placeholder
        message.toolLoadingName != null -> ToolLoadingBubble(message)
        // Rich card (from tool result)
        message.card != null -> {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                UiCardComposable(card = message.card, modifier = Modifier.fillMaxWidth())
            }
        }
        // User bubble
        message.role == AgentMessageRole.USER -> UserBubble(message)
        // Agent bubble (text, with optional thinking panel)
        else -> AgentBubble(message)
    }
}

// ─── Agent bubble ─────────────────────────────────────────────────────────────

@Composable
private fun AgentBubble(message: AgentMessage) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        // Thinking panel (collapsible)
        if (!message.thinkingContent.isNullOrBlank()) {
            ThinkingPanel(thinkingContent = message.thinkingContent, isStreaming = message.isStreaming)
            Spacer(Modifier.height(4.dp))
        }

        // Main content bubble
        if (message.content.isNotBlank() || message.isStreaming) {
            Surface(
                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (message.isStreaming && message.content.isBlank()) {
                        // Animated dots while waiting for first token
                        ThinkingDotsIndicator()
                    } else {
                        MarkdownText(
                            text = message.content + if (message.isStreaming) "▌" else "",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

// ─── User bubble ──────────────────────────────────────────────────────────────

@Composable
private fun UserBubble(message: AgentMessage) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Card(
            modifier = Modifier.widthIn(max = 280.dp),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

// ─── Tool loading bubble ──────────────────────────────────────────────────────

@Composable
private fun ToolLoadingBubble(message: AgentMessage) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(message.content, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ─── Collapsible thinking panel ───────────────────────────────────────────────

@Composable
private fun ThinkingPanel(thinkingContent: String, isStreaming: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.animateContentSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isStreaming) {
                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = if (isStreaming) "思考中..." else "思考过程",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.weight(1f)
                )
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

// ─── Animated dots (waiting for first token) ──────────────────────────────────

@Composable
private fun ThinkingDotsIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f, label = "dotsAlpha",
        animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse)
    )
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(2.dp)) {
        repeat(3) { i ->
            Box(modifier = Modifier.size(6.dp).clip(CircleShape)
                .alpha(if (i == 0) alpha else if (i == 1) (alpha + 0.2f).coerceAtMost(1f) else (alpha + 0.4f).coerceAtMost(1f))
                .background(MaterialTheme.colorScheme.onSurfaceVariant))
        }
    }
}

// ─── Bottom bar components ────────────────────────────────────────────────────

@Composable
private fun QuickActionsBar(
    onResumeOptimize: () -> Unit,
    onMockInterview: () -> Unit,
    onTracking: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val chipColor = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
        AssistChip(onClick = onResumeOptimize, label = { Text("简历优化") },
            leadingIcon = { Icon(Icons.Outlined.Description, null, modifier = Modifier.size(14.dp)) },
            shape = RoundedCornerShape(16.dp), colors = chipColor)
        AssistChip(onClick = onMockInterview, label = { Text("模拟面试") },
            leadingIcon = { Icon(Icons.Outlined.School, null, modifier = Modifier.size(14.dp)) },
            shape = RoundedCornerShape(16.dp), colors = chipColor)
        AssistChip(onClick = onTracking, label = { Text("投递追踪") },
            leadingIcon = { Icon(Icons.Default.Checklist, null, modifier = Modifier.size(14.dp)) },
            shape = RoundedCornerShape(16.dp), colors = chipColor)
    }
}

@Composable
private fun AttachmentBar(
    fileName: String?,
    isParsing: Boolean,
    onClear: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isParsing) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("正在解析文件...", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Icon(Icons.Default.AttachFile, null, modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text(fileName ?: "文件已附加", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = onClear, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, "移除附件", modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun InputArea(
    text: String,
    isStreaming: Boolean,
    hasAttachment: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    onAttach: () -> Unit
) {
    val gradientColors = listOf(
        Color(0xFF6C63FF), // 紫
        Color(0xFF3B82F6), // 蓝
        Color(0xFF06B6D4), // 青
        Color(0xFF10B981)  // 绿
    )

    // 悬浮容器：顶部渐变遮罩 + 输入框
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(bottom = 20.dp)
    ) {
        // 输入框本体
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .shadow(
                    elevation = 24.dp,
                    shape = RoundedCornerShape(28.dp),
                    ambientColor = Color(0xFF6C63FF).copy(alpha = 0.55f),
                    spotColor = Color(0xFF3B82F6).copy(alpha = 0.55f)
                )
                .border(
                    width = 2.dp,
                    brush = Brush.horizontalGradient(gradientColors),
                    shape = RoundedCornerShape(28.dp)
                )
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        if (hasAttachment) "添加说明（可选）..." else "说点什么...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                maxLines = 4,
                shape = RoundedCornerShape(24.dp),
                leadingIcon = {
                    IconButton(
                        onClick = onAttach,
                        enabled = !isStreaming,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.AttachFile,
                            contentDescription = "上传文件",
                            modifier = Modifier.size(20.dp),
                            tint = if (hasAttachment) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                },
                trailingIcon = {
                    IconButton(
                        onClick = if (isStreaming) onCancel else onSend,
                        enabled = isStreaming || text.isNotBlank() || hasAttachment,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (isStreaming) Icons.Default.Close else Icons.AutoMirrored.Filled.Send,
                            contentDescription = if (isStreaming) "停止" else "发送",
                            modifier = Modifier.size(20.dp),
                            tint = if (isStreaming || text.isNotBlank() || hasAttachment)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )
        }
    }
}

// ─── Previews ──────────────────────────────────────────────────────────────

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun AgentBubblePreview() {
    TieLinkTheme {
        Column(modifier = Modifier.padding(8.dp)) {
            AgentBubble(AgentMessage(
                role = AgentMessageRole.AGENT,
                content = "你好！我是智简求职助手，我可以帮你优化简历、模拟面试和管理投递进度。请告诉我你需要什么帮助？",
                thinkingContent = "用户进入了聊天界面，需要提供友好的欢迎信息并介绍我的功能范围。"
            ))
            AgentBubble(AgentMessage(
                role = AgentMessageRole.AGENT,
                content = "你的简历中缺少「数据分析」相关技能，建议补充。",
                thinkingContent = null
            ))
            AgentBubble(AgentMessage(
                role = AgentMessageRole.AGENT,
                content = "",
                isStreaming = true
            ))
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun UserBubblePreview() {
    TieLinkTheme {
        Column(modifier = Modifier.padding(8.dp)) {
            UserBubble(AgentMessage(
                role = AgentMessageRole.USER,
                content = "帮我优化简历"
            ))
            UserBubble(AgentMessage(
                role = AgentMessageRole.USER,
                content = "我想应聘字节跳动的Android开发岗位，请帮我分析匹配度。"
            ))
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ThinkingPanelPreview() {
    TieLinkTheme {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ThinkingPanel(
                thinkingContent = "正在分析用户简历与JD的匹配度...\n关键词提取完成，开始进行语义匹配。",
                isStreaming = true
            )
            ThinkingPanel(
                thinkingContent = "简历中技能清单覆盖了JD要求的80%关键词。缺少的关键词包括：Kotlin Coroutines、Jetpack Compose动画",
                isStreaming = false
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ThinkingDotsIndicatorPreview() {
    TieLinkTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            ThinkingDotsIndicator()
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun QuickActionsBarPreview() {
    TieLinkTheme {
        QuickActionsBar(
            onResumeOptimize = {},
            onMockInterview = {},
            onTracking = {}
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun AttachmentBarPreview() {
    TieLinkTheme {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AttachmentBar(
                fileName = "我的简历.pdf",
                isParsing = false,
                onClear = {}
            )
            AttachmentBar(
                fileName = null,
                isParsing = true,
                onClear = {}
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun InputAreaPreview() {
    TieLinkTheme {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            InputArea(
                text = "",
                isStreaming = false,
                hasAttachment = false,
                onTextChange = {},
                onSend = {},
                onCancel = {},
                onAttach = {}
            )
            InputArea(
                text = "帮我优化简历中关于项目经验的部分",
                isStreaming = false,
                hasAttachment = true,
                onTextChange = {},
                onSend = {},
                onCancel = {},
                onAttach = {}
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ToolLoadingBubblePreview() {
    TieLinkTheme {
        ToolLoadingBubble(AgentMessage(
            role = AgentMessageRole.AGENT,
            content = "正在分析简历匹配度...",
            toolLoadingName = "match_analysis"
        ))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun AgentChatScreenContentPreview() {
    // Preview of the screen's static layout — hiltViewModel() unavailable in preview
    TieLinkTheme {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text("TieLink", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    },
                    navigationIcon = {
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.Menu, contentDescription = "菜单")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            },
            bottomBar = {
                Column {
                    QuickActionsBar(onResumeOptimize = {}, onMockInterview = {}, onTracking = {})
                    InputArea(
                        text = "", isStreaming = false, hasAttachment = false,
                        onTextChange = {}, onSend = {}, onCancel = {}, onAttach = {}
                    )
                }
            }
        ) { paddingValues ->
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(4.dp)) }
                    item {
                        UserBubble(AgentMessage(role = AgentMessageRole.USER, content = "帮我优化简历"))
                    }
                    item {
                        AgentBubble(AgentMessage(
                            role = AgentMessageRole.AGENT,
                            content = "好的！我分析了你的简历和JD，发现以下可以优化的地方:\n\n1. 项目经验中缺少量化数据\n2. 技能清单未覆盖「Kotlin Coroutines」\n3. 建议添加技术博客链接",
                            thinkingContent = "分析用户简历... 已匹配15个关键词中有12个，3个缺失。"
                        ))
                    }
                    item { Spacer(modifier = Modifier.height(4.dp)) }
                }
            }
        }
    }
}
