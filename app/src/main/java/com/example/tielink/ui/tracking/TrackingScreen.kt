package com.example.tielink.ui.tracking

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.tielink.data.repository.TrackingItem
import com.example.tielink.ui.LocalGlobalJdViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TrackingScreen(
    onNavigateBack: () -> Unit,
    onNavigateToJdInput: () -> Unit,
    viewModel: TrackingViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val globalJdVm = LocalGlobalJdViewModel.current
    val jdState by globalJdVm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("投递管理") },
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
                    onClick = viewModel::toggleAddNew
                ) {
                    Icon(Icons.Default.Add, "新增投递")
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
                    onSave = viewModel::addTracking,
                    onCancel = viewModel::toggleAddNew
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // ── Tracking List ──────────────────────────────
            val items = viewModel.filteredItems
            if (items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📋", style = MaterialTheme.typography.displayMedium)
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
                            onStatusChange = { newStatus ->
                                viewModel.updateStatus(item.id, newStatus)
                            },
                            onDelete = { viewModel.deleteItem(item.id) }
                        )
                    }
                }
            }
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
private fun TrackingItemCard(
    item: TrackingItem,
    onStatusChange: (String) -> Unit,
    onDelete: () -> Unit
) {
    var showStatusMenu by remember { mutableStateOf(false) }
    val statusColor = Color(STATUS_COLORS[item.status] ?: 0xFF757575)
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status indicator dot
            Surface(
                modifier = Modifier.size(10.dp),
                shape = CircleShape,
                color = statusColor
            ) { }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${item.companyName} - ${item.positionName}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )

                // Timeline
                if (item.timeline.isNotEmpty()) {
                    Text(
                        text = item.timeline.joinToString(" → ") {
                            it.status
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "创建: ${dateFormat.format(Date(item.createdAt))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                onStatusChange(s)
                                showStatusMenu = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(
                onClick = onDelete,
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
