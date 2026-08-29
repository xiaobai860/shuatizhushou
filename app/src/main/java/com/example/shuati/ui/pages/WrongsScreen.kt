package com.example.shuati.ui.pages


import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.shuati.model.WrongRecord
import com.example.shuati.ui.*
import com.example.shuati.ui.components.CardBox
import com.example.shuati.ui.components.EmptyState
import com.example.shuati.ui.components.SectionTitle
import com.example.shuati.ui.components.SoftButton
import com.example.shuati.ui.theme.semantic
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Refresh
import com.example.shuati.ui.components.ScreenTopBar
import com.example.shuati.ui.components.TagChip
import com.example.shuati.ui.components.formatAnswerWithLabel
import com.example.shuati.ui.components.typeLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WrongsScreen(navController: NavController, bankId: String? = null) {
    val repo = repository()
    var records by remember { mutableStateOf(emptyList<WrongRecord>()) }
    var masteryTarget by remember { mutableStateOf(repo.getMasteryTarget()) }
    var showTarget by remember { mutableStateOf(false) }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }
    val allTags = remember { repo.getAllTags() }
    val banks = remember { repo.getBanks() }
    // 有效题目 key：题库存在 且 题库中包含该题
    val validKeys = remember(banks) {
        banks.flatMap { b -> b.questions.map { q -> "${b.id}::${q.id}" } }.toSet()
    }

    fun reload() {
        records = repo.getWrongRecords().let { list ->
            if (bankId != null) list.filter { it.bankId == bankId } else list
        }
    }
    LaunchedEffect(Unit) { reload() }

    fun getFilteredRecords(): List<WrongRecord> = if (selectedTag == null) records
    else records.filter { rec -> (rec.question?.tags ?: emptyList()).contains(selectedTag) }

    // 排序：答错次数降序，次数相同按记录时间升序（最早记录在前）
    val displayRecords = getFilteredRecords().sortedWith(
        compareByDescending<WrongRecord> { it.wrongCount }.thenBy { it.recordedAt }
    )
    // 失效记录：题库已删除或题库中已不含该题
    val invalidRecords = records.filter { "${it.bankId}::${it.questionId}" !in validKeys }

    Scaffold(

        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ScreenTopBar(
                "错题本",
                onBack = { navController.popBackStack() },
                subtitle = "共 ${records.size} 题 · 按答错次数排序",
                actions = {
                    TextButton(onClick = { showTarget = true }) { Text("连对目标 $masteryTarget") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (allTags.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
                ) {
                    FilterChip(
                        selected = selectedTag == null,
                        onClick = { selectedTag = null },
                        label = { Text("全部") }
                    )
                    allTags.forEach { tag ->
                        FilterChip(
                            selected = selectedTag == tag,
                            onClick = { selectedTag = if (selectedTag == tag) null else tag },
                            label = { Text(tag) }
                        )
                    }
                }
            }
            if (records.isEmpty()) {
                EmptyState("太棒了，暂时没有错题")
            } else {
                // 全部重做强调卡
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.semantic.errorContainer)
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Refresh, null, tint = MaterialTheme.semantic.error, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("全部重做", fontSize = 14.5.sp, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.semantic.onErrorContainer)
                            Text("连对 $masteryTarget 次自动移出错题本", fontSize = 11.sp,
                                color = MaterialTheme.semantic.onErrorContainer.copy(alpha = 0.8f))
                        }
                        SoftButton(
                            text = "开始",
                            compact = true,
                            modifier = Modifier.width(88.dp),
                            onClick = {
                                navController.navigate("${Routes.QUIZ}?mode=sequence&source=wrong&showModeSelect=true")
                            }
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)) {
                    SectionTitle("待巩固", count = "共 ${displayRecords.size} 道")
                }
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(displayRecords) { rec ->
                        val streak = repo.getStreak(rec.bankId, rec.questionId)
                        CardBox {
                            Column(Modifier.fillMaxWidth()) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                    // 题型标签 + 答错次数
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Surface(
                                            shape = MaterialTheme.shapes.small,
                                            color = MaterialTheme.colorScheme.secondaryContainer
                                        ) {
                                            Text(
                                                typeLabel(rec.question?.type ?: com.example.shuati.model.QuestionType.single),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                        Spacer(Modifier.height(2.dp))
                                        Text("错${rec.wrongCount}次", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error)
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(rec.question?.question ?: "(题目缺失)", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                                        Spacer(Modifier.height(4.dp))
                                        Text("连对 $streak/$masteryTarget", fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if ((rec.question?.tags ?: emptyList()).isNotEmpty()) {
                                            Spacer(Modifier.height(4.dp))
                                            Row { (rec.question?.tags ?: emptyList()).forEach { TagChip(it) } }
                                        }
                                        // 答案预览
                                        if (!rec.question?.answer.isNullOrBlank()) {
                                            Spacer(Modifier.height(4.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                                shape = MaterialTheme.shapes.small
                                            ) {
                                                Text(
                                                    "答案：${rec.question?.let { formatAnswerWithLabel(it) } ?: ""}",
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                    IconButton(onClick = {
                                        repo.removeWrongRecord(rec.bankId, rec.questionId)
                                        reload()
                                    }) { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = {
                                        navController.navigate("${Routes.QUIZ}?bankId=${rec.bankId}&mode=sequence&source=bank&questionIds=${rec.bankId}::${rec.questionId}")
                                    }) { Text("重做此题") }
                                }
                            }
                        }
                    }

                    // 失效记录区：题库已删除或题库中已不含该题
                    if (invalidRecords.isNotEmpty()) {
                        item {
                            Column {
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.ErrorOutline, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("已失效（${invalidRecords.size}道，题库已删除）", fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(Modifier.height(8.dp))
                                invalidRecords.forEach { rec ->
                                    CardBox {
                                        Text(rec.bankName + "（已删除/不含此题）", fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.height(4.dp))
                                        Text(rec.question?.question ?: "(题目缺失)", fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                        if (!rec.question?.answer.isNullOrBlank()) {
                                            Spacer(Modifier.height(4.dp))
                                            Text("答案：${rec.question?.answer}", fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        invalidRecords.forEach { repo.removeWrongRecord(it.bankId, it.questionId) }
                                        reload()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                                ) {
                                    Text("一键清除失效记录", color = MaterialTheme.colorScheme.onErrorContainer)
                                }
                            }
                        }
                    }
                }
                    // 底部操作
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showClearDialog = true }) {
                            Text("清空错题", color = MaterialTheme.semantic.error)
                        }
                        Spacer(Modifier.weight(1f))
                        SoftButton(
                            text = "开始重做 ${displayRecords.size} 题",
                            compact = true,
                            modifier = Modifier.width(190.dp),
                            onClick = {
                                navController.navigate("${Routes.QUIZ}?mode=sequence&source=wrong&showModeSelect=true")
                            }
                        )
                    }
            }
        }
    }

    if (showTarget) {
        var v by remember { mutableStateOf(masteryTarget.toString()) }
        AlertDialog(
            onDismissRequest = { showTarget = false },
            title = { Text("设置掌握目标") },
            text = {
                OutlinedTextField(
                    value = v, onValueChange = { v = it.filter { c -> c.isDigit() } },
                    label = { Text("连续答对 N 次移出错题") }, singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    v.toIntOrNull()?.takeIf { it > 0 }?.let {
                        repo.saveMasteryTarget(it); masteryTarget = it
                    }
                    showTarget = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showTarget = false }) { Text("取消") } }
        )
    }

    if (showClearDialog) {
        val isAll = selectedTag == null
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空错题") },
            text = { Text(if (isAll) "确认清空全部错题记录？此操作不可撤销。" else "确认清空当前筛选的 ${displayRecords.size} 条错题记录？") },
            confirmButton = {
                TextButton(onClick = {
                    if (isAll) {
                        repo.clearWrongRecords()
                    } else {
                        displayRecords.forEach { repo.removeWrongRecord(it.bankId, it.questionId) }
                    }
                    reload()
                    showClearDialog = false
                }) { Text("清空") }
            },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("取消") } }
        )
    }
}
