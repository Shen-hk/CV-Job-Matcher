package com.example.tielink.ui.agent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.tielink.domain.agent.AgentTeamPolicy
import com.example.tielink.domain.model.AgentTeamProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentTeamsSheet(
    teams: List<AgentTeamProfile>, loaded: Boolean, selectedId: String?, busy: Boolean, error: String?,
    changeVersion: Long, lastDeletedId: String?,
    onSelect: (String?) -> Unit, onDismiss: () -> Unit,
    onSave: (String?, String, String, String, Boolean, () -> Unit) -> Unit,
    onDelete: (String, () -> Unit) -> Unit, onClearError: () -> Unit
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var editId by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var brief by rememberSaveable { mutableStateOf("") }
    var instructions by rememberSaveable { mutableStateOf("") }
    var includeHtml by rememberSaveable { mutableStateOf(true) }
    var originalName by rememberSaveable { mutableStateOf("") }
    var originalBrief by rememberSaveable { mutableStateOf("") }
    var originalInstructions by rememberSaveable { mutableStateOf("") }
    var originalHtml by rememberSaveable { mutableStateOf(true) }
    var pendingExit by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    var feedback by rememberSaveable { mutableStateOf<String?>(null) }
    var seenChangeVersion by rememberSaveable { mutableStateOf(changeVersion) }
    // A save/delete may complete across Activity recreation. Reconcile restored editor state
    // from the retained ViewModel instead of depending only on a callback to the old composition.
    LaunchedEffect(changeVersion) {
        if (changeVersion < seenChangeVersion) {
            // A fresh process has no retained change event. Keep the restored unsaved draft.
            seenChangeVersion = changeVersion
        } else if (changeVersion > seenChangeVersion) {
            seenChangeVersion = changeVersion
            if (lastDeletedId == null) {
                editing = false; feedback = "团队已保存，当前没有启动执行。"
            } else {
                if (selectedId == lastDeletedId) onSelect(null)
                deletingId = null; feedback = "团队已删除，任务与成果仍然保留。"
            }
        }
    }
    val changed = editing && (name != originalName || brief != originalBrief ||
        instructions != originalInstructions || includeHtml != originalHtml)
    fun leave(destination: String) {
        if (busy) return
        if (changed) pendingExit = destination
        else if (destination == "sheet") onDismiss() else editing = false
    }
    fun edit(team: AgentTeamProfile?) {
        onClearError()
        editId = team?.id
        name = team?.name.orEmpty(); originalName = name
        brief = team?.brief.orEmpty(); originalBrief = brief
        instructions = team?.instructions.orEmpty(); originalInstructions = instructions
        includeHtml = team?.includeHtml ?: true; originalHtml = includeHtml
        editing = true; feedback = null
    }

    ModalBottomSheet(onDismissRequest = { leave("sheet") },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true,
            confirmValueChange = { target ->
                if (target == SheetValue.Hidden && (busy || changed)) {
                    if (!busy) pendingExit = "sheet"
                    false
                } else true
            })) {
        LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (editing) if (editId == null) "创建团队" else "编辑团队" else "我的团队",
                        style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { leave(if (editing) "list" else "sheet") }, enabled = !busy) {
                        Text(if (editing) "返回列表" else "关闭")
                    }
                }
                Text("你始终和主 Agent 对话。团队保存文档任务的工作要求，选用后再说出要做的事。",
                    style = MaterialTheme.typography.bodySmall)
            }
            if (editing) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(name, { if (it.length <= AgentTeamPolicy.MAX_NAME) name = it },
                            Modifier.fillMaxWidth(), enabled = !busy, label = { Text("团队名称") }, singleLine = true,
                            supportingText = { Text("${name.length}/${AgentTeamPolicy.MAX_NAME}") })
                        OutlinedTextField(brief, { if (it.length <= AgentTeamPolicy.MAX_BRIEF) brief = it },
                            Modifier.fillMaxWidth(), enabled = !busy, label = { Text("主要帮你做什么") }, minLines = 2,
                            supportingText = { Text("例如：把零散灵感整理成可验证的一页方案") })
                        OutlinedTextField(instructions, { if (it.length <= AgentTeamPolicy.MAX_INSTRUCTIONS) instructions = it },
                            Modifier.fillMaxWidth(), enabled = !busy, label = { Text("工作要求（可选）") }, minLines = 4,
                            supportingText = { Text("${instructions.length}/${AgentTeamPolicy.MAX_INSTRUCTIONS} · 可写结构、语气、检查标准") })
                    }
                }
                item {
                    Text("默认交付工具", style = MaterialTheme.typography.titleSmall)
                    Text("文档编写 · 必选，生成并保存 Markdown 草稿")
                    Row {
                        Checkbox(checked = includeHtml, enabled = !busy, onCheckedChange = { includeHtml = it })
                        Column(Modifier.padding(top = 12.dp)) {
                            Text("HTML 排版与预览")
                            Text("草稿完成后生成，可预览并导出 HTML 文件", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text("PDF 和简历仍从已有工具入口使用。", style = MaterialTheme.typography.bodySmall)
                }
                item {
                    Button(enabled = !busy && name.isNotBlank() && brief.isNotBlank(), onClick = {
                        onSave(editId, name, brief, instructions, includeHtml) {
                            editing = false; feedback = "团队已保存。选用后再发送任务，当前没有启动执行。"
                        }
                    }) { Text(if (busy) "正在保存…" else "保存团队") }
                    Text("更改仅用于之后的新任务；修订已有草稿沿用接单时的工作要求。",
                        style = MaterialTheme.typography.bodySmall)
                }
            } else {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("主 Agent · 默认", style = MaterialTheme.typography.titleSmall)
                            Text("直接生成文档草稿和 HTML，不需要先创建团队。", style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { onSelect(null); onDismiss() }) { Text(if (selectedId == null) "当前选用" else "使用默认") }
                        }
                    }
                }
                if (!loaded) item { Text("正在读取团队…") }
                items(teams, key = { it.id }) { team ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(team.name + if (team.id == selectedId) " · 当前选用" else "", style = MaterialTheme.typography.titleSmall)
                            Text(team.brief, style = MaterialTheme.typography.bodyMedium)
                            Text(AgentTeamPolicy.outputLabel(team.snapshot()), style = MaterialTheme.typography.bodySmall)
                            Row {
                                TextButton(onClick = { onSelect(team.id); onDismiss() }, enabled = !busy) { Text("选用") }
                                TextButton(onClick = { edit(team) }, enabled = !busy) { Text("编辑") }
                                TextButton(onClick = { deletingId = team.id }, enabled = !busy) { Text("删除") }
                            }
                        }
                    }
                }
                item {
                    Button(onClick = { edit(null) }, enabled = loaded && !busy && teams.size < AgentTeamPolicy.MAX_TEAMS) { Text("创建团队") }
                    TextButton(onClick = {
                        edit(null)
                        name = "灵感整理团队"
                        brief = "把一句灵感整理成一页方案，明确下一步验证动作。"
                        instructions = "按目标用户、使用场景、最小方案、关键假设、验证办法和下一步行动组织内容。语言具体简洁。缺少信息时标注假设，不编造市场数据。"
                    }, enabled = loaded && !busy && teams.size < AgentTeamPolicy.MAX_TEAMS) { Text("从灵感整理模板开始") }
                }
            }
            error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            feedback?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.primary) } }
            item { Text("团队配置保存在本机。创建、选用和编辑都不会自动启动任务。", style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(vertical = 16.dp)) }
        }
    }
    pendingExit?.let { destination -> AlertDialog(onDismissRequest = { pendingExit = null },
        title = { Text("放弃未保存的更改？") }, text = { Text("已保存的团队与正在执行的任务不会受影响。") },
        confirmButton = { TextButton(onClick = { pendingExit = null; editing = false
            if (destination == "sheet") onDismiss()
        }) { Text("放弃更改") } },
        dismissButton = { TextButton(onClick = { pendingExit = null }) { Text("继续编辑") } }) }
    deletingId?.let { id -> AlertDialog(onDismissRequest = { if (!busy) deletingId = null },
        title = { Text("删除这个团队？") }, text = { Text("正在执行的任务和历史成果会保留各自的工作要求。之后的新任务不能再选用这个团队。") },
        confirmButton = { TextButton(enabled = !busy, onClick = { onDelete(id) {
            if (selectedId == id) onSelect(null)
            deletingId = null; feedback = "团队已删除，任务与成果仍然保留。"
        } }) { Text(if (busy) "正在删除…" else "删除团队") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { deletingId = null }) { Text("保留") } }) }
}
