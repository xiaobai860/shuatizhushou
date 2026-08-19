package com.example.shuati.ui.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.shuati.model.QuestionBank
import com.example.shuati.ui.*
import com.example.shuati.ui.components.CardBox
import com.example.shuati.ui.components.EmptyState

/**
 * 底部导航的三个顶层页：题库中心 / 学习中心 / 我的。
 * 首页 HomeScreen 单独维护，这里承载其余功能的分类入口。
 */

// ─────────────────────────── 题库中心 ───────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryTabScreen(navController: NavController) {
    val repo = repository()
    var banks by remember { mutableStateOf(emptyList<QuestionBank>()) }
    LaunchedEffect(Unit) { banks = repo.getBanks() }
    val totalQuestions = banks.sumOf { it.questions.size }

    Scaffold(topBar = { TopAppBar(title = { Text("题库中心", fontWeight = FontWeight.SemiBold) }) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("题库", banks.size.toString(), Modifier.weight(1f))
                    StatCard("题目", totalQuestions.toString(), Modifier.weight(1f))
                }
            }
            item {
                Text("题库操作", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FeatureTile(Icons.Filled.Book, "题库管理", Modifier.weight(1f)) { navController.navigate(Routes.LIBRARY) }
                    FeatureTile(Icons.Filled.Upload, "导入题库", Modifier.weight(1f)) { navController.navigate(Routes.IMPORT) }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FeatureTile(Icons.Filled.BugReport, "查重检测", Modifier.weight(1f)) { navController.navigate(Routes.DEDUP) }
                    FeatureTile(Icons.Filled.AutoStories, "全部题库", Modifier.weight(1f)) {
                        navController.navigate("${Routes.QUIZ}?bankId=__all__&mode=sequence&source=all&showModeSelect=true")
                    }
                }
            }
            item {
                Text("我的题库", fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
            }
            if (banks.isEmpty()) {
                item { EmptyState("还没有题库，去「导入题库」添加吧") }
            } else {
                items(banks.sortedByDescending { it.updatedAt }) { bank ->
                    val progress = repo.getProgress(bank.id)
                    val answered = progress?.answeredIds?.size ?: 0
                    val total = bank.questions.size
                    CardBox(onClick = { navController.navigate("${Routes.EDIT_BANK}?bankId=${bank.id}") }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(bank.name, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                                Text("$total 题" + if (answered > 0) " · 已答 $answered" else "",
                                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = {
                                navController.navigate("${Routes.QUIZ}?bankId=${bank.id}&mode=sequence&source=bank&showModeSelect=true")
                            }) { Text("刷题") }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────── 学习中心 ───────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyTabScreen(navController: NavController) {
    val repo = repository()
    var wrongCount by remember { mutableStateOf(0) }
    var favCount by remember { mutableStateOf(0) }
    var historyCount by remember { mutableStateOf(0) }
    var planCount by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        wrongCount = repo.getWrongRecords().size
        favCount = repo.getFavoriteRecords().size
        historyCount = repo.getHistoryRecords().size
        planCount = repo.getStudyPlans().count { !it.archived }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("学习中心", fontWeight = FontWeight.SemiBold) }) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                Text("智能推荐", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                CardBox(onClick = { navController.navigate(Routes.RECOMMEND) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Star, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("智能推荐", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text("根据错题、收藏等自动推荐需复习的题目", fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Text("学习记录", fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                    modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FeatureTile(Icons.Filled.CalendarMonth, "学习计划", Modifier.weight(1f),
                        badge = if (planCount > 0) planCount.toString() else null) { navController.navigate(Routes.STUDY_PLAN) }
                    FeatureTile(Icons.Filled.ErrorOutline, "错题回顾", Modifier.weight(1f),
                        badge = if (wrongCount > 0) wrongCount.toString() else null) { navController.navigate(Routes.WRONGS) }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FeatureTile(Icons.Filled.Star, "收藏题目", Modifier.weight(1f),
                        badge = if (favCount > 0) favCount.toString() else null) { navController.navigate(Routes.FAVORITES) }
                    FeatureTile(Icons.Filled.History, "刷题历史", Modifier.weight(1f),
                        badge = if (historyCount > 0) historyCount.toString() else null) { navController.navigate(Routes.HISTORY) }
                }
            }
        }
    }
}

// ─────────────────────────── 我的 ───────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MineTabScreen(navController: NavController) {
    val repo = repository()
    val ctx = LocalContext.current
    var storageUsed by remember { mutableStateOf(0L) }
    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull()
    }
    LaunchedEffect(Unit) { storageUsed = repo.storageUsedBytes() }

    Scaffold(topBar = { TopAppBar(title = { Text("我的", fontWeight = FontWeight.SemiBold) }) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                Text("数据管理", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FeatureTile(Icons.Filled.BarChart, "统计分析", Modifier.weight(1f)) { navController.navigate(Routes.STATS) }
                    FeatureTile(Icons.Filled.Search, "题目搜索", Modifier.weight(1f)) { navController.navigate(Routes.SEARCH) }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FeatureTile(Icons.Filled.Backup, "备份还原", Modifier.weight(1f)) { navController.navigate(Routes.BACKUP) }
                    Spacer(Modifier.weight(1f))
                    // 占位保持布局对称
                    Spacer(Modifier.weight(1f))
                }
            }
            item {
                Text("存储空间", fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                    modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(8.dp))
                CardBox {
                    val usedKb = storageUsed / 1024.0
                    val limitKb = 10240.0
                    val ratio = (usedKb / limitKb).toFloat().coerceIn(0f, 1f)
                    val warning = usedKb / limitKb >= 0.7
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Storage, null,
                            tint = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("本地数据", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatBytes(storageUsed), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        if (warning) {
                            Text("占用较多", fontSize = 12.sp, color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Medium)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { ratio },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (warning) "本地数据占用较多，可在「备份还原」中清理或导出备份"
                        else "数据保存在本机，可在「备份还原」中导出备份",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    Text("刷题 v$version", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ─────────────────────────── 通用组件 ───────────────────────────

/** 功能入口卡片：图标 + 文字 + 可选数量角标 */
@Composable
private fun FeatureTile(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    badge: String? = null,
    onClick: () -> Unit
) {
    CardBox(modifier = modifier.clickable { onClick() }) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Box {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                if (badge != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.error,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Text(badge, color = MaterialTheme.colorScheme.onError, fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(label, fontSize = 13.sp)
        }
    }
}

/** 数字统计卡（与首页共用风格） */
@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    CardBox(modifier = modifier) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 字节数格式化为可读字符串 */
private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024L -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}
