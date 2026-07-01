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
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.tielink.ui.history.HistoryViewModel
import com.example.tielink.domain.model.AgentMessage
import com.example.tielink.domain.model.AgentMessageRole
import com.example.tielink.ui.theme.TieLinkTheme
import androidx.compose.material3.rememberDrawerState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentChatScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToResumeOptimize: () -> Unit,
    onNavigateToTracking: () -> Unit,
    onNavigateToHistoryRecord: (Long) -> Unit = {},
    onNavigateToJdList: () -> Unit = {},
    onNavigateToResumeLibrary: () -> Unit = {},
    onNavigateToResumePreview: (Long) -> Unit = {},
    viewModel: AgentViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val historyViewModel: HistoryViewModel = hiltViewModel()
    val historyState by historyViewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    // 鏂版秷鎭垨 streaming 杩囩▼涓窡闅忓唴瀹瑰闀挎粴鍒板簳閮?
    LaunchedEffect(state.messages.size, state.isStreaming) {
        if (state.messages.isNotEmpty()) {
            if (state.isStreaming) {
                snapshotFlow { state.messages.size }
                    .collect { listState.scrollToItem(maxOf(0, it - 1)) }
            } else {
                listState.scrollToItem(state.messages.size - 1)
            }
        }
    }

    // toolName 鏉ヨ嚜涓婁紶鍗＄墖鏃堕潪绌猴紱鏉ヨ嚜杈撳叆妗嗛檮浠舵寜閽椂涓虹┖涓?
    var pendingPickerToolName by remember { mutableStateOf("") }

    // 浣跨敤 GetContent 鑰岄潪 OpenDocument 鈥?MIUI 瀵?ACTION_GET_CONTENT 鍏煎鎬ф洿濂?
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        android.util.Log.d("AgentChatScreen", "鏂囦欢閫夋嫨鍣ㄥ洖璋? uri=$uri, pendingToolName=$pendingPickerToolName")
        if (uri == null) {
            android.util.Log.w("AgentChatScreen", "鏂囦欢閫夋嫨鍣ㄨ繑鍥?null (鐢ㄦ埛鍙栨秷鎴?MIUI 鎶曢€掑け璐?")
            return@rememberLauncherForActivityResult
        }
        val mimeType = context.contentResolver.getType(uri)
        var fileName = "鏂囦欢"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) fileName = cursor.getString(nameIndex)
        }
        viewModel.attachFile(context, uri, mimeType, fileName, fromCardToolName = pendingPickerToolName)
        pendingPickerToolName = ""
    }

    // 鏀堕泦鏂囦欢閫夋嫨鍣ㄨЕ鍙戜簨浠讹紱toolName 闈炵┖璇存槑鏉ヨ嚜涓婁紶鍗＄墖
    LaunchedEffect(Unit) {
        viewModel.openFilePicker.collect { toolName ->
            android.util.Log.d("AgentChatScreen", "openFilePicker 鏀跺埌: toolName=$toolName")
            pendingPickerToolName = toolName
            filePickerLauncher.launch("*/*")
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AgentDrawerContent(
                historyItems = historyState.items,
                onOpenSettings = {
                    drawerScope.launch {
                        drawerState.close()
                        onNavigateToSettings()
                    }
                },
                onOpenHistoryRecord = { sessionId ->
                    drawerScope.launch {
                        drawerState.close()
                        onNavigateToHistoryRecord(sessionId)
                    }
                },
                onOpenJdList = {
                    drawerScope.launch {
                        drawerState.close()
                        onNavigateToJdList()
                    }
                },
                onOpenResumeLibrary = {
                    drawerScope.launch {
                        drawerState.close()
                        onNavigateToResumeLibrary()
                    }
                }
            )
        }
    ) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("TieLink", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = {
                    IconButton(onClick = { drawerScope.launch { drawerState.open() } }) {
                        Icon(Icons.Default.Menu, contentDescription = "打开侧边栏")
                    }
                },
                                actions = {
                    IconButton(onClick = onNavigateToJdList) {
                        Icon(Icons.Default.Work, contentDescription = "JD 库")
                    }
                    IconButton(onClick = onNavigateToResumeLibrary) {
                        Icon(Icons.Outlined.Description, contentDescription = "简历库")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            Column {
                QuickActionsBar(
                    onResumeOptimize = onNavigateToResumeOptimize,
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
                        pendingPickerToolName = ""   // 杈撳叆妗嗛檮浠舵寜閽細鎵嬪姩妯″紡
                        filePickerLauncher.launch("*/*")
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
                                Icon(Icons.Default.Close, contentDescription = "鍏抽棴",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (state.messages.isEmpty()) {
                    // 鈹€鈹€ 娆㈣繋椤碉紙Gemini 椋庢牸锛夆攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
                    WelcomePage(
                        prompts = state.suggestedPrompts,
                        onPromptClick = { viewModel.sendPrompt(it) }
                    )
                } else {
                    // 鈹€鈹€ 鑱婂ぉ瑙嗗浘 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
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
                            MessageRow(message = message, onNavigateToResumePreview = onNavigateToResumePreview)
                        }

                        item { Spacer(modifier = Modifier.height(4.dp)) }
                    }

                    // 搴曢儴娓愬彉閬僵
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

// 鈹€鈹€鈹€ Welcome page 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

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
        // 涓婃柟 30% 鐣欑櫧锛岃鍐呭钀藉湪瑙嗚涓亸涓婁綅缃?
        Spacer(Modifier.fillMaxSize(0.15f))

//        // 搴旂敤鍥炬爣
//        androidx.compose.foundation.Image(
//            painter = painterResource(R.mipmap.ic_launcher_round),
//            contentDescription = null,
//            modifier = Modifier.size(52.dp)
//        )

//        Spacer(Modifier.height(16.dp))

        Text(
            text = "鎴戞槸 TieLink",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = "鍒嗘瀽鍖归厤搴︺€佷紭鍖栫畝鍘嗐€佽拷韪姇閫?鈥?閮藉彲浠ョ洿鎺ヨ窡鎴戣",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        // 寤鸿 chips 鈥?鍗曞垪锛屽搴﹁嚜閫傚簲鏂囧瓧
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

// 鈹€鈹€鈹€ Message row 鈥?dispatches to the right layout 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

@Composable
private fun MessageRow(message: AgentMessage, onNavigateToResumePreview: (Long) -> Unit = {}) {
    when {
        // Tool loading placeholder
        message.toolLoadingName != null -> ToolLoadingBubble(message)
        // Rich card (from tool result)
        message.card != null -> {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                UiCardComposable(card = message.card, modifier = Modifier.fillMaxWidth(), onNavigateToResumePreview = onNavigateToResumePreview)
            }
        }
        // User bubble
        message.role == AgentMessageRole.USER -> UserBubble(message)
        // Agent bubble (text, with optional thinking panel)
        else -> AgentBubble(message)
    }
}

// 鈹€鈹€鈹€ Agent bubble 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

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
                            text = message.content + if (message.isStreaming) "..." else "",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

// 鈹€鈹€鈹€ User bubble 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

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

// 鈹€鈹€鈹€ Tool loading bubble 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

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

// 鈹€鈹€鈹€ Collapsible thinking panel 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

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
                    text = if (isStreaming) "思考中..." else "思考完成",
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

// 鈹€鈹€鈹€ Animated dots (waiting for first token) 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

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

// 鈹€鈹€鈹€ Bottom bar components 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

@Composable
private fun QuickActionsBar(
    onResumeOptimize: () -> Unit,
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
                Text("姝ｅ湪瑙ｆ瀽鏂囦欢...", style = MaterialTheme.typography.bodySmall,
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
    // remember 閬垮厤姣忓抚閲嶇粍鏃堕噸鏂板垱寤哄垪琛?
    val gradientColors = remember {
        listOf(
            Color(0xFF6C63FF), // 绱?
            Color(0xFF3B82F6), // 钃?
            Color(0xFF06B6D4), // 闈?
            Color(0xFF10B981)  // 缁?
        )
    }
    val inputShape = remember { RoundedCornerShape(28.dp) }

    // 鎮诞瀹瑰櫒锛歩mePadding 宸查€愬抚璺熼殢 IME inset 鍔ㄧ敾骞虫粦涓婄Щ
    // 娉ㄦ剰锛氫笉瑕佸啀鍙?animateContentSize() 鈥斺€?浼氬拰 imePadding 鐨勭郴缁熷姩鐢讳簰鐩告墦鏋堕€犳垚鍗￠】
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(bottom = 20.dp)
    ) {
        // 杈撳叆妗嗘湰浣?
        // 鍏抽敭浼樺寲锛?
        // 1. shadow 鍘婚櫎鑷畾涔夐鑹?鈫?GPU RenderNode 纭欢鍔犻€燂紙鑷畾涔夐鑹蹭細鍥為€€鍒拌蒋浠舵覆鏌擄級
        // 2. elevation 浠?24dp 闄嶈嚦 12dp锛屽噺灏戦槾褰辫绠楅潰绉?
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .shadow(
                    elevation = 12.dp,
                    shape = inputShape
                )
                .border(
                    width = 2.dp,
                    brush = Brush.horizontalGradient(gradientColors),
                    shape = inputShape
                )
                .clip(inputShape)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        if (hasAttachment) "娣诲姞璇存槑锛堝彲閫夛級..." else "璇寸偣浠€涔?..",
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
                            contentDescription = "涓婁紶鏂囦欢",
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

// 鈹€鈹€鈹€ Previews 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun AgentBubblePreview() {
    TieLinkTheme {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AgentBubble(
                AgentMessage(
                    role = AgentMessageRole.AGENT,
                    content = "我先帮你看一下简历和岗位要求。",
                    thinkingContent = "正在匹配关键技能"
                )
            )
            AgentBubble(
                AgentMessage(
                    role = AgentMessageRole.AGENT,
                    content = "建议补充项目结果和量化描述。",
                    thinkingContent = null
                )
            )
            AgentBubble(
                AgentMessage(
                    role = AgentMessageRole.AGENT,
                    content = "",
                    isStreaming = true
                )
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun UserBubblePreview() {
    TieLinkTheme {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            UserBubble(
                AgentMessage(
                    role = AgentMessageRole.USER,
                    content = "帮我优化简历"
                )
            )
            UserBubble(
                AgentMessage(
                    role = AgentMessageRole.USER,
                    content = "我想应聘 Android 开发岗位"
                )
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ThinkingPanelPreview() {
    TieLinkTheme {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ThinkingPanel(
                thinkingContent = "正在分析简历与 JD 的匹配度...",
                isStreaming = true
            )
            ThinkingPanel(
                thinkingContent = "已完成初步分析，建议补充项目成果。",
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
                text = "帮我优化简历中的项目经历",
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
        ToolLoadingBubble(
            AgentMessage(
                role = AgentMessageRole.AGENT,
                content = "正在分析简历匹配度...",
                toolLoadingName = "match_analysis"
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun AgentChatScreenContentPreview() {
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
                    QuickActionsBar(onResumeOptimize = {}, onTracking = {})
                    InputArea(
                        text = "帮我优化简历",
                        isStreaming = false,
                        hasAttachment = false,
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
                        UserBubble(
                            AgentMessage(
                                role = AgentMessageRole.USER,
                                content = "帮我优化简历"
                            )
                        )
                    }
                    item {
                        AgentBubble(
                            AgentMessage(
                                role = AgentMessageRole.AGENT,
                                content = "我先帮你把简历拆成关键模块，再看和目标岗位的匹配度。",
                                thinkingContent = "正在生成示例预览"
                            )
                        )
                    }
                    item { Spacer(modifier = Modifier.height(4.dp)) }
                }
            }
        }
    }
}

@Composable
private fun AgentDrawerContent(
    historyItems: List<com.example.tielink.domain.model.HistoryItem>,
    onOpenSettings: () -> Unit,
    onOpenHistoryRecord: (Long) -> Unit,
    onOpenJdList: () -> Unit,
    onOpenResumeLibrary: () -> Unit
) {
    ModalDrawerSheet {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "聊天记录",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "点一下就能回到上次的记录，或者切换到别的历史会话。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (historyItems.isNotEmpty()) {
                val latest = historyItems.first()
                DrawerActionCard(
                    title = "继续上次记录",
                    subtitle = latest.jdTitle,
                    onClick = { onOpenHistoryRecord(latest.id) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            Text(
                text = "最近记录",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (historyItems.isEmpty()) {
                Text(
                    text = "还没有历史记录。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                historyItems.take(6).forEach { item ->
                    DrawerHistoryRow(
                        title = item.jdTitle,
                        subtitle = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(item.createdAt)),
                        onClick = { onOpenHistoryRecord(item.id) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider()
            Spacer(modifier = Modifier.height(12.dp))

            DrawerActionCard(
                title = "模型配置",
                subtitle = "切换 DeepSeek、Ollama 或本地模型",
                onClick = onOpenSettings
            )
            Spacer(modifier = Modifier.height(8.dp))
            DrawerActionCard(
                title = "历史总览",
                subtitle = "查看全部润色记录",
                onClick = onOpenJdList
            )
            Spacer(modifier = Modifier.height(8.dp))
            DrawerActionCard(
                title = "简历库",
                subtitle = "打开已保存的简历版本",
                onClick = onOpenResumeLibrary
            )
        }
    }
}

@Composable
private fun DrawerActionCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.Default.ArrowForward, contentDescription = null)
        }
    }
}

@Composable
private fun DrawerHistoryRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
    }
}
