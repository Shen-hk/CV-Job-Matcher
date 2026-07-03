package com.example.tielink.ui.jdlist

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.tielink.automation.BossImportController

@Composable
fun BossImportDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val importState by BossImportController.state.collectAsState()
    var keyword by remember { mutableStateOf(importState.keyword) }
    var limitText by remember { mutableStateOf(importState.limit.toString()) }
    val accessibilityEnabled = BossImportController.isAccessibilityEnabled(context)
    val accessibilitySettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (
            BossImportController.isAccessibilityEnabled(context) &&
            keyword.isNotBlank()
        ) {
            val limit = limitText.toIntOrNull()?.coerceIn(1, 20) ?: 5
            if (BossImportController.start(context, keyword, limit)) onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("BOSS 一键导入") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text("自动打开 BOSS 搜索岗位并保存到 JD 库。首次使用需开启 TieLink 无障碍服务。")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("岗位关键词") },
                    placeholder = { Text("例如：Android 开发") },
                    singleLine = true,
                    enabled = !importState.running
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = limitText,
                    onValueChange = { value ->
                        limitText = value.filter(Char::isDigit).take(2)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("导入数量（1-20）") },
                    singleLine = true,
                    enabled = !importState.running,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                if (importState.message.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(importState.message)
                }
            }
        },
        confirmButton = {
            if (importState.running) {
                Button(onClick = {
                    BossImportController.stop(context)
                    onDismiss()
                }) {
                    Text("停止")
                }
            } else if (!accessibilityEnabled) {
                Button(onClick = {
                    accessibilitySettingsLauncher.launch(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    )
                }) {
                    Text("开启权限")
                }
            } else {
                Button(
                    onClick = {
                        val limit = limitText.toIntOrNull()?.coerceIn(1, 20) ?: 5
                        if (BossImportController.start(context, keyword, limit)) onDismiss()
                    },
                    enabled = keyword.isNotBlank()
                ) {
                    Text("开始导入")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}
