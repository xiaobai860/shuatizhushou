package com.example.shuati.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.shuati.data.AppRepository
import com.example.shuati.model.QuestionBank
import com.example.shuati.model.QuizHistory
import com.example.shuati.ui.Routes
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(navController: NavController) {
    val repo = remember { AppRepository(navController.context) }
    var history by remember { mutableStateOf(repo.getHistoryRecords()) }
    val banks = remember { repo.getBanks() }
    var showClearDialog by remember { mutableStateOf(false) }

    // 按题库筛选
    val allBankIds = history.map { it.bankId }.distinct()
    var selectedBankId by remember { mutableStateOf<String?>(null) }

    val filteredHistory = if (selectedBankId != null) {
        history.filter { it.bankId == selectedBankId }
    } else {
        history
    }

    // 按时间排序
    val sortedHistory = filteredHistory.sortedByDescending { it.finishedAt }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("答题历史") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (history.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "清空历史")
                        }
                    }
                    IconButton(onClick = {
                        navController.navigate(Routes.STATS)
                    }) {
                        Icon(Icons.Filled.BarChart, contentDescription = "统计")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // 题库筛选栏
            if (allBankIds.size > 1) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilterChip(
                            selected = selectedBankId == null,
                            onClick = { selectedBankId = null },
                            label = { Text("全部", fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(Icons.Filled.AllInclusive, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        )
                        allBankIds.forEach { bankId ->
                            val bankName = banks.find { b -> b.id == bankId }?.name ?: bankId
                            FilterChip(
                                selected = selectedBankId == bankId,
                                onClick = { selectedBankId = if (selectedBankId == bankId) null else bankId },
                                label = { Text(bankName.take(8), fontSize = 12.sp) },
                                leadingIcon = {
                                    Icon(Icons.Filled.Book, contentDescription = null, modifier = Modifier.size(14.dp))
                                }
                            )
                        }
                    }
                }
            }

            // 统计概览
            val totalSessions = sortedHistory.size
            val totalQuestions = sortedHistory.sumOf { it.total }
            val totalCorrect = sortedHistory.sumOf { it.correct }
            val avgAccuracy = if (totalQuestions > 0) String.format("%.1f%%", totalCorrect * 100.0 / totalQuestions) else "0%"
            val totalTime = sortedHistory.sumOf { it.durationSec ?: 0 }
            val avgTime = if (totalSessions > 0) String.format("%.1fs", totalTime.toFloat() / totalSessions) else "0s"

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        InfoItem("刷题次数", "$totalSessions")
                        InfoItem("答题总量", "$totalQuestions")
                        InfoItem("平均正确率", avgAccuracy)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        InfoItem("总用时", formatDuration(totalTime))
                        InfoItem("平均用时", avgTime)
                    }
                }
            }

            // 历史记录列表
            if (sortedHistory.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.History,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "暂无答题记录",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "开始答题后，历史记录将显示在这里",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sortedHistory, key = { it.id }) { record ->
                        HistoryItem(record, banks, repo, navController)
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空历史") },
            text = { Text("确认清空全部刷题历史？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    repo.clearHistoryRecords()
                    history = emptyList()
                    showClearDialog = false
                }) { Text("清空") }
            },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("取消") } }
        )
    }
}

@Composable
fun HistoryItem(record: QuizHistory, banks: List<QuestionBank>, repo: AppRepository, navController: NavController) {
    val bankName = banks.find { it.id == record.bankId }?.name ?: record.bankName
    val relativeDate = formatRelativeDate(record.finishedAt)

    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val acc = if (record.total > 0) record.correct * 100 / record.total else 0
            // 标题行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = bankName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = relativeDate,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        AssistChip(
                            onClick = {},
                            label = { Text(getModeLabel(record.mode ?: "sequential"), fontSize = 10.sp) }
                        )
                    }
                }

                // 正确率
                Text(
                    text = "$acc%",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (acc >= 60) Color(0xFF4CAF50) else Color(0xFFFF5252)
                )
            }

            // 详情行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DetailItem(
                        icon = Icons.Filled.CheckCircle,
                        text = "答对 ${record.correct}",
                        color = Color(0xFF4CAF50)
                    )
                    DetailItem(
                        icon = Icons.Filled.Cancel,
                        text = "答错 ${record.wrong}",
                        color = Color(0xFFFF5252)
                    )
                    DetailItem(
                        icon = Icons.Filled.Timer,
                        text = "用时 ${record.durationSec ?: 0}s",
                        color = Color(0xFF2196F3)
                    )
                }

                // 再次练习按钮
                TextButton(
                    onClick = {
                        val mode = record.mode.takeIf { it in listOf("sequential", "random") } ?: "sequential"
                        navController.navigate("${Routes.QUIZ}?bankId=${record.bankId}&mode=$mode&source=history&planId=null")
                    }
                ) {
                    Icon(Icons.Filled.Replay, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("再次练习", fontSize = 12.sp)
                }
            }

            // 正确率进度条
            LinearProgressIndicator(
                progress = { acc / 100f },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = if (acc >= 60) Color(0xFF4CAF50) else Color(0xFFFF5252),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
fun DetailItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = color)
        Text(text, fontSize = 12.sp, color = color)
    }
}

@Composable
fun InfoItem(label: String, value: String) {
    Column {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
    }
}

fun getModeLabel(mode: String): String = when (mode) {
    "sequential" -> "顺序"
    "random" -> "随机"
    "browse" -> "浏览"
    else -> mode
}

fun formatRelativeDate(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        days >= 1 -> "${days} 天前"
        hours >= 1 -> "${hours} 小时前"
        minutes >= 1 -> "${minutes} 分钟前"
        else -> "刚刚"
    }
}

fun formatDuration(seconds: Int): String {
    val hours = seconds / 3600
    val mins = (seconds % 3600) / 60
    return when {
        hours > 0 -> "$hours 小时 $mins 分"
        mins > 0 -> "$mins 分钟"
        else -> "$seconds 秒"
    }
}
