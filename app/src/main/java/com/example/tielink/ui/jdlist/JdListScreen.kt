package com.example.tielink.ui.jdlist

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.tielink.data.local.db.entity.JdLibraryEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JdListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPolish: (resumeText: String, jdRawText: String, jdStructuredJson: String,
                          templatePath: String?, sourceType: String, fullPolish: Boolean) -> Unit,
    onNavigateToTracking: (jdCompany: String, jdPosition: String) -> Unit,
    onNavigateToGreeting: (jdRawText: String, jdCompany: String) -> Unit,
    selectionMode: Boolean = false,
    onJdSelected: (Long) -> Unit = {},
    viewModel: JdListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // [+] menu
    var showAddMenu by remember { mutableStateOf(false) }
    var showBossImportDialog by remember { mutableStateOf(false) }
    // text input dialog
    var showTextDialog by remember { mutableStateOf(false) }
    var textInput by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var sourceFilter by remember { mutableStateOf("全部") }

    val ocrLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) viewModel.addFromImage(context, uri)
    }

    // JD detail dialog state
    var detailJd by remember { mutableStateOf<JdLibraryEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("JD 库", fontWeight = FontWeight.Medium) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showAddMenu = true }) {
                            Icon(Icons.Default.Add, "添加 JD")
                        }
                        DropdownMenu(
                            expanded = showAddMenu,
                            onDismissRequest = { showAddMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("输入文字") },
                                onClick = { showAddMenu = false; showTextDialog = true },
                                leadingIcon = { Icon(Icons.Default.Description, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("图片 OCR") },
                                onClick = {
                                    showAddMenu = false
                                    ocrLauncher.launch("image/*")
                                },
                                leadingIcon = { Icon(Icons.Default.CameraAlt, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("BOSS 一键导入") },
                                onClick = {
                                    showAddMenu = false
                                    showBossImportDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Search, null) }
                            )
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
                state.error != null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(state.error!!, color = MaterialTheme.colorScheme.error)
                    }
                }
                state.jdList.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("暂无 JD", style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Text("点击右上角 + 添加，或在聊天中直接发送 JD，AI 会自动保存到这里",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                else -> {
                    val visibleJds = remember(state.jdList, searchQuery, sourceFilter) {
                        state.jdList.filter { jd ->
                            val matchesQuery = searchQuery.isBlank() ||
                                jd.companyName.contains(searchQuery, ignoreCase = true) ||
                                jd.positionName.contains(searchQuery, ignoreCase = true) ||
                                jd.skills.contains(searchQuery, ignoreCase = true)
                            val matchesSource = when (sourceFilter) {
                                "BOSS" -> jd.sourceType == "boss_auto"
                                "AI" -> jd.sourceType == "ai_auto"
                                "手动" -> jd.sourceType == "manual" || jd.sourceType == "ocr"
                                else -> true
                            }
                            matchesQuery && matchesSource
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item { Spacer(Modifier.height(4.dp)) }
                        if (selectionMode) {
                            item {
                                Text(
                                    text = "请选择一个 JD 作为当前岗位，返回后 Agent 会直接基于它继续分析。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        item {
                            JdRadarHeader(
                                items = state.jdList,
                                query = searchQuery,
                                onQueryChange = { searchQuery = it },
                                sourceFilter = sourceFilter,
                                onSourceFilterChange = { sourceFilter = it }
                            )
                        }
                        if (visibleJds.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "没有找到匹配的岗位",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        items(visibleJds, key = { it.id }) { jd ->
                            JdCard(
                                jd = jd,
                                selectionMode = selectionMode,
                                isSelected = state.currentJdId == jd.id,
                                onClick = { detailJd = jd },
                                onSelect = {
                                    viewModel.selectJdForAgent(jd.id)
                                    onJdSelected(jd.id)
                                },
                                onDelete = { viewModel.deleteJd(jd) },
                                onGoPolish = {
                                    onNavigateToPolish("", jd.rawText, jd.structuredJson,
                                        null, "file", true)
                                },
                                onGoTracking = {
                                    onNavigateToTracking(jd.companyName, jd.positionName)
                                },
                                onGoGreeting = {
                                    onNavigateToGreeting(jd.rawText, jd.companyName)
                                }
                            )
                        }
                    }
                }
            }

            // processing overlay
            if (state.isProcessing) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 8.dp
                    ) {
                        Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                            Spacer(Modifier.width(16.dp))
                            Text("正在解析 JD...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        // text input dialog
        if (showTextDialog) {
            androidx.compose.ui.window.Dialog(onDismissRequest = { showTextDialog = false }) {
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(20.dp)) {
                        Text("输入 JD 文本", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            modifier = Modifier.fillMaxWidth().height(200.dp),
                            placeholder = { Text("粘贴岗位描述文本...") },
                            maxLines = 10
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { showTextDialog = false }) { Text("取消") }
                            FilledTonalButton(
                                onClick = {
                                    showTextDialog = false
                                    viewModel.addFromText(textInput)
                                    textInput = ""
                                },
                                enabled = textInput.isNotBlank()
                            ) { Text("保存") }
                        }
                    }
                }
            }
        }

        if (showBossImportDialog) {
            BossImportDialog(onDismiss = { showBossImportDialog = false })
        }

        // JD detail dialog
        detailJd?.let { jd ->
            JdDetailDialog(
                jd = jd,
                onDismiss = { detailJd = null },
                onDelete = {
                    viewModel.deleteJd(jd)
                    detailJd = null
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JdRadarHeader(
    items: List<JdLibraryEntity>,
    query: String,
    onQueryChange: (String) -> Unit,
    sourceFilter: String,
    onSourceFilterChange: (String) -> Unit
) {
    val bossCount = items.count { it.sourceType == "boss_auto" }
    val companyCount = items.map { it.companyName.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .size

    Column {
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
                            listOf(Color(0xFF102A43), Color(0xFF174C5B), Color(0xFF16756B))
                        )
                    )
                    .padding(17.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "OPPORTUNITY RADAR",
                            color = Color(0xFF7EE7D8),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "${items.size} 个岗位正在候选池",
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Icon(
                        Icons.Default.Search,
                        null,
                        Modifier.size(32.dp),
                        tint = Color.White.copy(alpha = 0.70f)
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    JdRadarMetric(Modifier.weight(1f), companyCount.toString(), "目标公司")
                    JdRadarMetric(Modifier.weight(1f), bossCount.toString(), "BOSS 导入")
                    JdRadarMetric(
                        Modifier.weight(1f),
                        items.count { it.skills.isNotBlank() }.toString(),
                        "已提取技能"
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("搜索公司、岗位或技能") },
            leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(19.dp)) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp)
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("全部", "BOSS", "AI", "手动").forEach { filter ->
                FilterChip(
                    selected = sourceFilter == filter,
                    onClick = { onSourceFilterChange(filter) },
                    label = { Text(filter) }
                )
            }
        }
    }
}

@Composable
private fun JdRadarMetric(
    modifier: Modifier,
    value: String,
    label: String
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(13.dp),
        color = Color.White.copy(alpha = 0.10f)
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
            Text(value, color = Color.White, fontWeight = FontWeight.ExtraBold)
            Text(
                label,
                color = Color.White.copy(alpha = 0.62f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JdCard(
    jd: JdLibraryEntity,
    selectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onGoPolish: () -> Unit,
    onGoTracking: () -> Unit,
    onGoGreeting: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MM-dd", Locale.getDefault()) }
    val skills = jd.skills.takeIf { it.isNotBlank() }?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    val sourceLabel = when (jd.sourceType) {
        "ai_auto" -> "AI 自动保存"
        "ocr" -> "图片 OCR"
        "boss_auto" -> "BOSS 自动导入"
        else -> "手动录入"
    }
    val sourceColor = when (jd.sourceType) {
        "boss_auto" -> Color(0xFF0F766E)
        "ai_auto" -> Color(0xFF0E7490)
        "ocr" -> Color(0xFFB45309)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(14.dp)) {
            // Header row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = RoundedCornerShape(13.dp),
                    color = sourceColor.copy(alpha = 0.11f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            jd.companyName.trim().take(1).ifBlank { "岗" },
                            color = sourceColor,
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = jd.positionName.ifBlank { "未识别岗位" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    if (jd.companyName.isNotBlank() || jd.salary.isNotBlank()) {
                        val subtitleParts = listOfNotNull(
                            jd.companyName.takeIf { it.isNotBlank() },
                            jd.salary.takeIf { it.isNotBlank() }
                        )
                        Text(
                            text = subtitleParts.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = sourceColor.copy(alpha = 0.10f)
                ) {
                    Text(
                        text = sourceLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = sourceColor,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (isSelected) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "当前 JD",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Skills row
            if (skills.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    skills.take(6).forEach { skill ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = skill.trim(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(6.dp))

            // Action buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (selectionMode) {
                    FilledTonalButton(
                        onClick = onSelect,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        enabled = !isSelected
                    ) {
                        Text(
                            if (isSelected) "已选择" else "选择",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    OutlinedButton(
                        onClick = onClick,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("查看详情", style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    FilledTonalButton(onClick = onGoPolish, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)) {
                        Icon(Icons.Default.AutoAwesome, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("去润色", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(onClick = onGoGreeting, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)) {
                        Text("打招呼", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(onClick = onGoTracking, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)) {
                        Text("去投递", style = MaterialTheme.typography.labelSmall)
                    }
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        "删除",
                        Modifier.size(17.dp),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.58f)
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                dateFormat.format(Date(jd.createdAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JdDetailDialog(jd: JdLibraryEntity, onDismiss: () -> Unit, onDelete: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val sourceLabel = when (jd.sourceType) {
        "ai_auto" -> "AI 自动保存"
        "ocr" -> "图片 OCR"
        "boss_auto" -> "BOSS 自动导入"
        else -> "手动录入"
    }
    val skills = jd.skills.takeIf { it.isNotBlank() }?.split(",")?.filter { it.isNotBlank() } ?: emptyList()

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(0.95f)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = jd.companyName.ifBlank { "未识别公司" },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (jd.positionName.isNotBlank() || jd.salary.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            val detailParts = listOfNotNull(
                                jd.positionName.takeIf { it.isNotBlank() },
                                jd.salary.takeIf { it.isNotBlank() }
                            )
                            Text(
                                text = detailParts.joinToString(" · "),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Meta chips
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Text(sourceLabel, style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Text(dateFormat.format(Date(jd.createdAt)), style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }

                // Skills
                if (skills.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("技能标签", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        skills.forEach { skill ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            ) {
                                Text(
                                    text = skill.trim(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                // Raw text
                Spacer(Modifier.height(12.dp))
                Text("原始内容", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Text(
                        text = jd.rawText.ifBlank { "(无内容)" },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Close + Delete buttons
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Delete, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("删除")
                    }
                    FilledTonalButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) { Text("关闭") }
                }
            }
        }
    }
}
