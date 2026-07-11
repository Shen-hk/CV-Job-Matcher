package com.example.tielink.ui.settings

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tielink.ui.components.AppListRow
import com.example.tielink.ui.components.AppStatusPill
import com.example.tielink.ui.components.AppSurfaceCard
import com.example.tielink.ui.theme.ActionBlue
import com.example.tielink.ui.theme.FocusCyan
import com.example.tielink.ui.theme.MatchGreen
import com.example.tielink.ui.theme.MissRed
import com.example.tielink.ui.theme.AppSpacing
import com.example.tielink.ui.theme.TieLinkTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToModelConfig: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) {
            snackbarHostState.showSnackbar("设置已保存")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(AppSpacing.page)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsSection(title = "AI 模型", accentColor = ActionBlue) {
                AppListRow(
                    title = state.activeModelName ?: "未配置模型",
                    subtitle = state.activeProviderName ?: "请先配置 AI Provider 和模型",
                    leadingIcon = Icons.Default.Settings,
                    accentColor = ActionBlue,
                    trailing = {
                        AppStatusPill(
                            text = if (state.activeModelName != null) "已启用" else "待配置",
                            color = if (state.activeModelName != null) MatchGreen else MaterialTheme.colorScheme.outline
                        )
                    },
                    onClick = onNavigateToModelConfig
                )
                Spacer(modifier = Modifier.height(AppSpacing.xs))
                OutlinedButton(
                    onClick = onNavigateToModelConfig,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("模型配置")
                }
            }

            Spacer(modifier = Modifier.height(AppSpacing.lg))

            SettingsSection(title = "连接测试", accentColor = FocusCyan) {
                AppListRow(
                    title = "当前活跃配置",
                    subtitle = "使用当前 Provider、模型和 API Key 进行一次轻量验证",
                    leadingIcon = Icons.Default.WifiTethering,
                    accentColor = MaterialTheme.colorScheme.tertiary
                )
                Spacer(modifier = Modifier.height(AppSpacing.xs))
                Button(
                    onClick = viewModel::testConnection,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isTesting && state.apiKey.isNotBlank()
                ) {
                    if (state.isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("测试连接")
                }

                state.testResult?.let { result ->
                    Spacer(modifier = Modifier.height(AppSpacing.sm))
                    AppStatusPill(
                        text = if (result.isEmpty()) "连接成功" else result,
                        color = if (result.isEmpty()) MatchGreen else MissRed,
                        leadingDot = result.isEmpty()
                    )
                }
            }

            Spacer(modifier = Modifier.height(AppSpacing.lg))

            SettingsSection(title = "关于", accentColor = MaterialTheme.colorScheme.secondary) {
                AppListRow(
                    title = "TieLink v1.0",
                    subtitle = "面向中文求职场景的简历优化、岗位分析和投递节奏 Agent。",
                    leadingIcon = Icons.Default.Info,
                    accentColor = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    accentColor: Color,
    content: @Composable () -> Unit
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = AppSpacing.xs, bottom = AppSpacing.xs)
    )
    AppSurfaceCard(accentColor = accentColor) {
        Column(modifier = Modifier.padding(AppSpacing.xs)) {
            content()
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun SettingsScreenPreview() {
    // Preview of static layout — hiltViewModel() unavailable in preview
    TieLinkTheme {
        SettingsScreen(onNavigateBack = {})
    }
}
