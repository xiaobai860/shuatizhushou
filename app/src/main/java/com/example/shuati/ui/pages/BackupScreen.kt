package com.example.shuati.ui.pages

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.shuati.model.BackupData
import com.example.shuati.ui.*
import com.example.shuati.ui.components.CardBox
import com.example.shuati.ui.components.HeroCard
import com.example.shuati.ui.components.ListRow
import com.example.shuati.ui.components.ScreenTopBar
import com.example.shuati.ui.components.SectionTitle
import com.example.shuati.ui.components.SoftButton
import com.example.shuati.ui.theme.ShuatiSpacing
import com.example.shuati.ui.theme.semantic
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(navController: NavController) {
    val repo = repository()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf("") }
    // true=覆盖还原（清空替换）；false=合并还原（保留现有、跳过重名）
    var overwrite by remember { mutableStateOf(false) }
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
                    status = if (r.message != null) r.message!!
                    else "还原完成：导入 ${r.imported} 个题库" +
                            (if (r.skipped > 0) "，跳过 ${r.skipped} 个重名" else "")
                } catch (e: Exception) { status = "还原失败：${e.message}" }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ScreenTopBar("备份与还原", onBack = { navController.popBackStack() }, subtitle = "全量数据 · JSON 格式")
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ShuatiSpacing.PageHorizontal),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 上次备份 Hero
            HeroCard {
                Column {
                    Text("上次备份", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        if (status.contains("导出")) "刚刚" else "尚未备份过",
                        fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.4).sp, color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(5.dp))
                    Text("题库、错题、收藏、历史、计划、进度与设置全量导出",
                        fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(14.dp))
                    SoftButton(
                        text = "立即备份",
                        icon = Icons.Filled.Upload,
                        compact = true,
                        modifier = Modifier.width(128.dp),
                        onClick = {
                            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "application/json"
                                putExtra(Intent.EXTRA_TITLE, "shuati_backup_${System.currentTimeMillis()}.json")
                            }
                            exportLauncher.launch(intent)
                        }
                    )
                }
            }

            // 备份内容
            Spacer(Modifier.height(2.dp))
            SectionTitle("备份内容", count = "7 类")
            CardBox {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                    BackupTag("题库")
                    BackupTag("题目")
                    BackupTag("错题")
                    BackupTag("收藏")
                    BackupTag("历史")
                    BackupTag("计划")
                    BackupTag("设置")
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "备份文件为 JSON 格式，仅保存在本机，不会上传云端。换机时手动拷贝即可。",
                    fontSize = 11.5.sp, lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 还原备份
            Spacer(Modifier.height(2.dp))
            SectionTitle("还原备份")
            Column(Modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface)) {
                ListRow(
                    icon = { Icon(Icons.Filled.Folder, null, Modifier.size(18.dp)) },
                    iconBackground = MaterialTheme.colorScheme.surfaceContainer,
                    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    title = "从文件管理器选择",
                    subtitle = "选择 .json 备份文件",
                    trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "application/json"
                        }
                        importLauncher.launch(intent)
                    },
                    showDivider = false
                )
            }

            // 还原方式
            Spacer(Modifier.height(2.dp))
            SectionTitle("还原方式")
            CardBox {
                RestoreOption("合并还原", "保留现有数据，补充新数据", selected = !overwrite, onClick = { overwrite = false })
                Spacer(Modifier.height(4.dp))
                RestoreOption("覆盖还原", "清空现有数据后完全替换", selected = overwrite, onClick = { overwrite = true })
            }
            if (overwrite) {
                Surface(
                    color = MaterialTheme.semantic.warningContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("覆盖还原不可撤销", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.semantic.onWarningContainer)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "当前的全部题库、错题、收藏与历史将被删除。建议先执行一次备份。",
                            fontSize = 12.sp, lineHeight = 18.sp,
                            color = MaterialTheme.semantic.onWarningContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            // 危险操作
            Spacer(Modifier.height(2.dp))
            SectionTitle("危险操作")
            CardBox {
                Column {
                    Text("清空所有数据", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.semantic.onErrorContainer)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "删除全部题库、错题、收藏、历史、计划与进度。此操作无法撤销。",
                        fontSize = 11.5.sp, lineHeight = 18.sp,
                        color = MaterialTheme.semantic.onErrorContainer.copy(alpha = 0.85f)
                    )
                    Spacer(Modifier.height(12.dp))
                    SoftButton(
                        text = "清空所有数据",
                        icon = Icons.Filled.Delete,
                        compact = true,
                        modifier = Modifier.width(160.dp),
                        onClick = { showClearDialog = true }
                    )
                }
            }

            // 状态提示
            if (status.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(status, Modifier.padding(12.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }

            Spacer(Modifier.height(8.dp))
            SoftButton(
                text = "导出备份到文件",
                icon = Icons.Filled.Backup,
                onClick = {
                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "application/json"
                        putExtra(Intent.EXTRA_TITLE, "shuati_backup_${System.currentTimeMillis()}.json")
                    }
                    exportLauncher.launch(intent)
                }
            )
            Spacer(Modifier.height(20.dp))
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
                }) { Text("清空", color = MaterialTheme.semantic.error) }
            },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("取消") } }
        )
    }
}

/** 备份内容标签 */
@Composable
private fun BackupTag(text: String) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(8.dp)) {
        Text(text, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

/** 还原方式选项（单选） */
@Composable
private fun RestoreOption(label: String, desc: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(6.dp))
        Column {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(desc, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
