package com.example.tielink.ui.agent

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.tielink.domain.agent.BackgroundDocumentPolicy
import com.example.tielink.domain.agent.AgentTeamPolicy
import com.example.tielink.domain.model.AgentBackgroundStatus
import com.example.tielink.domain.model.AgentBackgroundTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentBackgroundTasksSheet(
    tasks: List<AgentBackgroundTask>,
    loaded: Boolean,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
    onRead: (String) -> Unit,
    onOpenConversation: (Long) -> Unit
) {
    val selected = tasks.firstOrNull { it.id == selectedId }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportContent by rememberSaveable { mutableStateOf("") }
    var exportFeedback by remember { mutableStateOf<String?>(null) }
    var showHtml by rememberSaveable(selectedId) { mutableStateOf(false) }
    var confirmCancel by rememberSaveable(selectedId) { mutableStateOf(false) }
    val exportDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri != null) scope.launch {
            exportFeedback = withContext(Dispatchers.IO) {
                runCatching {
                    requireNotNull(context.contentResolver.openOutputStream(uri)).use { it.write(exportContent.toByteArray(Charsets.UTF_8)) }
                    "文档已导出"
                }.getOrElse { "导出未完成：${it.message}" }
            }
        }
    }
    val exportHtml = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { uri ->
        if (uri != null) scope.launch {
            exportFeedback = withContext(Dispatchers.IO) {
                runCatching {
                    requireNotNull(context.contentResolver.openOutputStream(uri)).use { it.write(exportContent.toByteArray(Charsets.UTF_8)) }
                    "HTML 已导出"
                }.getOrElse { "导出未完成：${it.message}" }
            }
        }
    }
    LaunchedEffect(selected?.id, selected?.status) {
        if (selected?.status == AgentBackgroundStatus.COMPLETED) onRead(selected.id)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (selectedId == null) "我的任务" else "进度与成果", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { if (selectedId == null) onDismiss() else onSelect(null) }) {
                        Text(if (selectedId == null) "关闭" else "全部任务")
                    }
                }
            }
            if (!loaded) {
                item { Text("正在读取任务与已保存成果…") }
            } else if (selectedId != null && selected == null) {
                item { Text("该任务暂不可用，可能已被删除。可以返回任务列表查看。") }
            } else if (selected != null) {
                item {
                    Text(selected.title, style = MaterialTheme.typography.titleMedium)
                    Text(BackgroundDocumentPolicy.statusLabel(selected.status), color = MaterialTheme.colorScheme.primary)
                    Text(BackgroundDocumentPolicy.progress(selected), style = MaterialTheme.typography.bodyMedium)
                    BackgroundDocumentPolicy.evidence(selected).forEach { Text("已完成 · $it", style = MaterialTheme.typography.bodySmall) }
                    if (selected.error.isNotBlank()) Text(selected.error, color = MaterialTheme.colorScheme.error)
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (selected.status.canPause) TextButton(onClick = { onPause(selected.id) }) { Text("暂停") }
                        if (selected.status.canResume) TextButton(onClick = { onResume(selected.id) }) { Text("继续") }
                        if (!selected.status.isTerminal) TextButton(onClick = { confirmCancel = true }) { Text("取消任务") }
                        selected.originHistoryId?.let { id -> TextButton(onClick = { onOpenConversation(id) }) { Text("回原对话") } }
                    }
                }
                item {
                    Text("本次要求", style = MaterialTheme.typography.titleSmall)
                    Text(selected.prompt, style = MaterialTheme.typography.bodySmall)
                    selected.sourceTaskId?.let { sourceId ->
                        TextButton(onClick = { onSelect(sourceId) }) { Text("查看本次修订所依据的原稿") }
                    }
                    selected.supersedesTaskId?.let { previousId ->
                        TextButton(onClick = { onSelect(previousId) }) { Text("查看调整前的任务与记录") }
                    }
                }
                selected.team?.let { team -> item {
                    Text("本次团队 · ${team.name}", style = MaterialTheme.typography.titleSmall)
                    Text(team.brief, style = MaterialTheme.typography.bodySmall)
                    if (team.instructions.isNotBlank()) Text(team.instructions, style = MaterialTheme.typography.bodySmall)
                    Text("交付：${AgentTeamPolicy.outputLabel(selected.includeHtml)}。已保存接单时的要求，之后编辑团队不会改变本任务。",
                        style = MaterialTheme.typography.bodySmall)
                } }
                if (selected.hasDocument) {
                    item {
                        Text("已保存的文档草稿", style = MaterialTheme.typography.titleMedium)
                        Text("查看成果不会暂停任务；导出不等于采用为正式资料。", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { exportContent = selected.document; exportDocument.launch("${selected.title.take(32)}.md") }) { Text("导出文档") }
                        MarkdownText(selected.document)
                    }
                } else if (selected.partialText.isNotBlank()) {
                    item {
                        Text("生成中的未完成内容", style = MaterialTheme.typography.titleMedium)
                        Text("此内容尚未形成完整草稿；继续时会重新生成，不直接拼接未完成文本。", style = MaterialTheme.typography.bodySmall)
                        Text(selected.partialText, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (selected.hasHtml) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { showHtml = !showHtml }) { Text(if (showHtml) "收起 HTML" else "预览 HTML") }
                            TextButton(onClick = { exportContent = selected.html; exportHtml.launch("${selected.title.take(32)}.html") }) { Text("导出 HTML") }
                        }
                    }
                    if (showHtml) item { BackgroundHtmlPreview(selected.html) }
                }
            } else {
                if (tasks.isEmpty()) item {
                    Text("还没有后台任务。可以对 Agent 说：帮我把这个灵感整理成一页方案，我先去忙。", style = MaterialTheme.typography.bodyMedium)
                }
                items(tasks, key = { it.id }) { task ->
                    Card(onClick = { onSelect(task.id) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(task.title, style = MaterialTheme.typography.titleSmall)
                            Text(BackgroundDocumentPolicy.statusLabel(task.status) + if (task.isUnread) " · 新成果" else "",
                                color = MaterialTheme.colorScheme.primary)
                            Text(BackgroundDocumentPolicy.progress(task), style = MaterialTheme.typography.bodySmall)
                            BackgroundDocumentPolicy.evidence(task).lastOrNull()?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
            exportFeedback?.let { text -> item { Text(text, style = MaterialTheme.typography.bodySmall) } }
            item { Text("后台任务支持文档草稿与 HTML；简历优化和 PDF 请从对应工具进入。", style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(vertical = 16.dp)) }
        }
    }
    if (confirmCancel && selected != null) AlertDialog(onDismissRequest = { confirmCancel = false },
        title = { Text("取消这项任务？") }, text = { Text("后续工作将停止，已经生成的成果会继续保留。若只是稍后继续，可以使用暂停。") },
        confirmButton = { TextButton(onClick = { confirmCancel = false; onCancel(selected.id) }) { Text("取消任务") } },
        dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("保留任务") } })
}

@Composable
private fun BackgroundHtmlPreview(html: String) {
    val context = LocalContext.current
    val view = remember { WebView(context).apply {
        settings.javaScriptEnabled = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.blockNetworkLoads = true
        webViewClient = WebViewClient()
    } }
    DisposableEffect(view) { onDispose { view.destroy() } }
    AndroidView(factory = { view }, modifier = Modifier.fillMaxWidth().height(480.dp),
        update = { if (it.tag != html) { it.tag = html; it.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null) } })
}
