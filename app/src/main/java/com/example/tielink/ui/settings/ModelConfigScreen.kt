package com.example.tielink.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tielink.ui.theme.TieLinkTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelConfigScreen(
    onNavigateBack: () -> Unit,
    viewModel: ModelConfigViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.saveMessage) {
        state.saveMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("模型配置") },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("当前生效配置", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when (state.aiProvider) {
                            "ollama" -> "Ollama · ${state.ollamaModel}"
                            "local" -> "本地模式 · 仅 Embedding"
                            else -> "DeepSeek · ${state.deepSeekModel}"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when (state.aiProvider) {
                            "ollama" -> state.ollamaBaseUrl
                            "local" -> "无需外部服务地址"
                            else -> state.deepSeekBaseUrl
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text("选择模型后端", style = MaterialTheme.typography.titleMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ProviderButton(
                    label = "DeepSeek",
                    selected = state.aiProvider == "deepseek",
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.updateAiProvider("deepseek") }
                )
                ProviderButton(
                    label = "Ollama",
                    selected = state.aiProvider == "ollama",
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.updateAiProvider("ollama") }
                )
                ProviderButton(
                    label = "本地",
                    selected = state.aiProvider == "local",
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.updateAiProvider("local") }
                )
            }

            when (state.aiProvider) {
                "ollama" -> OllamaConfigSection(
                    baseUrl = state.ollamaBaseUrl,
                    model = state.ollamaModel,
                    embedModel = state.ollamaEmbedModel,
                    onBaseUrlChange = viewModel::updateOllamaBaseUrl,
                    onModelChange = viewModel::updateOllamaModel,
                    onEmbedModelChange = viewModel::updateOllamaEmbedModel
                )

                "local" -> LocalConfigSection()

                else -> DeepSeekConfigSection(
                    apiKey = state.deepSeekApiKey,
                    baseUrl = state.deepSeekBaseUrl,
                    model = state.deepSeekModel,
                    onApiKeyChange = viewModel::updateDeepSeekApiKey,
                    onBaseUrlChange = viewModel::updateDeepSeekBaseUrl,
                    onModelChange = viewModel::updateDeepSeekModel
                )
            }

            Button(
                onClick = viewModel::saveConfig,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isSaving) {
                    Text("保存中...")
                } else {
                    Icon(Icons.Default.Settings, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("保存并启用")
                }
            }
        }
    }
}

@Composable
private fun ProviderButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) {
            Text(label)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) {
            Text(label)
        }
    }
}

@Composable
private fun DeepSeekConfigSection(
    apiKey: String,
    baseUrl: String,
    model: String,
    onApiKeyChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onModelChange: (String) -> Unit
) {
    ConfigCard(
        title = "DeepSeek 配置",
        description = "适合云端调用的默认模型后端。"
    ) {
        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("API Key") }
        )
        OutlinedTextField(
            value = baseUrl,
            onValueChange = onBaseUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Base URL") }
        )
        OutlinedTextField(
            value = model,
            onValueChange = onModelChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("模型名称") }
        )
    }
}

@Composable
private fun OllamaConfigSection(
    baseUrl: String,
    model: String,
    embedModel: String,
    onBaseUrlChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onEmbedModelChange: (String) -> Unit
) {
    ConfigCard(
        title = "Ollama 配置",
        description = "本地或局域网模型，适合离线和私有部署。"
    ) {
        OutlinedTextField(
            value = baseUrl,
            onValueChange = onBaseUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("服务地址") }
        )
        OutlinedTextField(
            value = model,
            onValueChange = onModelChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("聊天模型") }
        )
        OutlinedTextField(
            value = embedModel,
            onValueChange = onEmbedModelChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Embedding 模型") }
        )
    }
}

@Composable
private fun LocalConfigSection() {
    ConfigCard(
        title = "本地模式",
        description = "当前模式只使用本地 Embedding，不依赖远程大模型。"
    ) {
        Text(
            text = "适合先做离线体验或排查配置问题。若要进行对话生成，请切回 DeepSeek 或 Ollama。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ConfigCard(
    title: String,
    description: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            content()
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ModelConfigScreenPreview() {
    TieLinkTheme {
        ModelConfigScreen(onNavigateBack = {})
    }
}
