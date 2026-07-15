package com.example.tielink.ui.interview

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tielink.data.local.db.entity.JdLibraryEntity
import com.example.tielink.domain.model.InterviewMessage
import com.example.tielink.domain.model.InterviewPersona
import com.example.tielink.domain.model.MessageRole
import com.example.tielink.domain.model.ResumeVersion
import com.example.tielink.ui.components.AppStatusPill
import com.example.tielink.ui.components.VoiceInputIconButton
import com.example.tielink.ui.theme.ActionBlue
import com.example.tielink.ui.theme.AppSpacing
import com.example.tielink.ui.theme.FocusCyan
import com.example.tielink.ui.theme.MatchGreen
import com.example.tielink.ui.theme.MissRed
import com.example.tielink.ui.theme.SignalMint
import com.example.tielink.ui.theme.TextSecondary
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InterviewScreen(
    onNavigateBack: () -> Unit,
    viewModel: InterviewViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val voiceController = rememberInterviewVoiceController(state.selectedPersona)
    val latestSpeakEnabled by rememberUpdatedState(state.isSpeakerEnabled)
    val latestMessages by rememberUpdatedState(state.messages)
    val transcriptListState = rememberLazyListState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSettings by remember { mutableStateOf(false) }

    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var cameraError by remember { mutableStateOf<String?>(null) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraGranted = granted
        cameraError = if (granted) null else "需要摄像头权限才能进入视频面试画面。"
    }

    LaunchedEffect(Unit) {
        if (!cameraGranted) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(latestMessages, latestSpeakEnabled, voiceController.isReady) {
        if (!latestSpeakEnabled || !voiceController.isReady) return@LaunchedEffect
        val latestInterviewerMessage = latestMessages.lastOrNull { it.role == MessageRole.INTERVIEWER } ?: return@LaunchedEffect
        voiceController.speakInterviewerMessage(latestInterviewerMessage)
    }

    LaunchedEffect(state.messages.size, state.liveUserTranscript, state.isUserListening) {
        val itemCount = state.messages.size + if (state.liveUserTranscript.isNotBlank()) 1 else 0
        if (itemCount > 0) {
            transcriptListState.animateScrollToItem(itemCount - 1)
        }
    }

    DisposableEffect(Unit) {
        onDispose { voiceController.stop() }
    }

    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            InterviewSettingsSheet(
                state = state,
                onSelectResume = viewModel::selectResume,
                onSelectJd = viewModel::selectJd,
                onSelectPersona = viewModel::selectPersona,
                onPressureChange = viewModel::updatePressureLevel,
                onFollowUpDepthChange = viewModel::updateFollowUpDepth,
                onFundamentalsWeightChange = viewModel::updateFundamentalsWeight,
                onProjectWeightChange = viewModel::updateProjectWeight,
                onAlgorithmWeightChange = viewModel::updateAlgorithmWeight,
                onSystemDesignWeightChange = viewModel::updateSystemDesignWeight,
                onBehavioralWeightChange = viewModel::updateBehavioralWeight,
                onToggleInstantFeedback = viewModel::toggleInstantFeedback,
                onDismiss = { showSettings = false }
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (cameraGranted && state.isCameraEnabled) {
            CameraPreview(modifier = Modifier.fillMaxSize())
        } else {
            FullScreenCameraPlaceholder(
                cameraGranted = cameraGranted,
                cameraEnabled = state.isCameraEnabled,
                cameraError = cameraError,
                onRequestCamera = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to Color.Black.copy(alpha = 0.62f),
                            0.26f to Color.Transparent,
                            0.58f to Color.Transparent,
                            1.00f to Color.Black.copy(alpha = 0.76f)
                        )
                    )
                )
        )

        FullScreenTopBar(
            state = state,
            onNavigateBack = onNavigateBack,
            onOpenSettings = { showSettings = true }
        )

        LiveCaptionLayer(
            state = state,
            listState = transcriptListState,
            onReplayVoice = { message -> voiceController.forceSpeak(message) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = AppSpacing.md)
                .padding(bottom = 132.dp)
        )

        FullScreenControls(
            state = state,
            onToggleMic = viewModel::toggleMic,
            onToggleSpeaker = viewModel::toggleSpeaker,
            onToggleCamera = viewModel::toggleCamera,
            onPrimaryAction = {
                if (state.isInCall) viewModel.endInterview() else viewModel.startInterview()
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.md)
        )

        FloatingAnswerBar(
            state = state,
            draft = state.draftAnswer,
            onDraftChange = viewModel::updateDraftAnswer,
            onSend = viewModel::submitAnswer,
            onVoiceRecognized = viewModel::submitRecognizedAnswer,
            onVoicePartial = viewModel::updateLiveUserTranscript,
            onVoiceListeningChanged = viewModel::setUserListening,
            onVoiceError = viewModel::reportVoiceCaptureError,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = AppSpacing.md)
                .padding(bottom = 86.dp)
        )
    }
}

@Composable
private fun FullScreenTopBar(
    state: InterviewUiState,
    onNavigateBack: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            FilledIconButton(onClick = onNavigateBack, modifier = Modifier.size(42.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Column {
                Text(
                    text = state.selectedPersona.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = state.interviewTargetLabel(),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
            AppStatusPill(
                text = if (state.isInCall) "通话中 ${formatElapsed(state.elapsedSeconds)}" else "待接通",
                color = if (state.isInCall) MatchGreen else FocusCyan
            )
            FilledIconButton(onClick = onOpenSettings, modifier = Modifier.size(42.dp)) {
                Icon(Icons.Default.Settings, contentDescription = "面试设置")
            }
        }
    }
}

@Composable
private fun LiveCaptionLayer(
    state: InterviewUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onReplayVoice: (InterviewMessage) -> Unit,
    modifier: Modifier = Modifier
) {
    val hasContent = state.messages.isNotEmpty() || state.liveUserTranscript.isNotBlank()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp, max = 260.dp),
        shape = RoundedCornerShape(8.dp),
        color = Color.Black.copy(alpha = 0.38f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
    ) {
        if (!hasContent) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppSpacing.md),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "开始面试后，面试官和你的对话会实时显示在这里",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.82f),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = AppSpacing.xs)
            ) {
                items(state.messages, key = { it.id.takeIf { id -> id != 0L } ?: it.timestamp }) { message ->
                    CaptionRow(
                        message = message,
                        onReplayVoice = { onReplayVoice(message) }
                    )
                }
                if (state.liveUserTranscript.isNotBlank()) {
                    item(key = "live-user-transcript") {
                        LiveUserCaption(
                            transcript = state.liveUserTranscript,
                            isListening = state.isUserListening
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CaptionRow(
    message: InterviewMessage,
    onReplayVoice: () -> Unit
) {
    val accent = when (message.role) {
        MessageRole.USER -> ActionBlue
        MessageRole.INTERVIEWER -> SignalMint
        MessageRole.SYSTEM -> FocusCyan
    }
    val roleLabel = when (message.role) {
        MessageRole.USER -> "我"
        MessageRole.INTERVIEWER -> "面试官"
        MessageRole.SYSTEM -> "系统"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = accent.copy(alpha = 0.22f)
        ) {
            Text(
                text = roleLabel,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            text = message.content,
            modifier = Modifier.weight(1f).padding(top = 2.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White
        )
        if (message.role == MessageRole.INTERVIEWER) {
            IconButton(onClick = onReplayVoice, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "重播提问",
                    tint = Color.White.copy(alpha = 0.84f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun LiveUserCaption(
    transcript: String,
    isListening: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = ActionBlue.copy(alpha = 0.30f)
        ) {
            Text(
                text = if (isListening) "我 · 说话中" else "我 · 待提交",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            text = transcript,
            modifier = Modifier.weight(1f).padding(top = 2.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White
        )
    }
}

@Composable
private fun FloatingAnswerBar(
    state: InterviewUiState,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onVoiceRecognized: (String) -> Unit,
    onVoicePartial: (String) -> Unit,
    onVoiceListeningChanged: (Boolean) -> Unit,
    onVoiceError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color.Black.copy(alpha = 0.42f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
        ) {
            VoiceInputIconButton(
                onTextRecognized = onVoiceRecognized,
                onError = onVoiceError,
                onPartialText = onVoicePartial,
                onListeningStateChange = onVoiceListeningChanged,
                enabled = state.isInCall && state.isMicEnabled,
                prompt = "请直接回答当前面试问题"
            )
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                enabled = state.isInCall,
                placeholder = {
                    Text(
                        text = when {
                            !state.isInCall -> "开始面试后可以语音或文字回答"
                            state.isUserListening -> "正在实时转写..."
                            else -> "说话会实时变成字幕，也可以手动输入"
                        },
                        color = Color.White.copy(alpha = 0.62f)
                    )
                }
            )
            IconButton(onClick = onSend, enabled = state.isInCall && draft.isNotBlank()) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "提交回答",
                    tint = if (draft.isNotBlank()) Color.White else Color.White.copy(alpha = 0.34f)
                )
            }
        }
    }
}

@Composable
private fun FullScreenControls(
    state: InterviewUiState,
    onToggleMic: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleCamera: () -> Unit,
    onPrimaryAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoundCallButton(
            icon = if (state.isMicEnabled) Icons.Default.Mic else Icons.Default.MicOff,
            label = if (state.isMicEnabled) "麦克风" else "静音",
            tint = if (state.isMicEnabled) FocusCyan else TextSecondary,
            onClick = onToggleMic
        )
        RoundCallButton(
            icon = if (state.isSpeakerEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
            label = if (state.isSpeakerEnabled) "播报" else "静音",
            tint = if (state.isSpeakerEnabled) SignalMint else TextSecondary,
            onClick = onToggleSpeaker
        )
        RoundCallButton(
            icon = if (state.isCameraEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
            label = if (state.isCameraEnabled) "摄像头" else "关闭",
            tint = if (state.isCameraEnabled) ActionBlue else TextSecondary,
            onClick = onToggleCamera
        )
        RoundCallButton(
            icon = if (state.isInCall) Icons.Default.CallEnd else Icons.Default.Cameraswitch,
            label = if (state.isInCall) "结束" else "开始",
            tint = if (state.isInCall) MissRed else MatchGreen,
            onClick = onPrimaryAction
        )
    }
}

@Composable
private fun RoundCallButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Surface(
            onClick = onClick,
            modifier = Modifier.size(52.dp),
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.44f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
            }
        }
        Text(label, color = Color.White.copy(alpha = 0.84f), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun InterviewSettingsSheet(
    state: InterviewUiState,
    onSelectResume: (Long?) -> Unit,
    onSelectJd: (Long?) -> Unit,
    onSelectPersona: (InterviewPersona) -> Unit,
    onPressureChange: (Float) -> Unit,
    onFollowUpDepthChange: (Float) -> Unit,
    onFundamentalsWeightChange: (Float) -> Unit,
    onProjectWeightChange: (Float) -> Unit,
    onAlgorithmWeightChange: (Float) -> Unit,
    onSystemDesignWeightChange: (Float) -> Unit,
    onBehavioralWeightChange: (Float) -> Unit,
    onToggleInstantFeedback: () -> Unit,
    onDismiss: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.86f)
            .navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("面试设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("选择上下文并调节面试官风格", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "关闭")
                }
            }
        }

        item {
            SelectionSection(
                state = state,
                onSelectResume = onSelectResume,
                onSelectJd = onSelectJd
            )
        }

        item {
            PersonaSection(
                selectedPersona = state.selectedPersona,
                enabled = !state.isInCall,
                onSelectPersona = onSelectPersona
            )
        }

        item {
            InterviewerTuningSection(
                pressureLevel = state.pressureLevel,
                followUpDepth = state.followUpDepth,
                fundamentalsWeight = state.fundamentalsWeight,
                projectWeight = state.projectWeight,
                algorithmWeight = state.algorithmWeight,
                systemDesignWeight = state.systemDesignWeight,
                behavioralWeight = state.behavioralWeight,
                instantFeedbackEnabled = state.instantFeedbackEnabled,
                onPressureChange = onPressureChange,
                onFollowUpDepthChange = onFollowUpDepthChange,
                onFundamentalsWeightChange = onFundamentalsWeightChange,
                onProjectWeightChange = onProjectWeightChange,
                onAlgorithmWeightChange = onAlgorithmWeightChange,
                onSystemDesignWeightChange = onSystemDesignWeightChange,
                onBehavioralWeightChange = onBehavioralWeightChange,
                onToggleInstantFeedback = onToggleInstantFeedback
            )
        }
    }
}

@Composable
private fun SelectionSection(
    state: InterviewUiState,
    onSelectResume: (Long?) -> Unit,
    onSelectJd: (Long?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        Text("面试上下文", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        OptionDropdown(
            title = "岗位",
            icon = Icons.Default.Work,
            selectedLabel = state.selectedJdLabel(),
            emptyLabel = "使用最近岗位",
            items = state.jds,
            itemLabel = { it.optionLabel() },
            onSelect = { onSelectJd(it?.id) }
        )
        OptionDropdown(
            title = "简历",
            icon = Icons.Default.Description,
            selectedLabel = state.selectedResumeLabel(),
            emptyLabel = "使用最近简历",
            items = state.resumes,
            itemLabel = { it.optionLabel() },
            onSelect = { onSelectResume(it?.id) }
        )
    }
}

@Composable
private fun PersonaSection(
    selectedPersona: InterviewPersona,
    enabled: Boolean,
    onSelectPersona: (InterviewPersona) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        Text("面试官", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
        ) {
            InterviewPersona.entries.forEach { persona ->
                FilterChip(
                    selected = persona == selectedPersona,
                    onClick = { onSelectPersona(persona) },
                    enabled = enabled,
                    label = { Text(persona.displayName) },
                    leadingIcon = if (persona == selectedPersona) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else {
                        null
                    }
                )
            }
        }
        Text(selectedPersona.description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

@Composable
private fun InterviewerTuningSection(
    pressureLevel: Float,
    followUpDepth: Float,
    fundamentalsWeight: Float,
    projectWeight: Float,
    algorithmWeight: Float,
    systemDesignWeight: Float,
    behavioralWeight: Float,
    instantFeedbackEnabled: Boolean,
    onPressureChange: (Float) -> Unit,
    onFollowUpDepthChange: (Float) -> Unit,
    onFundamentalsWeightChange: (Float) -> Unit,
    onProjectWeightChange: (Float) -> Unit,
    onAlgorithmWeightChange: (Float) -> Unit,
    onSystemDesignWeightChange: (Float) -> Unit,
    onBehavioralWeightChange: (Float) -> Unit,
    onToggleInstantFeedback: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
        Text("调节", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        TuningSlider(
            label = "压力强度",
            value = pressureLevel,
            lowLabel = "温和",
            highLabel = "高压",
            onValueChange = onPressureChange
        )
        TuningSlider(
            label = "追问深度",
            value = followUpDepth,
            lowLabel = "概要",
            highLabel = "深挖",
            onValueChange = onFollowUpDepthChange
        )
        Text("考察重点", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        TuningSlider(
            label = "八股基础",
            value = fundamentalsWeight,
            lowLabel = "少问",
            highLabel = "多问",
            onValueChange = onFundamentalsWeightChange
        )
        TuningSlider(
            label = "项目经历",
            value = projectWeight,
            lowLabel = "少问",
            highLabel = "多问",
            onValueChange = onProjectWeightChange
        )
        TuningSlider(
            label = "算法题",
            value = algorithmWeight,
            lowLabel = "少问",
            highLabel = "多问",
            onValueChange = onAlgorithmWeightChange
        )
        TuningSlider(
            label = "系统设计",
            value = systemDesignWeight,
            lowLabel = "少问",
            highLabel = "多问",
            onValueChange = onSystemDesignWeightChange
        )
        TuningSlider(
            label = "行为面",
            value = behavioralWeight,
            lowLabel = "少问",
            highLabel = "多问",
            onValueChange = onBehavioralWeightChange
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("即时点评", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text("开启后追问会更直接指出刚才回答的问题", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Switch(checked = instantFeedbackEnabled, onCheckedChange = { onToggleInstantFeedback() })
        }
    }
}

@Composable
private fun TuningSlider(
    label: String,
    value: Float,
    lowLabel: String,
    highLabel: String,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text("${(value * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        }
        Slider(value = value, onValueChange = onValueChange)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(lowLabel, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(highLabel, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
    }
}

@Composable
private fun <T> OptionDropdown(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selectedLabel: String,
    emptyLabel: String,
    items: List<T>,
    itemLabel: (T) -> String,
    onSelect: (T?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(AppSpacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Text(selectedLabel, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = { expanded = true }) { Text("选择") }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(emptyLabel) },
                    onClick = {
                        onSelect(null)
                        expanded = false
                    }
                )
                items.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(itemLabel(item), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        onClick = {
                            onSelect(item)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FullScreenCameraPlaceholder(
    cameraGranted: Boolean,
    cameraEnabled: Boolean,
    cameraError: String?,
    onRequestCamera: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF101820), Color(0xFF22313F), Color(0xFF0A0D10))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            modifier = Modifier.padding(AppSpacing.xl)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (cameraEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
            Text(
                text = if (!cameraGranted) "允许摄像头后进入全屏视频面试" else "摄像头当前已关闭",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = cameraError ?: "实时字幕和面试官语音仍会继续工作。",
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
            if (!cameraGranted) {
                TextButton(onClick = onRequestCamera) { Text("允许摄像头") }
            }
        }
    }
}

@Composable
private fun CameraPreview(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember(context) {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    LaunchedEffect(lifecycleOwner, previewView) {
        val cameraProvider = context.awaitCameraProvider()
        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }

        runCatching {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                preview
            )
        }
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            runCatching {
                ProcessCameraProvider.getInstance(context).get().unbindAll()
            }
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

private suspend fun Context.awaitCameraProvider(): ProcessCameraProvider =
    suspendCancellableCoroutine { continuation ->
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener(
            { continuation.resume(future.get()) },
            ContextCompat.getMainExecutor(this)
        )
    }

private fun InterviewUiState.interviewTargetLabel(): String {
    return listOf(companyName, positionName).filter { it.isNotBlank() }.joinToString(" · ")
        .ifBlank { "未选择岗位" }
}

private fun InterviewUiState.selectedJdLabel(): String {
    val selected = selectedJdId?.let { id -> jds.firstOrNull { it.id == id } }
    return selected?.optionLabel() ?: interviewTargetLabel()
}

private fun InterviewUiState.selectedResumeLabel(): String {
    val selected = selectedResumeId?.let { id -> resumes.firstOrNull { it.id == id } }
    return selected?.optionLabel() ?: if (hasResumeContext) "最近简历上下文" else "未选择简历"
}

private fun JdLibraryEntity.optionLabel(): String {
    return listOf(companyName, positionName).filter { it.isNotBlank() }.joinToString(" · ")
        .ifBlank { rawText.take(24).ifBlank { "未命名岗位" } }
}

private fun ResumeVersion.optionLabel(): String {
    val tagText = tags.take(2).joinToString(" / ")
    return if (tagText.isBlank()) name else "$name · $tagText"
}

private fun formatElapsed(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

private class InterviewVoiceController(context: Context) {
    private val appContext = context.applicationContext
    private var textToSpeech: TextToSpeech? = null
    private var pendingPersona: InterviewPersona = InterviewPersona.MILD_TECH
    private var lastSpokenToken: String? = null

    var isReady by mutableStateOf(false)
        private set

    fun initialize() {
        if (textToSpeech != null) return
        textToSpeech = TextToSpeech(appContext) { status ->
            isReady = status == TextToSpeech.SUCCESS
            if (isReady) {
                applyPersonaVoice(pendingPersona)
            }
        }
    }

    fun updatePersona(persona: InterviewPersona) {
        pendingPersona = persona
        if (isReady) applyPersonaVoice(persona)
    }

    fun speakInterviewerMessage(message: InterviewMessage) {
        val token = "${message.id}-${message.timestamp}-${message.content.hashCode()}"
        if (token == lastSpokenToken) return
        lastSpokenToken = token
        speak(message.content, token)
    }

    fun forceSpeak(message: InterviewMessage) {
        val token = "manual-${message.id}-${message.timestamp}-${System.currentTimeMillis()}"
        lastSpokenToken = token
        speak(message.content, token)
    }

    fun stop() {
        textToSpeech?.stop()
    }

    fun shutdown() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        isReady = false
        lastSpokenToken = null
    }

    private fun speak(text: String, utteranceId: String) {
        val tts = textToSpeech ?: return
        if (!isReady || text.isBlank()) return
        tts.stop()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), utteranceId)
        } else {
            @Suppress("DEPRECATION")
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null)
        }
    }

    private fun applyPersonaVoice(persona: InterviewPersona) {
        val tts = textToSpeech ?: return
        when (persona) {
            InterviewPersona.FOREIGN_HR -> {
                tts.language = Locale.US
                tts.setSpeechRate(0.92f)
                tts.setPitch(1.0f)
            }
            InterviewPersona.PRESSURE -> {
                tts.language = Locale.SIMPLIFIED_CHINESE
                tts.setSpeechRate(1.06f)
                tts.setPitch(0.92f)
            }
            InterviewPersona.STATE_STRUCTURED -> {
                tts.language = Locale.SIMPLIFIED_CHINESE
                tts.setSpeechRate(0.94f)
                tts.setPitch(0.96f)
            }
            InterviewPersona.MILD_TECH -> {
                tts.language = Locale.SIMPLIFIED_CHINESE
                tts.setSpeechRate(0.96f)
                tts.setPitch(1.04f)
            }
            InterviewPersona.CUSTOM -> {
                tts.language = Locale.SIMPLIFIED_CHINESE
                tts.setSpeechRate(0.98f)
                tts.setPitch(1.0f)
            }
        }
    }
}

@Composable
private fun rememberInterviewVoiceController(persona: InterviewPersona): InterviewVoiceController {
    val context = LocalContext.current
    val controller = remember(context) { InterviewVoiceController(context) }

    DisposableEffect(controller) {
        controller.initialize()
        onDispose { controller.shutdown() }
    }

    LaunchedEffect(controller, persona) {
        controller.updatePersona(persona)
    }

    return controller
}
