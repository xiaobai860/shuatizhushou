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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Upload
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

/** 题库列表的轻量统计（同步计算：纯内存分组，微秒级）。 */
private data class BankStats(
    val wrongCountByBank: Map<String, Int>,
    val masteryTarget: Int,
    val maxStreakByBank: Map<String, Int>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(navController: NavController) {
    val repo = repository()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var banks by remember { mutableStateOf(emptyList<QuestionBank>()) }
    var searchKeyword by remember { mutableStateOf("") }
    var showDelete by remember { mutableStateOf<QuestionBank?>(null) }
    var showCleanSample by remember { mutableStateOf(false) }
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
    val hasSample = remember(banks) { banks.any { it.id.startsWith("sample_") } }

    // 题库列表的统计信息：一次性批量计算，避免在 LazyColumn 的每个 item 内
    // 重复做全量遍历（错题计数、掌握度、疑似错题）与 O(n²) 查重。
    // 之前每个卡片都调用 findDuplicateGroups，N 个题库即 N 次查重，是列表卡顿的主因。

    // 轻量统计（错题/掌握度）：纯内存分组，微秒级，可同步计算。
    val syncStats = remember(banks) {
        val wrongCount = repo.getWrongRecords().groupingBy { it.bankId }.eachCount()
        val target = repo.getMasteryTarget()
        val maxStreak = HashMap<String, Int>()
        repo.getMasteryStreaks().forEach { (k, v) ->
            val bankId = k.substringBefore("::")
            if (v > (maxStreak[bankId] ?: 0)) maxStreak[bankId] = v
        }
        BankStats(wrongCount, target, maxStreak)
    }
    val wrongCountByBank = syncStats.wrongCountByBank
    val masteryTarget = syncStats.masteryTarget
    val maxStreakByBank = syncStats.maxStreakByBank

    // 徽章数据全部来自题库持久化字段（导入时算好；题目增删改后置 -1）。
    // 本页对「失效（-1）」的题库在进入时后台逐个补算并写回，一次只算一个，
    // 避免集中跑 O(n²)；未失效的题库零计算。不进本页则永不触发检测。
    var recomputedCount by remember { mutableStateOf(0) }   // 补算进度；变化触发重组刷新徽章
    val staleBanks = banks.filter { it.dupCount < 0 || it.suspectCount < 0 }
    if (staleBanks.isNotEmpty()) {
        val staleKeys = staleBanks.map { it.id to it.updatedAt }
        LaunchedEffect(staleKeys) {
            for (bank in staleBanks) {
                withContext(Dispatchers.Default) {
                    repo.computeAndStoreCounts(bank.id)
                }
                recomputedCount++
            }
        }
    }
    Scaffold(

        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = {
            // 统一两分支高度：加 statusBarsPadding + 固定 52dp（与 ScreenTopBar 一致）。
            // 否则进入/退出多选模式时 topBar 高度变化会让下方列表整块上下跳动。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(52.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectedIds.isEmpty()) {
                    ScreenTopBar("题库管理", actions = {
                        if (hasSample) {
                            TextButton(onClick = { showCleanSample = true }) {
                                Text("清理测试数据", color = MaterialTheme.colorScheme.error)
                            }
                        }
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
                items(filtered, key = { it.id }) { bank ->
                    val isSelected = selectedIds.contains(bank.id)
                    val progress = repo.getProgress(bank.id)
                    val completed = progress?.everCompleted ?: false
                    // 以下统计改为查预计算好的 Map（见上方 statsByBank），
                    // 避免在 items 内对每个题库重复做全量遍历与 O(n²) 查重。
                    val wrongCount = wrongCountByBank[bank.id] ?: 0
                    val target = masteryTarget
                    val maxStreak = maxStreakByBank[bank.id] ?: 0
                    val pct = if (target > 0) (maxStreak.toFloat() / target).coerceIn(0f, 1f) else 0f
                    // dupCount 为持久化字段（-1=补算中），读不到时显示 0 避免闪烁
                    val dupCount = bank.dupCount.coerceAtLeast(0)
                    // 补算完成后 updateBank 改写 banks 触发重组，此处自动读到新值；-1 期间显示 0 避免闪烁
                    val suspectCount = bank.suspectCount.coerceAtLeast(0)

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

    if (showCleanSample) {
        val sampleCount = banks.count { it.id.startsWith("sample_") }
        AlertDialog(
            onDismissRequest = { showCleanSample = false },
            title = { Text("清理测试数据") },
            text = {
                Text(
                    "将删除 $sampleCount 个「示例」题库及其错题、收藏、历史、计划等全部关联数据，且不可恢复。\n" +
                        "此操作仅移除测试数据，不影响你自建的题库。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val n = repo.removeSampleData()
                        loadBanks()
                        snackbarHostState.showSnackbar("已清理 $n 个示例题库")
                    }
                    showCleanSample = false
                }) { Text("清理", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showCleanSample = false }) { Text("取消") }
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
