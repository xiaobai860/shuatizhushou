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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.shuati.model.QuestionBank
import com.example.shuati.ui.*
import com.example.shuati.ui.components.CardBox
import com.example.shuati.ui.components.EmptyState
import com.example.shuati.ui.components.typeLabel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    val repo = repository()
    var banks by remember { mutableStateOf(emptyList<QuestionBank>()) }
    var totalQuestions by remember { mutableStateOf(0) }
    var wrongCount by remember { mutableStateOf(0) }
    var favCount by remember { mutableStateOf(0) }
    var planCount by remember { mutableStateOf(0) }
    var todayStreak by remember { mutableStateOf(0) }
    var storageUsed by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        val bs = repo.getBanks()
        banks = bs
        totalQuestions = bs.sumOf { it.questions.size }
        wrongCount = repo.getWrongRecords().size
        favCount = repo.getFavoriteRecords().size
        planCount = repo.getStudyPlans().count { !it.archived }
        todayStreak = repo.getTodayStreak()
        storageUsed = repo.storageUsedBytes()
    }

    Scaffold(
        topBar = { ScreenTopBarHome() }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // 统计概览
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("题库", banks.size.toString(), Modifier.weight(1f))
                    StatCard("题目", totalQuestions.toString(), Modifier.weight(1f))
                    StatCard("错题", wrongCount.toString(), Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("今日连续", todayStreak.toString() + "天", Modifier.weight(1f))
                    StatCard("进行中计划", planCount.toString(), Modifier.weight(1f))
                    StatCard("收藏", favCount.toString(), Modifier.weight(1f))
                }
            }

            // 存储空间
            item {
                CardBox {
                    val usedKb = storageUsed / 1024.0
                    val limitKb = 10240.0  // 展示上限 10MB
                    val ratio = (usedKb / limitKb).toFloat().coerceIn(0f, 1f)
                    val warning = usedKb / limitKb >= 0.7
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Storage, contentDescription = null,
                            tint = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("存储空间", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatBytes(storageUsed), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        if (warning) {
                            Text("占用较多", fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error,
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

            // 快捷入口
            item {
                Text("快捷入口", fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                    modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickEntry(Icons.Filled.Book, "题库管理", Modifier.weight(1f)) { navController.navigate(Routes.LIBRARY) }
                    QuickEntry(Icons.Filled.Upload, "导入题库", Modifier.weight(1f)) { navController.navigate(Routes.IMPORT) }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickEntry(Icons.Filled.Star, "智能推荐", Modifier.weight(1f)) { navController.navigate(Routes.RECOMMEND) }
                    QuickEntry(Icons.Filled.CalendarMonth, "学习计划", Modifier.weight(1f)) { navController.navigate(Routes.STUDY_PLAN) }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickEntry(Icons.Filled.BugReport, "查重检测", Modifier.weight(1f)) { navController.navigate(Routes.DEDUP) }
                    QuickEntry(Icons.Filled.Backup, "备份还原", Modifier.weight(1f)) { navController.navigate(Routes.BACKUP) }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickEntry(Icons.Filled.AutoStories, "全部题库", Modifier.weight(1f)) {
                        navController.navigate("${Routes.QUIZ}?bankId=__all__&mode=sequence&source=all&showModeSelect=true")
                    }
                    QuickEntry(Icons.Filled.Visibility, "浏览模式", Modifier.weight(1f)) {
                        navController.navigate("${Routes.QUIZ}?bankId=__all__&mode=browse&source=browse")
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickEntry(Icons.Filled.ErrorOutline, "错题回顾", Modifier.weight(1f)) { navController.navigate(Routes.WRONGS) }
                    QuickEntry(Icons.Filled.Star, "收藏题目", Modifier.weight(1f)) { navController.navigate(Routes.FAVORITES) }
                    QuickEntry(Icons.Filled.History, "刷题历史", Modifier.weight(1f)) { navController.navigate(Routes.HISTORY) }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickEntry(Icons.Filled.Search, "题目搜索", Modifier.weight(1f)) { navController.navigate(Routes.SEARCH) }
                    QuickEntry(Icons.Filled.BarChart, "统计分析", Modifier.weight(1f)) { navController.navigate(Routes.STATS) }
                }
            }

            // 最近题库
            item {
                Text("最近题库", fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
            }
            if (banks.isEmpty()) {
                item { EmptyState("还没有题库，去「导入题库」添加吧") }
            } else {
                items(banks.sortedByDescending { it.updatedAt }) { bank ->
                    val progress = repo.getProgress(bank.id)
                    val answered = progress?.answeredIds?.size ?: 0
                    val total = bank.questions.size
                    val pct = if (total > 0) (answered.toFloat() / total).coerceIn(0f, 1f) else 0f
                    val completed = progress?.everCompleted ?: false
                    CardBox(onClick = { navController.navigate("${Routes.EDIT_BANK}?bankId=${bank.id}") }) {
                        Column(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(bank.name, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                                    Text(
                                        "$total 题" + if (answered > 0) " · 已答 $answered" else "",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = {
                                    navController.navigate("${Routes.QUIZ}?bankId=${bank.id}&mode=sequence&source=bank&showModeSelect=true")
                                }) {
                                    Text("开始刷题")
                                }
                            }
                            if (total > 0) {
                                Spacer(Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    LinearProgressIndicator(
                                        progress = { pct },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(4.dp)
                                            .clip(MaterialTheme.shapes.small),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if (completed) "已刷完" else "${(pct * 100).toInt()}%",
                                        fontSize = 11.sp,
                                        color = if (completed) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun ScreenTopBarHome() {
    val ctx = LocalContext.current
    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }
            .getOrNull()
    }
    TopAppBar(title = {
        Text("刷题" + if (version != null) " v$version" else "", fontWeight = FontWeight.SemiBold)
    })
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    CardBox(modifier = modifier) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 22.sp,
            color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun QuickEntry(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    CardBox(modifier = modifier.clickable { onClick() }) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(label, fontSize = 13.sp)
        }
    }
}

/** 字节数格式化为可读字符串 */
private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024L -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}
