package com.example.shuati.ui.pages

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.shuati.logic.ExcelWriter
import com.example.shuati.logic.Scoring
import com.example.shuati.model.QuestionBank
import com.example.shuati.ui.*
import com.example.shuati.ui.components.CardBox
import com.example.shuati.ui.components.EmptyState
import com.example.shuati.ui.components.ScreenTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(navController: NavController) {
    val repo = repository()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var banks by remember { mutableStateOf(emptyList<QuestionBank>()) }
    var searchKeyword by remember { mutableStateOf("") }
    var showDelete by remember { mutableStateOf<QuestionBank?>(null) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val context = LocalContext.current

    // 导出题库 Excel（SAF 创建文件）
    var exportBank by remember { mutableStateOf<QuestionBank?>(null) }
    var exportMsg by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )
    ) { uri ->
        val bank = exportBank
        exportBank = null
        if (bank == null || uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            exportMsg = "正在导出…"
            val ok = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        ExcelWriter.writeBank(bank, out)
                    } != null
                } catch (_: Exception) { false }
            }
            exportMsg = if (ok) "已导出 ${bank.name}.xlsx" else "导出失败，请重试"
        }
    }

    fun loadBanks() {
        banks = repo.getBanks()
    }
    LaunchedEffect(Unit) { loadBanks() }

    val filtered = remember(banks, searchKeyword) {
        if (searchKeyword.isBlank()) banks
        else banks.filter {
            it.name.contains(searchKeyword, ignoreCase = true) ||
                    it.questions.any { q -> q.question.contains(searchKeyword, ignoreCase = true) }
        }
    }

    Scaffold(

        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectedIds.isEmpty()) {
                    ScreenTopBar("题库管理", actions = {
                        TextButton(onClick = { navController.navigate(Routes.IMPORT) }) { Text("导入") }
                    })
                } else {
                    Text(
                        "${selectedIds.size} 已选",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { selectedIds = emptySet() }) {
                        Text("取消", color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        },
        floatingActionButton = {
            if (selectedIds.isEmpty()) {
                FloatingActionButton(onClick = { navController.navigate(Routes.IMPORT) }) {
                    Icon(Icons.Filled.Upload, contentDescription = "导入题库")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LaunchedEffect(exportMsg) {
            if (exportMsg != null) {
                snackbarHostState.showSnackbar(exportMsg!!)
                exportMsg = null
            }
        }
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = searchKeyword,
                onValueChange = { searchKeyword = it },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                placeholder = { Text("搜索题库 / 题目") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = if (searchKeyword.isNotEmpty()) {
                    { IconButton(onClick = { searchKeyword = "" }) { Icon(Icons.Filled.Clear, contentDescription = "清除") } }
                } else null
            )
            if (selectedIds.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = {
                        selectedIds.forEach { id -> scope.launch { repo.deleteBank(id) } }
                        loadBanks()
                        selectedIds = emptySet()
                    }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("删除")
                    }
                    if (selectedIds.size > 1) {
                        val batchIds = selectedIds.toList()
                        Button(onClick = {
                            val firstBank = banks.find { it.id == batchIds.first() }
                            if (firstBank != null) {
                                navController.navigate("${Routes.QUIZ}?bankId=${firstBank.id}&mode=sequence&source=all&showModeSelect=true")
                            }
                        }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("批量刷 ${batchIds.size} 个题库")
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                if (filtered.isEmpty()) {
                    item { EmptyState(if (banks.isEmpty()) "还没有题库，点右下角 + 导入" else "没有匹配的题库") }
                }
                items(filtered) { bank ->
                    val isSelected = selectedIds.contains(bank.id)
                    val progress = repo.getProgress(bank.id)
                    val completed = progress?.everCompleted ?: false
                    val wrongCount = repo.getWrongRecords().count { it.bankId == bank.id }
                    val target = repo.getMasteryTarget()
                    val bankQuestions = bank.questions.map { it.id }.toSet()
                    val maxStreak = repo.getMasteryStreaks()
                        .filterKeys { it.startsWith("${bank.id}::") }
                        .values.maxOrNull() ?: 0
                    val pct = if (target > 0) (maxStreak.toFloat() / target).coerceIn(0f, 1f) else 0f
                    val dupGroups = repo.findDuplicateGroups(bank, repo.getDedupThreshold())
                    val dupCount = dupGroups.sumOf { it.size } - dupGroups.size
                    val suspectCount = bank.questions.count { Scoring.checkIsSuspect(it) }

                    CardBox(
                        onClick = {
                            if (selectedIds.isEmpty()) {
                                navController.navigate("${Routes.EDIT_BANK}?bankId=${bank.id}")
                            } else {
                                selectedIds = if (isSelected) selectedIds - bank.id else selectedIds + bank.id
                            }
                        },
                        modifier = Modifier.then(if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium) else Modifier)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (selectedIds.isNotEmpty()) {
                                RadioButton(selected = isSelected, onClick = {
                                    selectedIds = if (isSelected) selectedIds - bank.id else selectedIds + bank.id
                                })
                                Spacer(Modifier.width(8.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(bank.name, fontWeight = FontWeight.Medium, fontSize = 17.sp)
                                Spacer(Modifier.height(4.dp))
                                Text("${bank.questions.size} 题" + if (wrongCount > 0) " · $wrongCount 错题" else "",
                                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (bank.updatedAt > 0) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "更新于 " + java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
                                            .format(java.util.Date(bank.updatedAt)),
                                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    )
                                }
                                if (completed) {
                                    Spacer(Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        AssistChip(
                                            onClick = {},
                                            label = { Text("已刷完 100%") },
                                            leadingIcon = { Icon(Icons.Filled.CheckCircle, null, Modifier.size(16.dp)) },
                                            colors = AssistChipDefaults.assistChipColors(
                                                containerColor = MaterialTheme.colorScheme.primaryContainer
                                            )
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        TextButton(onClick = {
                                            repo.clearProgress(bank.id)
                                            loadBanks()
                                        }) {
                                            Text("清除进度", fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                                if (!completed && bank.questions.isNotEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = { pct },
                                        modifier = Modifier.fillMaxWidth().height(4.dp)
                                            .clip(MaterialTheme.shapes.small),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    Text("${(pct * 100).toInt()}% 掌握进度",
                                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (dupCount > 0) {
                                    LibraryBadge("重复$dupCount", MaterialTheme.colorScheme.tertiary)
                                }
                                if (suspectCount > 0) {
                                    Spacer(Modifier.width(4.dp))
                                    LibraryBadge("疑错$suspectCount", MaterialTheme.colorScheme.error)
                                }
                            }
                            Spacer(Modifier.width(4.dp))
                            // 删除按钮
                            IconButton(onClick = { showDelete = bank }) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "删除题库",
                                    tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                navController.navigate("${Routes.QUIZ}?bankId=${bank.id}&mode=sequence&source=bank&showModeSelect=true")
                            }) { Text("刷题") }
                            OutlinedButton(onClick = {
                                navController.navigate("${Routes.QUIZ}?bankId=${bank.id}&mode=sequence&source=all")
                            }) { Text("顺序") }
                            OutlinedButton(onClick = {
                                navController.navigate("${Routes.QUIZ}?bankId=${bank.id}&mode=random&source=all")
                            }) { Text("随机") }
                        }
                        if (wrongCount > 0) {
                            Spacer(Modifier.height(6.dp))
                            Button(
                                onClick = { navController.navigate("${Routes.QUIZ}?bankId=${bank.id}&source=wrong&showModeSelect=true") },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("只练 $wrongCount 道错题") }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = { exportBank = bank; exportLauncher.launch("${bank.name}.xlsx") },
                                modifier = Modifier.weight(1f).height(38.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) {
                                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(3.dp))
                                Text("导出", fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = { navController.navigate("${Routes.DEDUP}?bankId=${bank.id}") },
                                modifier = Modifier.weight(1f).height(38.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) { Text("查重", fontSize = 12.sp) }
                            OutlinedButton(
                                onClick = { navController.navigate("${Routes.QUIZ}?bankId=${bank.id}&mode=browse&source=browse") },
                                modifier = Modifier.weight(1f).height(38.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) { Text("浏览", fontSize = 12.sp) }
                        }
                    }
                }
            }
        }
    }

    if (showDelete != null) {
        val bank = showDelete!!
        AlertDialog(
            onDismissRequest = { showDelete = null },
            title = { Text("删除题库") },
            text = { Text("确定要删除「${bank.name}」吗？\n此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repo.deleteBank(bank.id)
                        loadBanks()
                    }
                    showDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = null }) { Text("取消") }
            }
        )
    }
}

/** 题库卡片的小徽章（如"重N"重复题、"错N"疑似错题） */
@Composable
private fun LibraryBadge(text: String, color: Color) {
    Surface(color = color, shape = RoundedCornerShape(50), contentColor = Color.White) {
        Text(
            text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}
