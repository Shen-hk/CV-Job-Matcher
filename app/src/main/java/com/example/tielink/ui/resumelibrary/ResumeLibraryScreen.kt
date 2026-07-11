package com.example.tielink.ui.resumelibrary

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.tielink.domain.model.ResumeLibraryItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ResumeLibraryScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPreview: (Long) -> Unit,
    onNavigateToHistoryPreview: (Long) -> Unit = {},
    selectionMode: Boolean = false,
    onResumeSelected: (Long) -> Unit = {},
    viewModel: ResumeLibraryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<ResumeLibraryItem?>(null) }
    var renameText by rememberSaveable { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<ResumeLibraryItem?>(null) }
    val supportedTypes = remember {
        arrayOf(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/plain"
        )
    }
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val mimeType = context.contentResolver.getType(uri)
        var fileName = "文件"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                fileName = cursor.getString(nameIndex)
            }
        }
        viewModel.uploadResume(context, uri, mimeType, fileName) { versionId ->
            if (selectionMode) {
                viewModel.selectResumeForOptimize(versionId)
                onResumeSelected(versionId)
            }
        }
    }
    val filteredItems = state.items.filter { item ->
        query.isBlank() ||
            item.title.contains(query, ignoreCase = true) ||
            item.subtitle.contains(query, ignoreCase = true)
    }

    renameTarget?.let { item ->
        RenameResumeDialog(
            title = renameText,
            itemTypeLabel = if (item.type == "history") "润色记录" else "简历版本",
            onTitleChange = { renameText = it },
            onConfirm = {
                viewModel.renameItem(item, renameText)
                renameTarget = null
                renameText = ""
            },
            onDismiss = {
                renameTarget = null
                renameText = ""
            }
        )
    }

    deleteTarget?.let { item ->
        DeleteResumeDialog(
            itemTitle = item.title,
            itemTypeLabel = if (item.type == "history") "润色记录" else "简历版本",
            onConfirm = {
                viewModel.deleteItem(item)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("简历库", fontWeight = FontWeight.Medium) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = { filePickerLauncher.launch(supportedTypes) },
                        enabled = !state.isUploading
                    ) {
                        if (state.isUploading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.UploadFile, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("新增/上传")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.items.isEmpty() && state.error != null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(state.error!!, color = MaterialTheme.colorScheme.error)
                    }
                }
                state.items.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Description, null, Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                            Spacer(Modifier.height(12.dp))
                            Text("暂无简历", style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Text("完成一次润色或保存版本后，简历会自动出现在这里",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(16.dp))
                            FilledTonalButton(
                                onClick = { filePickerLauncher.launch(supportedTypes) },
                                enabled = !state.isUploading
                            ) {
                                if (state.isUploading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.Default.UploadFile, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("上传简历")
                                }
                            }
                        }
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        if (selectionMode) {
                            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                Text(
                                    text = "请选择一份简历，返回后会作为当前简历使用",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = "搜索")
                            },
                            placeholder = {
                                Text("搜索简历名称、标签或关联说明")
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "支持新增、查看、重命名、删除，以及将版本设为当前简历",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (state.error != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = state.error!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        if (filteredItems.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (query.isBlank()) "暂无简历记录" else "没有匹配到相关简历",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item { Spacer(Modifier.height(4.dp)) }

                                val grouped = filteredItems.groupBy {
                                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it.createdAt))
                                }
                                grouped.forEach { (date, items) ->
                                    item {
                                        Text(
                                            date,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                                        )
                                    }
                                    items(items, key = { "${it.type}_${it.id}" }) { item ->
                                        ResumeItemCard(
                                            item = item,
                                            selectionMode = selectionMode,
                                            onPrimaryClick = {
                                                if (selectionMode && item.type == "version") {
                                                    viewModel.selectResumeForOptimize(item.id)
                                                    onResumeSelected(item.id)
                                                } else if (item.type == "history") {
                                                    onNavigateToHistoryPreview(item.id)
                                                } else {
                                                    onNavigateToPreview(item.id)
                                                }
                                            },
                                            onViewClick = {
                                                if (item.type == "history") {
                                                    onNavigateToHistoryPreview(item.id)
                                                } else {
                                                    onNavigateToPreview(item.id)
                                                }
                                            },
                                            onActivateClick = {
                                                if (item.type == "version") {
                                                    viewModel.activateVersion(item.id)
                                                }
                                            },
                                            onRenameClick = {
                                                renameTarget = item
                                                renameText = item.title
                                            },
                                            onDeleteClick = { deleteTarget = item }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResumeItemCard(
    item: ResumeLibraryItem,
    selectionMode: Boolean,
    onPrimaryClick: () -> Unit,
    onViewClick: () -> Unit,
    onActivateClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val icon = when (item.type) {
        "history" -> Icons.Default.Work
        else -> Icons.Default.Description
    }
    val primaryLabel = if (selectionMode && item.type == "version") "选择" else "查看"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPrimaryClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    icon,
                    null,
                    Modifier
                        .size(28.dp)
                        .padding(4.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (item.isActive) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "当前",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (item.subtitle.isNotBlank()) {
                        Text(
                            text = item.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (item.matchScore > 0) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${item.matchScore}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            item.matchScore >= 80 -> MaterialTheme.colorScheme.primary
                            item.matchScore >= 50 -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.error
                        }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(onClick = onPrimaryClick) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(primaryLabel)
                }
                if (selectionMode && item.type == "version") {
                    OutlinedButton(onClick = onViewClick) {
                        Text("预览")
                    }
                }
                if (item.type == "version" && !selectionMode) {
                    OutlinedButton(
                        onClick = onActivateClick,
                        enabled = !item.isActive
                    ) {
                        Text(if (item.isActive) "当前简历" else "设为当前")
                    }
                }
                OutlinedButton(onClick = onRenameClick) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("重命名")
                }
                OutlinedButton(onClick = onDeleteClick) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("删除")
                }
            }
        }
    }
}

@Composable
private fun RenameResumeDialog(
    title: String,
    itemTypeLabel: String,
    onTitleChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名$itemTypeLabel") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = onTitleChange,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("请输入新的名称") }
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = title.isNotBlank()) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun DeleteResumeDialog(
    itemTitle: String,
    itemTypeLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除$itemTypeLabel") },
        text = {
            Text("确认删除“$itemTitle”吗？删除后无法恢复。")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
