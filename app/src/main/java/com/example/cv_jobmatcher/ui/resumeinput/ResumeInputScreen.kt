package com.example.cv_jobmatcher.ui.resumeinput

import android.net.Uri
import android.provider.OpenableColumns
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
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TextSnippet
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cv_jobmatcher.ui.components.ErrorBanner
import com.example.cv_jobmatcher.ui.components.SectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResumeInputScreen(
    onNavigateBack: () -> Unit,
    onResumeSubmitted: (resumeText: String, jdRawText: String, jdStructuredJson: String,
                         templatePath: String?, sourceType: String, fullPolish: Boolean) -> Unit,
    viewModel: ResumeInputViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            // Resolve file name and mime type
            val mimeType = context.contentResolver.getType(it)
            var fileName = "文件"
            context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) {
                    fileName = cursor.getString(nameIndex)
                }
            }
            viewModel.processFile(context, it, mimeType, fileName)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("简历输入") },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "第 2 步：输入你的简历",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "粘贴简历文本，或上传 PDF / DOCX 文件自动提取",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Show JD summary
            state.jdStructured?.let { jd ->
                Spacer(modifier = Modifier.height(12.dp))
                SectionCard(title = "目标岗位: ${jd.jobTitle}") {
                    Text(
                        text = jd.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── File upload buttons ─────────────────────────
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { filePickerLauncher.launch(arrayOf(
                        "application/pdf",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    )) },
                    enabled = !state.isFileProcessing,
                    modifier = Modifier.weight(1f)
                ) {
                    if (state.isFileProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(18.dp).width(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("解析中...")
                    } else {
                        Icon(
                            Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            modifier = Modifier.height(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("上传文件")
                    }
                }

                if (state.resumeText.isNotBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = viewModel::clearResume,
                        colors = ButtonDefaults.outlinedButtonColors()
                    ) {
                        Text("清空")
                    }
                }
            }

            // Show file name if loaded
            state.fileName?.let { name ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "已加载: $name",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Text input ──────────────────────────────────
            OutlinedTextField(
                value = state.resumeText,
                onValueChange = viewModel::updateResumeText,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
                placeholder = {
                    Text(
                        "在此粘贴你的简历...\n\n" +
                                "建议包含以下内容：\n" +
                                "• 个人信息（姓名、联系方式）\n" +
                                "• 工作经历（公司、职位、时间、职责、成果）\n" +
                                "• 项目经验（项目描述、你的角色、技术栈）\n" +
                                "• 技能清单\n" +
                                "• 教育背景\n\n" +
                                "或点击「上传文件」选择 PDF / DOCX"
                    )
                },
                maxLines = 25
            )

            Spacer(modifier = Modifier.height(8.dp))

            state.error?.let { error ->
                ErrorBanner(message = error)
                Spacer(modifier = Modifier.height(8.dp))
            }

            // ── Polish mode toggle ─────────────────────────
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Switch(
                    checked = state.fullPolish,
                    onCheckedChange = { viewModel.togglePolishMode() }
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (state.fullPolish) "全篇优化：根据JD深度改写简历" else "部分优化：仅调整关键词和措辞",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    viewModel.submitResume { resumeText, jdRawText, jdJson, tp, st, fp ->
                        onResumeSubmitted(resumeText, jdRawText, jdJson, tp, st, fp)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.resumeText.isNotBlank()
            ) {
                Text("开始润色 →")
            }
        }
    }
}
