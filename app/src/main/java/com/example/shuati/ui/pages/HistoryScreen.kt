package com.example.shuati.ui.pages

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.shuati.model.QuizHistory
import com.example.shuati.ui.*
import com.example.shuati.ui.components.CardBox
import com.example.shuati.ui.components.EmptyState
import com.example.shuati.ui.components.ScreenTopBar
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(navController: NavController) {
    val repo = repository()
    var records by remember { mutableStateOf(emptyList<QuizHistory>()) }
    var showClear by remember { mutableStateOf(false) }

    fun reload() { records = repo.getHistoryRecords().sortedByDescending { it.createdAt } }
    LaunchedEffect(Unit) { reload() }

    val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

    Scaffold(
        topBar = {
            ScreenTopBar("刷题历史", onBack = { navController.popBackStack() },
                actions = {
                    if (records.isNotEmpty())
                        IconButton(onClick = { showClear = true }) { Icon(Icons.Filled.Delete, null) }
                })
        }
    ) { padding ->
        if (records.isEmpty()) {
            EmptyState("还没有刷题记录")
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(records) { h ->
                    CardBox {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(h.bankName, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                                    if (!h.mode.isNullOrBlank()) {
                                        val label = when (h.mode) {
                                            "sequential" -> "顺序"
                                            "random" -> "随机"
                                            "browse" -> "浏览"
                                            else -> h.mode
                                        }
                                        Spacer(Modifier.width(6.dp))
                                        AssistChip(
                                            onClick = {},
                                            label = { Text(label, fontSize = 10.sp) },
                                            colors = AssistChipDefaults.assistChipColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                                            ),
                                            modifier = Modifier.height(20.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text("${fmt.format(Date(h.createdAt))}　共 ${h.total} 题　正确 ${h.correct}　错误 ${h.wrong}" +
                                        (if (h.durationSec != null) "　用时 ${h.durationSec}s" else ""),
                                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            val acc = if (h.total > 0) (h.correct * 100 / h.total) else 0
                            AssistChip(
                                onClick = {},
                                label = { Text("$acc%") },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if (acc >= 80) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    if (showClear) {
        AlertDialog(
            onDismissRequest = { showClear = false },
            title = { Text("清空历史") },
            text = { Text("确定清空全部刷题历史？") },
            confirmButton = { TextButton(onClick = { repo.clearHistoryRecords(); showClear = false; reload() }) { Text("清空", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showClear = false }) { Text("取消") } }
        )
    }
}
