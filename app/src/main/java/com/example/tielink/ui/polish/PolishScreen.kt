package com.example.tielink.ui.polish

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tielink.ui.components.ErrorBanner
import com.example.tielink.ui.theme.TieLinkTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PolishScreen(
    onNavigateBack: () -> Unit,
    onPolishSuccess: (sessionId: Long) -> Unit,
    viewModel: PolishViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("正在润色") },
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
        ) {
            // ── Loading state: 步骤进度 + 进度条 ──────────
            AnimatedVisibility(
                visible = uiState.state is PolishState.Loading,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(12.dp))

                    // 主标题
                    Text(
                        text = "AI 正在为你优化简历",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "你的经历值得更好的表达",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    // ── 步骤列表 ─────────────────────────
                    val steps = uiState.steps
                    if (steps.isNotEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            steps.forEachIndexed { index, step ->
                                StepItem(
                                    label = step.label,
                                    isComplete = step.isComplete,
                                    isCurrent = step.isCurrent,
                                    isLast = index == steps.lastIndex
                                )
                            }
                        }
                    } else {
                        // 回退：步骤还未初始化
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            strokeWidth = 4.dp
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // ── 底部进度条 ───────────────────────
                    PolishProgressBar(progress = uiState.overallProgress)

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Error state
            AnimatedVisibility(
                visible = uiState.state is PolishState.Error,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ErrorBanner(
                        message = (uiState.state as? PolishState.Error)?.message ?: "未知错误",
                        onRetry = { viewModel.retry() }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = viewModel::retry) {
                        Text("重试")
                    }
                }
            }

            // Success — navigate automatically
            if (uiState.state is PolishState.Success) {
                val sessionId = (uiState.state as PolishState.Success).sessionId
                onPolishSuccess(sessionId)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
//  Private: 步骤条目 & 进度条
// ═══════════════════════════════════════════════════════════

/** 单个步骤行：✅完成 / 🔄进行中 / ⬜待处理 */
@Composable
private fun StepItem(
    label: String,
    isComplete: Boolean,
    isCurrent: Boolean,
    isLast: Boolean
) {
    val textAlpha by animateFloatAsState(
        targetValue = if (isComplete || isCurrent) 1f else 0.45f,
        animationSpec = tween(400),
        label = "stepAlpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧图标：勾 / 转圈 / 空心圆
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                isComplete -> Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "已完成",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                isCurrent -> CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                else -> Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        // 步骤文字
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .alpha(textAlpha)
        )

        // 右侧勾（已完成）
        if (isComplete) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
    }

    // 步骤间分割线
    if (!isLast) {
        Box(
            modifier = Modifier
                .padding(start = 12.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    if (isComplete)
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    else
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )
        )
    }
}

/** 底部进度条 + 百分比文字 */
@Composable
private fun PolishProgressBar(progress: Float) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(600),
        label = "progressBar"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        // 进度文字
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "整体进度",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${(animatedProgress * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(6.dp))

        // 进度条
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

// ─── Previews ──────────────────────────────────────────────────────────────

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun StepItemPreview() {
    TieLinkTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            StepItem(label = "解析JD需求", isComplete = true, isCurrent = false, isLast = false)
            StepItem(label = "分析简历匹配度", isComplete = true, isCurrent = false, isLast = false)
            StepItem(label = "AI深度润色中", isComplete = false, isCurrent = true, isLast = false)
            StepItem(label = "生成优化建议", isComplete = false, isCurrent = false, isLast = true)
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun PolishProgressBarPreview() {
    TieLinkTheme {
        Box(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            PolishProgressBar(progress = 0.45f)
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun PolishScreenPreview() {
    // Preview of static layout — hiltViewModel() unavailable in preview
    TieLinkTheme {
        PolishScreen(onNavigateBack = {}, onPolishSuccess = {})
    }
}
