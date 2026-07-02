package com.example.tielink.ui.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.tielink.domain.model.HistoryItem
import com.example.tielink.domain.model.displayTitle
import com.example.tielink.domain.model.previewText
import com.example.tielink.ui.history.HistoryDateFilter
import com.example.tielink.ui.history.HistoryUiState
import com.example.tielink.ui.theme.TieLinkTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentDrawerContent(
    historyState: com.example.tielink.ui.history.HistoryUiState,
    onSearchQueryChange: (String) -> Unit,
    onDateFilterChange: (HistoryDateFilter) -> Unit,
    onToggleBulkMode: (Boolean) -> Unit,
    onToggleSelection: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onRenameHistory: (Long, String) -> Unit,
    onDeleteHistory: (Long) -> Unit,
    onDeleteSelected: () -> Unit,
    onPinHistory: (Long, Boolean) -> Unit,
    onPinSelected: (Boolean) -> Unit,
    onExportHistory: (Long) -> Unit,
    onClearAllHistory: () -> Unit,
    onCreateBranch: (HistoryItem) -> Unit,
    onCreateNewSession: () -> Unit,
    onOpenResumeOptimize: () -> Unit,
    onOpenTracking: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistoryRecord: (Long) -> Unit,
    onOpenJdList: () -> Unit,
    onOpenResumeLibrary: () -> Unit
) {
    var activeItem by remember { mutableStateOf<HistoryItem?>(null) }
    var renameTarget by remember { mutableStateOf<HistoryItem?>(null) }
    var renameText by remember { mutableStateOf(TextFieldValue("")) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var showDeleteSelectedConfirm by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text("清空全部历史") },
            text = { Text("会删除所有本地历史记录，操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    onClearAllHistory()
                    showClearAllConfirm = false
                }) { Text("确认清空") }
            },
            dismissButton = { TextButton(onClick = { showClearAllConfirm = false }) { Text("取消") } }
        )
    }

    if (showDeleteSelectedConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteSelectedConfirm = false },
            title = { Text("删除已选记录") },
            text = { Text("将删除 ${historyState.selectedIds.size} 条记录。") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteSelected()
                    showDeleteSelectedConfirm = false
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { showDeleteSelectedConfirm = false }) { Text("取消") } }
        )
    }

    renameTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名会话") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(item.displayTitle) }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRenameHistory(item.id, renameText.text.ifBlank { item.displayTitle })
                    renameTarget = null
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("取消") } }
        )
    }

    activeItem?.let { item ->
        ModalBottomSheet(
            onDismissRequest = { activeItem = null },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(item.displayTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    item.previewText.ifBlank { "这条记录还没有可展示的摘要。" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                HorizontalDivider()
                DrawerSheetAction("重命名会话", Icons.Default.Edit) {
                    renameText = TextFieldValue(item.displayTitle)
                    renameTarget = item
                    activeItem = null
                }
                DrawerSheetAction(if (item.isPinned) "取消置顶" else "置顶会话", Icons.Default.PushPin) {
                    onPinHistory(item.id, !item.isPinned)
                    activeItem = null
                }
                DrawerSheetAction("导出对话记录", Icons.Default.Share) {
                    onExportHistory(item.id)
                    activeItem = null
                }
                DrawerSheetAction("新建分支会话", Icons.AutoMirrored.Filled.OpenInNew) {
                    onCreateBranch(item)
                    activeItem = null
                }
                DrawerSheetAction("删除会话", Icons.Default.Delete, isDestructive = true) {
                    onDeleteHistory(item.id)
                    activeItem = null
                }
            }
        }
    }

    ModalDrawerSheet {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            DrawerAccountCard(
                modelSummary = historyState.modelSummary,
                syncSummary = historyState.syncSummary,
                onClick = onOpenSettings
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onCreateNewSession) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("新建会话")
                }
                IconButton(onClick = { onToggleBulkMode(!historyState.bulkMode) }) {
                    Icon(
                        if (historyState.bulkMode) Icons.Default.Close else Icons.Default.MoreVert,
                        contentDescription = if (historyState.bulkMode) "退出批量模式" else "更多操作"
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = historyState.searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text("搜索历史标题、摘要或内容") }
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text("快捷入口", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            DrawerQuickActionGrid(
                onOpenResumeOptimize = onOpenResumeOptimize,
                onOpenTracking = onOpenTracking,
                onOpenJdList = onOpenJdList,
                onOpenResumeLibrary = onOpenResumeLibrary,
                onOpenSettings = onOpenSettings
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HistoryDateFilter.values().forEach { filter ->
                    FilterChip(
                        selected = historyState.dateFilter == filter,
                        onClick = { onDateFilterChange(filter) },
                        label = { Text(filter.label) }
                    )
                }
            }

            if (historyState.bulkMode) {
                Spacer(modifier = Modifier.height(10.dp))
                BulkActionBar(
                    selectedCount = historyState.selectedIds.size,
                    hasVisibleItems = historyState.filteredItems.isNotEmpty(),
                    onSelectAll = onSelectAll,
                    onClearSelection = onClearSelection,
                    onPinSelected = { onPinSelected(true) },
                    onDeleteSelected = { showDeleteSelectedConfirm = true }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (!historyState.bulkMode && historyState.filteredItems.isNotEmpty()) {
                val latest = historyState.filteredItems.first()
                DrawerContinueCard(
                    title = "继续最近记录",
                    subtitle = latest.displayTitle,
                    onClick = { onOpenHistoryRecord(latest.id) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            Text("历史记录", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (historyState.filteredItems.isEmpty()) {
                    item {
                        Text(
                            text = if (historyState.searchQuery.isBlank()) "还没有历史记录。" else "没有找到匹配记录。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(historyState.filteredItems, key = { it.id }) { item ->
                        DrawerHistoryRow(
                            item = item,
                            isBulkMode = historyState.bulkMode,
                            isSelected = historyState.selectedIds.contains(item.id),
                            onClick = {
                                if (historyState.bulkMode) onToggleSelection(item.id)
                                else onOpenHistoryRecord(item.id)
                            },
                            onLongClick = {
                                if (!historyState.bulkMode) activeItem = item
                            },
                            onCheckedChange = { onToggleSelection(item.id) }
                        )
                    }
                }
            }

            HorizontalDivider()
            Spacer(modifier = Modifier.height(10.dp))

            BottomStatusBar(
                storageSummary = historyState.storageSummary,
                syncSummary = historyState.syncSummary,
                onOpenSettings = onOpenSettings,
                onOpenHistoryOverview = onOpenJdList,
                onClearAll = { showClearAllConfirm = true }
            )
        }
    }
}

@Composable
fun DrawerAccountCard(
    modelSummary: String,
    syncSummary: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.CenterStart)) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("T", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("TieLink 工作台", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(modelSummary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(syncSummary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(
                Icons.Default.Settings,
                contentDescription = null,
                modifier = Modifier.align(Alignment.CenterEnd),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun DrawerQuickActionGrid(
    onOpenResumeOptimize: () -> Unit,
    onOpenTracking: () -> Unit,
    onOpenJdList: () -> Unit,
    onOpenResumeLibrary: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DrawerQuickActionCard("简历优化", "继续处理简历", Icons.Outlined.Description, onOpenResumeOptimize)
        DrawerQuickActionCard("投递追踪", "管理流程进度", Icons.Default.Checklist, onOpenTracking)
        DrawerQuickActionCard("JD 列表", "查看岗位资料", Icons.Default.Work, onOpenJdList)
        DrawerQuickActionCard("简历库", "打开版本仓库", Icons.Outlined.Description, onOpenResumeLibrary)
        DrawerQuickActionCard("模型配置", "切换当前模型", Icons.Default.Settings, onOpenSettings)
    }
}

@Composable
fun DrawerQuickActionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun BulkActionBar(
    selectedCount: Int,
    hasVisibleItems: Boolean,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onPinSelected: () -> Unit,
    onDeleteSelected: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("已选 $selectedCount 条", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onSelectAll, enabled = hasVisibleItems) { Text("全选") }
        TextButton(onClick = onClearSelection, enabled = selectedCount > 0) { Text("清空") }
        TextButton(onClick = onPinSelected, enabled = selectedCount > 0) { Text("批量置顶") }
        TextButton(onClick = onDeleteSelected, enabled = selectedCount > 0) { Text("批量删除") }
    }
}

@Composable
fun DrawerContinueCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
fun DrawerHistoryRow(
    item: HistoryItem,
    isBulkMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onCheckedChange: () -> Unit
) {
    val dateText = remember(item.updatedAt) {
        java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(item.updatedAt))
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isBulkMode) {
                Checkbox(checked = isSelected, onCheckedChange = { onCheckedChange() })
                Spacer(modifier = Modifier.width(6.dp))
            }
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.displayTitle, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (item.isPinned) {
                        Icon(Icons.Default.PushPin, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = item.previewText.ifBlank { "点击继续查看这条记录" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(dateText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun DrawerSheetAction(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            title,
            color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun BottomStatusBar(
    storageSummary: String,
    syncSummary: String,
    onOpenSettings: () -> Unit,
    onOpenHistoryOverview: () -> Unit,
    onClearAll: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(syncSummary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("历史占用：$storageSummary", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onOpenSettings) { Text("设置") }
            TextButton(onClick = onOpenHistoryOverview) { Text("历史总览") }
            TextButton(onClick = onClearAll) { Text("清理记录") }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 900)
@Composable
private fun AgentDrawerContentPreview() {
    val sampleItems = listOf(
        HistoryItem(
            id = 1L,
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_500_000L,
            jdTitle = "Android 开发工程师",
            customTitle = "面试准备",
            jdRawText = "负责客户端架构与性能优化",
            originalResume = "Resume A",
            polishedResume = "Resume B",
            jdSkills = listOf("Kotlin", "Compose"),
            optimizationNote = "建议补充性能优化经历",
            isPinned = true
        ),
        HistoryItem(
            id = 2L,
            createdAt = 1_700_100_000_000L,
            updatedAt = 1_700_100_500_000L,
            jdTitle = "客户端研发",
            customTitle = "",
            jdRawText = "负责业务迭代",
            originalResume = "Resume C",
            polishedResume = "Resume D",
            jdSkills = listOf("Android", "Networking"),
            optimizationNote = "这条记录还有待继续优化",
            isPinned = false
        )
    )

    TieLinkTheme {
        AgentDrawerContent(
            historyState = HistoryUiState(
                items = sampleItems,
                filteredItems = sampleItems,
                isLoading = false,
                searchQuery = "",
                bulkMode = false,
                selectedIds = setOf(1L),
                dateFilter = HistoryDateFilter.ALL,
                modelSummary = "当前模型：DeepSeek",
                syncSummary = "最近同步：2 分钟前",
                storageSummary = "128 KB"
            ),
            onSearchQueryChange = {},
            onDateFilterChange = {},
            onToggleBulkMode = {},
            onToggleSelection = {},
            onSelectAll = {},
            onClearSelection = {},
            onRenameHistory = { _, _ -> },
            onDeleteHistory = {},
            onDeleteSelected = {},
            onPinHistory = { _, _ -> },
            onPinSelected = {},
            onExportHistory = {},
            onClearAllHistory = {},
            onCreateBranch = {},
            onCreateNewSession = {},
            onOpenResumeOptimize = {},
            onOpenTracking = {},
            onOpenSettings = {},
            onOpenHistoryRecord = {},
            onOpenJdList = {},
            onOpenResumeLibrary = {}
        )
    }
}
