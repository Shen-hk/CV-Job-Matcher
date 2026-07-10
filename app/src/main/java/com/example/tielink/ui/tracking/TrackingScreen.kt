package com.example.tielink.ui.tracking

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.WorkHistory
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.tielink.data.repository.TimelineEvent
import com.example.tielink.data.repository.TrackingItem
import com.example.tielink.ui.LocalGlobalJdViewModel
import com.example.tielink.ui.theme.TieLinkTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TrackingScreen(
    onNavigateBack: () -> Unit,
    onNavigateToJdInput: () -> Unit,
    initialJdCompany: String = "",
    initialJdPosition: String = "",
    viewModel: TrackingViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val globalJdVm = LocalGlobalJdViewModel.current
    val jdState by globalJdVm.state.collectAsState()
    var showSaveConfirm by remember { mutableStateOf(false) }
    var pendingStatusChange by remember { mutableStateOf<TrackingStatusChange?>(null) }
    var pendingDeleteItem by remember { mutableStateOf<TrackingItem?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(initialJdCompany, initialJdPosition) {
        if (initialJdCompany.isNotBlank() && initialJdPosition.isNotBlank()) {
            viewModel.openAddForm(
                company = initialJdCompany,
                position = initialJdPosition
            )
        }
    }

    LaunchedEffect(state.isAddingNew) {
        if (!state.isAddingNew) {
            showSaveConfirm = false
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        topBar = {
            TopAppBar(
                title = { Text("投递节奏", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            if (!state.isAddingNew) {
                FloatingActionButton(
                    onClick = viewModel::openAddForm
                ) {
                    Icon(Icons.Default.Add, contentDescription = "新增投递")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // ── JD Status Bar ──────────────────────────────
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNavigateToJdInput),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    text = jdState.displayLabel,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            TrackingOverviewCard(items = state.items)

            Spacer(modifier = Modifier.height(12.dp))

            // ── Status Filter Chips ────────────────────────
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = state.activeFilter == null,
                    onClick = { viewModel.setFilter(null) },
                    label = { Text("全部") }
                )
                STATUS_OPTIONS.forEach { status ->
                    FilterChip(
                        selected = state.activeFilter == status,
                        onClick = {
                            viewModel.setFilter(
                                if (state.activeFilter == status) null else status
                            )
                        },
                        label = { Text(status) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Add New Form ───────────────────────────────
            if (state.isAddingNew) {
                NewTrackingForm(
                    company = state.newCompany,
                    position = state.newPosition,
                    status = state.newStatus,
                    onCompanyChange = viewModel::updateNewCompany,
                    onPositionChange = viewModel::updateNewPosition,
                    onStatusChange = viewModel::updateNewStatus,
                    onSave = {
                        showSaveConfirm = true
                    },
                    onCancel = viewModel::closeAddForm
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "确认后会自动保存，保存成功后可撤销。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (showSaveConfirm) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TrackingSaveConfirmCard(
                        company = state.newCompany,
                        position = state.newPosition,
                        status = state.newStatus,
                        onConfirm = {
                            viewModel.addTracking { savedItem ->
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "已保存投递",
                                        actionLabel = "撤销"
                                    )
                                    if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                        viewModel.deleteItem(savedItem)
                                    }
                                }
                            }
                            showSaveConfirm = false
                        },
                        onCancel = {
                            showSaveConfirm = false
                        }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // ── Tracking List ──────────────────────────────
            pendingStatusChange?.let { pending ->
                TrackingStatusConfirmCard(
                    item = pending.item,
                    targetStatus = pending.targetStatus,
                    onConfirm = {
                        viewModel.updateStatus(pending.item.id, pending.targetStatus)
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = "状态已更新",
                                actionLabel = "撤销"
                            )
                            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                viewModel.updateStatus(pending.item.id, pending.item.status)
                            }
                        }
                        pendingStatusChange = null
                    },
                    onCancel = {
                        pendingStatusChange = null
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            pendingDeleteItem?.let { pending ->
                TrackingDeleteConfirmCard(
                    item = pending,
                    onConfirm = {
                        viewModel.deleteItem(pending) { deletedItem ->
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = "已删除投递",
                                    actionLabel = "撤销"
                                )
                                if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                    viewModel.restoreItem(deletedItem)
                                }
                            }
                        }
                        pendingDeleteItem = null
                    },
                    onCancel = {
                        pendingDeleteItem = null
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            val items = viewModel.filteredItems
            if (items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            modifier = Modifier.size(58.dp),
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.09f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.WorkHistory,
                                    null,
                                    Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            if (state.activeFilter != null) "该状态下暂无投递记录"
                            else "暂无投递记录，点击 + 添加",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items) { item ->
                        TrackingItemCard(
                            item = item,
                            onStatusRequest = { newStatus ->
                                pendingStatusChange = TrackingStatusChange(item, newStatus)
                            },
                            onDeleteRequest = {
                                pendingDeleteItem = item
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackingOverviewCard(items: List<TrackingItem>) {
    val interviewCount = items.count { it.status == "待面试" || it.status == "已面试" }
    val offerCount = items.count { it.status == "已offer" }
    val activeCount = items.count { it.status != "已拒" && it.status != "已offer" }
    val progress = if (items.isEmpty()) 0f else {
        items.sumOf { item ->
            STATUS_OPTIONS.indexOf(item.status).coerceIn(0, 4)
        }.toFloat() / (items.size * 4f)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF0B1220), Color(0xFF1D4ED8), Color(0xFF2563EB))
                    )
                )
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "APPLICATION PULSE",
                        color = Color(0xFFBFDBFE),
                        fontSize = 10.sp,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (items.isEmpty()) "从第一份投递开始" else "$activeCount 个机会正在推进",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Icon(
                    Icons.Default.QueryStats,
                    null,
                    Modifier.size(34.dp),
                    tint = Color.White.copy(alpha = 0.72f)
                )
            }

            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(50))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(7.dp)
                        .background(Color(0xFF93C5FD), RoundedCornerShape(50))
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TrackingMetric(
                    modifier = Modifier.weight(1f),
                    value = items.size.toString(),
                    label = "累计投递"
                )
                TrackingMetric(
                    modifier = Modifier.weight(1f),
                    value = interviewCount.toString(),
                    label = "面试机会"
                )
                TrackingMetric(
                    modifier = Modifier.weight(1f),
                    value = offerCount.toString(),
                    label = "Offer",
                    icon = Icons.Default.EmojiEvents
                )
            }
        }
    }
}

@Composable
private fun TrackingMetric(
    modifier: Modifier,
    value: String,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.10f)
    ) {
        Column(Modifier.padding(horizontal = 11.dp, vertical = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(value, color = Color.White, fontWeight = FontWeight.ExtraBold)
                if (icon != null) {
                    Spacer(Modifier.width(4.dp))
                    Icon(icon, null, Modifier.size(14.dp), tint = Color(0xFFFBBF24))
                }
            }
            Text(label, color = Color.White.copy(alpha = 0.62f), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun NewTrackingForm(
    company: String,
    position: String,
    status: String,
    onCompanyChange: (String) -> Unit,
    onPositionChange: (String) -> Unit,
    onStatusChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("新增投递", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = company,
                onValueChange = onCompanyChange,
                placeholder = { Text("公司名称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = position,
                onValueChange = onPositionChange,
                placeholder = { Text("岗位名称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(4.dp))

            // Status selector
            var statusExpanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { statusExpanded = true }) {
                    Text("状态: $status")
                }
                DropdownMenu(
                    expanded = statusExpanded,
                    onDismissRequest = { statusExpanded = false }
                ) {
                    STATUS_OPTIONS.forEach { s ->
                        DropdownMenuItem(
                            text = { Text(s) },
                            onClick = {
                                onStatusChange(s)
                                statusExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("取消") }
                Button(
                    onClick = onSave,
                    modifier = Modifier.weight(1f),
                    enabled = company.isNotBlank() && position.isNotBlank()
                ) { Text("添加") }
            }
        }
    }
}

@Composable
private fun TrackingSaveConfirmCard(
    company: String,
    position: String,
    status: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "确认新增投递",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("公司：$company", style = MaterialTheme.typography.bodyMedium)
            Text("岗位：$position", style = MaterialTheme.typography.bodyMedium)
            Text("状态：$status", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text("返回修改")
                }
                Button(onClick = onConfirm, modifier = Modifier.weight(1f)) {
                    Text("确认保存")
                }
            }
        }
    }
}

private data class TrackingStatusChange(
    val item: TrackingItem,
    val targetStatus: String
)

@Composable
private fun TrackingStatusConfirmCard(
    item: TrackingItem,
    targetStatus: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "确认修改状态",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("${item.companyName} - ${item.positionName}", style = MaterialTheme.typography.bodyMedium)
            Text("当前状态：${item.status}", style = MaterialTheme.typography.bodyMedium)
            Text("目标状态：$targetStatus", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text("返回")
                }
                Button(onClick = onConfirm, modifier = Modifier.weight(1f)) {
                    Text("确认修改")
                }
            }
        }
    }
}

@Composable
private fun TrackingDeleteConfirmCard(
    item: TrackingItem,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "确认删除投递",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("${item.companyName} - ${item.positionName}", style = MaterialTheme.typography.bodyMedium)
            Text("删除后无法恢复", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text("取消")
                }
                Button(onClick = onConfirm, modifier = Modifier.weight(1f)) {
                    Text("确认删除")
                }
            }
        }
    }
}

@Composable
private fun TrackingItemCard(
    item: TrackingItem,
    onStatusRequest: (String) -> Unit,
    onDeleteRequest: () -> Unit
) {
    var showStatusMenu by remember { mutableStateOf(false) }
    val statusColor = Color(STATUS_COLORS[item.status] ?: 0xFF757575)
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(13.dp),
                color = statusColor.copy(alpha = 0.12f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        item.companyName.trim().take(1).ifBlank { "企" },
                        color = statusColor,
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            Spacer(modifier = Modifier.width(11.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.positionName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = item.companyName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (item.timeline.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = item.timeline.joinToString(" → ") {
                            it.status
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Medium
                    )
                }

                Text(
                    text = dateFormat.format(Date(item.createdAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                )
            }

            // Status badge (clickable)
            Box {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.15f),
                    modifier = Modifier.clickable { showStatusMenu = true }
                ) {
                    Text(
                        text = item.status,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                DropdownMenu(
                    expanded = showStatusMenu,
                    onDismissRequest = { showStatusMenu = false }
                ) {
                    STATUS_OPTIONS.forEach { s ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        modifier = Modifier.size(8.dp),
                                        shape = CircleShape,
                                        color = Color(STATUS_COLORS[s] ?: 0xFF757575)
                                    ) { }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(s)
                                }
                            },
                            onClick = {
                                onStatusRequest(s)
                                showStatusMenu = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(
                onClick = onDeleteRequest,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    "删除",
                    Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

// ─── Previews ──────────────────────────────────────────────────────────────

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun TrackingItemCardPreview() {
    TieLinkTheme {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TrackingItemCard(
                item = TrackingItem(
                    id = 1,
                    companyName = "字节跳动",
                    positionName = "Android开发工程师",
                    status = "面试中",
                    timeline = listOf(
                        TimelineEvent("已投"),
                        TimelineEvent("简历筛选中"),
                        TimelineEvent("面试中")
                    ),
                    createdAt = System.currentTimeMillis()
                ),
                onStatusRequest = {},
                onDeleteRequest = {}
            )
            TrackingItemCard(
                item = TrackingItem(
                    id = 2,
                    companyName = "腾讯",
                    positionName = "iOS开发工程师",
                    status = "已投",
                    timeline = listOf(TimelineEvent("已投")),
                    createdAt = System.currentTimeMillis() - 86400000
                ),
                onStatusRequest = {},
                onDeleteRequest = {}
            )
            TrackingItemCard(
                item = TrackingItem(
                    id = 3,
                    companyName = "阿里巴巴",
                    positionName = "前端开发工程师",
                    status = "已Offer",
                    timeline = listOf(
                        TimelineEvent("已投"),
                        TimelineEvent("笔试"),
                        TimelineEvent("一面"),
                        TimelineEvent("二面"),
                        TimelineEvent("已Offer")
                    ),
                    createdAt = System.currentTimeMillis() - 86400000 * 7
                ),
                onStatusRequest = {},
                onDeleteRequest = {}
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun TrackingScreenPreview() {
    // Preview of static layout — hiltViewModel() unavailable in preview
    TieLinkTheme {
        TrackingScreen(
            onNavigateBack = {},
            onNavigateToJdInput = {}
        )
    }
}
