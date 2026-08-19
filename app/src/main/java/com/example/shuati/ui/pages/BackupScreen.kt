package com.example.shuati.ui.pages

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.shuati.model.BackupData
import com.example.shuati.ui.*
import com.example.shuati.ui.components.CardBox
import com.example.shuati.ui.components.ScreenTopBar
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(navController: NavController) {
    val repo = repository()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf("") }
    var overwrite by remember { mutableStateOf(true) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }

    // 导出
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (uri != null) {
            scope.launch {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(repo.exportBackupJson().toByteArray(Charsets.UTF_8))
                    }
                    status = "备份已导出"
                } catch (e: Exception) { status = "导出失败：${e.message}" }
            }
        }
    }

    // 导入
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (uri != null) {
            scope.launch {
                try {
                    val txt = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() } ?: ""
                    val data = Json { ignoreUnknownKeys = true }.decodeFromString<BackupData>(txt)
                    val r = repo.restoreBackup(data, overwrite)
                    status = if (r.message != null) r.message!! else "还原完成：导入 ${r.imported} 个题库" +
                            (if (r.skipped > 0) "，跳过 ${r.skipped} 个重名" else "")
                } catch (e: Exception) { status = "还原失败：${e.message}" }
            }
        }
    }

    Scaffold(topBar = { ScreenTopBar("备份与还原", onBack = { navController.popBackStack() }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CardBox {
                Text("导出备份", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(6.dp))
                Text("将所有题库、错题、收藏、历史、计划等导出为 JSON 文件。", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Button(onClick = {
                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "application/json"
                        putExtra(Intent.EXTRA_TITLE, "shuati_backup_${System.currentTimeMillis()}.json")
                    }
                    exportLauncher.launch(intent)
                }) { Icon(Icons.Filled.Backup, null); Spacer(Modifier.width(8.dp)); Text("导出到文件") }
            }

            CardBox {
                Text("从备份还原", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = overwrite, onCheckedChange = { overwrite = it })
                    Text("覆盖同名题库（取消则跳过重名）", fontSize = 13.sp)
                }
                Spacer(Modifier.height(10.dp))
                Button(onClick = {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "application/json"
                    }
                    importLauncher.launch(intent)
                }) { Icon(Icons.Filled.Restore, null); Spacer(Modifier.width(8.dp)); Text("选择备份文件") }
            }

            CardBox {
                Text("清空所有数据", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(6.dp))
                Text("删除所有题库、错题、收藏、历史、计划等数据，不可恢复。", fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { showClearDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) { Text("清空所有数据", color = MaterialTheme.colorScheme.error) }
            }

            if (status.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(status, Modifier.padding(12.dp), fontSize = 14.sp) }
            }

            Spacer(Modifier.weight(1f))
            Text("提示：备份文件可用于换机迁移或多人共享题库。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    // 清空数据确认弹窗（与小程序一致：不可恢复）
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空所有数据") },
            text = { Text("此操作将删除所有题库、错题、收藏、历史记录、学习计划、进度及偏好设置，不可恢复！确定清空吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    repo.clearAllData()
                    status = "已清空全部数据"
                }) { Text("清空", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("取消") } }
        )
    }
}
