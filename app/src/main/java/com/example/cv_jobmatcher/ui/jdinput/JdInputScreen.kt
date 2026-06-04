package com.example.cv_jobmatcher.ui.jdinput

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cv_jobmatcher.ui.components.ErrorBanner
import com.example.cv_jobmatcher.ui.components.SectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JdInputScreen(
    onNavigateToSettings: () -> Unit,
    onJdSubmitted: (jdRawText: String, jdStructuredJson: String) -> Unit,
    viewModel: JdInputViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.processImage(context, it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("岗位 JD 输入") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
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
            Text(
                text = "第 1 步：输入目标岗位 JD",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "粘贴招聘 JD 文本，或上传截图自动识别",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── Image picker + OCR ──────────────────────────
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    enabled = !state.isOcrProcessing,
                    modifier = Modifier.weight(1f)
                ) {
                    if (state.isOcrProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(18.dp).width(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("识别中...")
                    } else {
                        Icon(
                            Icons.Default.Image,
                            contentDescription = null,
                            modifier = Modifier.height(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("从图片识别")
                    }
                }

                if (state.jdRawText.isNotBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = viewModel::clearText,
                        colors = ButtonDefaults.outlinedButtonColors()
                    ) {
                        Text("清空")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Text input ──────────────────────────────────
            OutlinedTextField(
                value = state.jdRawText,
                onValueChange = viewModel::updateJdText,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                placeholder = { Text("在此粘贴岗位 JD 文字...\n\n例如：岗位职责、任职要求、技能要求等\n\n或点击「从图片识别」上传截图") },
                maxLines = 20
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (state.isProcessing) {
                CircularProgressIndicator()
            }

            state.error?.let { error ->
                ErrorBanner(message = error)
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Show structured extraction result
            state.jdStructured?.let { jd ->
                if (state.isStructured) {
                    Spacer(modifier = Modifier.height(8.dp))
                    SectionCard(title = "识别结果: ${jd.jobTitle}") {
                        if (jd.skills.isNotEmpty()) {
                            Text(
                                text = "技能: ${jd.skills.joinToString(" · ")}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        if (jd.requirements.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "要求: ${jd.requirements.joinToString(" · ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    viewModel.submitJd { rawText, json ->
                        onJdSubmitted(rawText, json)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isProcessing && state.jdRawText.isNotBlank()
            ) {
                Text("下一步：输入简历 →")
            }
        }
    }
}
