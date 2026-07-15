package com.example.tielink.ui.agent

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.tielink.domain.model.AgentUiAction
import com.example.tielink.domain.model.AgentMessageRole
import com.example.tielink.domain.model.isAgentChat
import com.example.tielink.ui.history.HistoryViewModel
import com.example.tielink.ui.theme.AppSpacing
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentChatScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToResumeOptimize: () -> Unit,
    onNavigateToInterview: () -> Unit,
    onNavigateToDebrief: () -> Unit,
    onNavigateToTracking: () -> Unit,
    onNavigateToHistoryRecord: (Long) -> Unit = {},
    onNavigateToJdList: () -> Unit = {},
    onNavigateToJdListForChoice: () -> Unit = {},
    onNavigateToResumeLibrary: () -> Unit = {},
    onNavigateToResumeLibraryForChoice: () -> Unit = {},
    onNavigateToResumePreview: (Long) -> Unit = {},
    initialHistoryId: Long? = null,
    initialPrompt: String? = null,
    viewModel: AgentViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val historyViewModel: HistoryViewModel = hiltViewModel()
    val historyState by historyViewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val inlineProcessMessageId = state.messages.lastOrNull {
        it.role == AgentMessageRole.AGENT && it.card == null && it.toolLoadingName == null
    }?.id

    LaunchedEffect(initialHistoryId) {
        initialHistoryId?.let(viewModel::openHistorySession)
    }

    var consumedInitialPrompt by rememberSaveable(initialPrompt) { mutableStateOf(false) }
    LaunchedEffect(initialPrompt) {
        val prompt = initialPrompt?.trim().orEmpty()
        if (prompt.isNotBlank() && !consumedInitialPrompt) {
            consumedInitialPrompt = true
            viewModel.sendPrompt(prompt)
        }
    }

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshContextBar()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

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

    var pendingPickerToolName by remember { mutableStateOf("") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        android.util.Log.d("AgentChatScreen", "文件选择器回调 uri=$uri, pendingToolName=$pendingPickerToolName")
        if (uri == null) {
            android.util.Log.w("AgentChatScreen", "文件选择器返回 null")
            return@rememberLauncherForActivityResult
        }
        val mimeType = context.contentResolver.getType(uri)
        var fileName = "文件"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) fileName = cursor.getString(nameIndex)
        }
        viewModel.attachFile(context, uri, mimeType, fileName, fromCardToolName = pendingPickerToolName)
        pendingPickerToolName = ""
    }

    LaunchedEffect(Unit) {
        viewModel.openFilePicker.collect { toolName ->
            android.util.Log.d("AgentChatScreen", "openFilePicker: toolName=$toolName")
            pendingPickerToolName = toolName
            filePickerLauncher.launch("*/*")
        }
    }

    LaunchedEffect(Unit) {
        viewModel.uiActions.collect { action ->
            when (action) {
                is AgentUiAction.SendPrompt -> viewModel.sendPrompt(action.prompt)
                AgentUiAction.OpenJdLibrary -> onNavigateToJdListForChoice()
                AgentUiAction.OpenResumeLibrary -> onNavigateToResumeLibraryForChoice()
                AgentUiAction.UploadResume -> {
                    pendingPickerToolName = "resume_tool"
                    filePickerLauncher.launch("*/*")
                }
                AgentUiAction.OpenTracking -> onNavigateToTracking()
                AgentUiAction.OpenResumeOptimize -> onNavigateToResumeOptimize()
                AgentUiAction.OpenSettings -> onNavigateToSettings()
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AgentDrawerContent(
                historyState = historyState,
                onSearchQueryChange = historyViewModel::updateSearchQuery,
                onDateFilterChange = historyViewModel::setDateFilter,
                onToggleBulkMode = historyViewModel::setBulkMode,
                onToggleSelection = historyViewModel::toggleSelection,
                onSelectAll = historyViewModel::selectAllFiltered,
                onClearSelection = historyViewModel::clearSelection,
                onRenameHistory = historyViewModel::renameItem,
                onDeleteHistory = historyViewModel::deleteItem,
                onDeleteSelected = historyViewModel::deleteSelected,
                onPinHistory = historyViewModel::setPinned,
                onPinSelected = historyViewModel::setPinnedSelected,
                onExportHistory = historyViewModel::exportItem,
                onClearAllHistory = historyViewModel::clearAll,
                onCreateBranch = { item ->
                    drawerScope.launch {
                        drawerState.close()
                        viewModel.startNewSession(historyViewModel.buildBranchPrompt(item))
                    }
                },
                onCreateNewSession = {
                    drawerScope.launch {
                        drawerState.close()
                        viewModel.startNewSession()
                    }
                },
                onOpenResumeOptimize = {
                    drawerScope.launch {
                        drawerState.close()
                        onNavigateToResumeOptimize()
                    }
                },
                onOpenInterview = {
                    drawerScope.launch {
                        drawerState.close()
                        onNavigateToInterview()
                    }
                },
                onOpenDebrief = {
                    drawerScope.launch {
                        drawerState.close()
                        onNavigateToDebrief()
                    }
                },
                onOpenTracking = {
                    drawerScope.launch {
                        drawerState.close()
                        onNavigateToTracking()
                    }
                },
                onOpenSettings = {
                    drawerScope.launch {
                        drawerState.close()
                        onNavigateToSettings()
                    }
                },
                onOpenHistoryRecord = { sessionId ->
                    drawerScope.launch {
                        drawerState.close()
                        val item = historyState.items.firstOrNull { it.id == sessionId }
                        if (item?.isAgentChat == true) {
                            viewModel.openHistorySession(sessionId)
                        } else {
                            onNavigateToHistoryRecord(sessionId)
                        }
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.72f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "TieLink",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    if (state.contextBar.jdTitle != null || state.contextBar.resumeVersionName != null) {
                                        "Career Agent · 上下文已连接"
                                    } else {
                                        "Career Agent · 等待上下文"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
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
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                        )
                    )
                },
                bottomBar = {
                    Column(
                        modifier = Modifier.background(MaterialTheme.colorScheme.background.copy(alpha = 0.97f))
                    ) {
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
                                pendingPickerToolName = ""
                                filePickerLauncher.launch("*/*")
                            },
                            onVoiceInput = viewModel::sendVoiceInput,
                            onVoiceError = viewModel::setError
                        )
                    }
                }
            ) { paddingValues ->
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                    if (state.messages.isEmpty()) {
                        WelcomePage(
                            contextBar = state.contextBar,
                            prompts = state.suggestedPrompts,
                            onPromptClick = { viewModel.sendPrompt(it) },
                            onOpenJd = onNavigateToJdListForChoice,
                            onOpenResume = onNavigateToResumeLibrary,
                            onUploadResume = {
                                pendingPickerToolName = "resume_tool"
                                filePickerLauncher.launch("*/*")
                            },
                            onOpenTracking = onNavigateToTracking
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().padding(horizontal = AppSpacing.md),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
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
                                MessageRow(
                                    message = message,
                                    inlineProcessState = if (message.id == inlineProcessMessageId) {
                                        state.processState
                                    } else {
                                        null
                                    },
                                    onCancelInlineProcess = { viewModel.cancelStream() },
                                    onNavigateToResumePreview = onNavigateToResumePreview,
                                    onNavigateToResumeLibrary = onNavigateToResumeLibraryForChoice,
                                    onRequestResumeUpload = { viewModel.requestFilePicker("resume_tool") },
                                    onDynamicAction = viewModel::runDynamicCardAction
                                )
                            }

                            item { Spacer(modifier = Modifier.height(4.dp)) }
                        }

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

                    AnimatedVisibility(
                        visible = state.error != null,
                        modifier = Modifier.align(Alignment.TopCenter),
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        state.error?.let { error ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                                    Text(
                                        error,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.align(Alignment.CenterStart)
                                    )
                                    IconButton(
                                        onClick = { viewModel.dismissError() },
                                        modifier = Modifier.size(24.dp).align(Alignment.CenterEnd)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "关闭",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
