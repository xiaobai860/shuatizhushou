package com.example.shuati.ui.pages

import androidx.compose.foundation.clickable
import com.example.shuati.ui.theme.semantic
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.shuati.ui.repository
import com.example.shuati.model.Question
import com.example.shuati.model.QuestionBank
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.example.shuati.ui.components.ScreenTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DedupScreen(navController: NavController, initialBankId: String? = null) {
    val repo = repository()
    val banks = remember { repo.getBanks() }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 查重模式：单题库 vs 跨题库（从题库列表直达时默认选中该题库）
    var dedupMode by remember { mutableStateOf("single") }
    // 相似度阈值 (20-95%，存为 Int)
    var threshold by remember { mutableStateOf(repo.getDedupThreshold().toInt()) }
    // 当前选中的题库（单题库模式下）
    var selectedBankId by remember { mutableStateOf(initialBankId) }
    // 批量选择
    var selectedGroupIdx by remember { mutableStateOf<Int?>(null) }
    var selectedQIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    var isLoading by remember { mutableStateOf(false) }
    var duplicateGroups by remember { mutableStateOf<List<List<Pair<String, Question>>>>(emptyList()) }
    var statsText by remember { mutableStateOf("") }
    var showAllGroups by remember { mutableStateOf(false) }

    val displayedGroups = if (showAllGroups) duplicateGroups else duplicateGroups.take(20)

    fun runDedup() {
        isLoading = true
        scope.launch {
            val results = when (dedupMode) {
                "cross" -> {
                    // 跨题库查重
                    val groups = repo.findDuplicateGroupsAcrossBanks(banks, threshold / 100.0)
                    groups.map { groupIds ->
                        // groupIds: List<Pair<bankId, questionId>>
                        groupIds.mapNotNull { (bid, qid) ->
                            banks.find { b -> b.id == bid }?.questions?.firstOrNull { q -> q.id == qid }
                                ?.let { bid to it }
                        }
                    }.filter { it.size > 1 }
                }
                else -> {
                    // 单题库查重
                    val bankId = selectedBankId ?: return@launch
                    val bank = banks.find { b -> b.id == bankId }
                    if (bank != null) {
                        val groups = repo.findDuplicateGroups(bank, threshold.toDouble() / 100.0)
                        groups.map { groupIds ->
                            groupIds.mapNotNull { qid ->
                                bank.questions.firstOrNull { it.id == qid }?.let { qid to it }
                            }.filter { it.second != null }.map { it.first to it.second!! }
                        }.filter { it.size > 1 }
                    } else emptyList()
                }
            }
            duplicateGroups = results
            statsText = "发现 ${results.size} 组重复题目（阈值 ${threshold}%）"
            isLoading = false
        }
    }

    Scaffold(

        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ScreenTopBar("查重检测", onBack = { navController.popBackStack() }, subtitle = "仅在同题型之间比对")
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 操作区域
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 模式切换
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = dedupMode == "single",
                            onClick = { dedupMode = "single" },
                            label = { Text("单题库", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Filled.Book, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        )
                        FilterChip(
                            selected = dedupMode == "cross",
                            onClick = { dedupMode = "cross" },
                            label = { Text("跨题库", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Filled.AllInclusive, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        )
                    }

                    // 单题库模式：选择题库
                    if (dedupMode == "single" && banks.size > 1) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("选择题库：", fontSize = 13.sp)
                            banks.forEach { bank ->
                                FilterChip(
                                    selected = selectedBankId == bank.id,
                                    onClick = { selectedBankId = bank.id },
                                    label = { Text(bank.name.take(8), fontSize = 11.sp) },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Book, contentDescription = null, modifier = Modifier.size(12.dp))
                                    }
                                )
                            }
                        }
                    }

                    // 相似度阈值滑块
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("相似度阈值：$threshold%", fontSize = 13.sp)
                        Slider(
                            value = threshold.toFloat(),
                            onValueChange = { threshold = it.toInt() },
                            valueRange = 20f..95f,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // 操作按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { runDedup() },
                            modifier = Modifier.weight(1f),
                            enabled = !isLoading && (dedupMode == "cross" || selectedBankId != null)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                            } else {
                                Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("开始查重")
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val totalDeleted = mutableListOf<String>()
                                    duplicateGroups.forEach { group ->
                                        if (group.size > 1) {
                                            // 删除非参考题
                                            val idsToDelete = group.drop(1).map { it.second.id }.toSet()
                                            val bankId = group[0].first
                                            repo.deleteQuestions(bankId, idsToDelete)
                                            totalDeleted.addAll(idsToDelete)
                                        }
                                    }
                                    // 同时清除相关的错题记录和收藏
                                    totalDeleted.forEach { qid ->
                                        repo.removeWrongRecordByQuestionId(qid)
                                        repo.removeFavoriteByQuestionId(qid)
                                    }
                                    snackbarHostState.showSnackbar("已清除 ${totalDeleted.size} 条重复题目及关联记录")
                                    duplicateGroups = emptyList()
                                    statsText = ""
                                }
                            },
                            enabled = duplicateGroups.isNotEmpty()
                        ) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("批量清除")
                        }
                    }

                    if (statsText.isNotEmpty()) {
                        Text(
                            text = statsText,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 查重结果
            if (duplicateGroups.isEmpty() && !isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无重复数据或尚未查重", textAlign = TextAlign.Center)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(displayedGroups, key = { it.first().second.id }) { group ->
                        DuplicateGroupCard(
                            group = group,
                            banks = banks,
                            repo = repo,
                            scope = scope,
                            snackbarHostState = snackbarHostState,
                            isSelected = selectedGroupIdx != null,
                            onToggleSelect = {
                                selectedGroupIdx = if (selectedGroupIdx == displayedGroups.indexOf(group)) null else displayedGroups.indexOf(group)
                            }
                        )
                    }

                    if (!showAllGroups && duplicateGroups.size > 20) {
                        item {
                            Button(
                                onClick = { showAllGroups = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("显示全部 ${duplicateGroups.size} 组重复题目")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DuplicateGroupCard(
    group: List<Pair<String, Question>>,
    banks: List<QuestionBank>,
    repo: AppRepository,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    isSelected: Boolean,
    onToggleSelect: () -> Unit
) {
    val firstBankId = group[0].first
    val firstBankName = banks.find { b -> b.id == firstBankId }?.name ?: firstBankId

    // 检查题型是否混合
    val questionTypes = group.map { (_, q) -> q.type.name }.distinct()
    val isMixedType = questionTypes.size > 1

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleSelect() },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 标题行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 选择指示器
                    if (isSelected) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                    Column {
                        Text(
                            text = "重复组 #$firstBankName",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        if (isMixedType) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.semantic.warning, modifier = Modifier.size(12.dp))
                                Text(
                                    "题型混合（${questionTypes.joinToString(" / ") { getTypeLabel(it) }}）",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.semantic.warning
                                )
                            }
                        }
                    }
                }

                // 相似度徽章
                if (group.size > 1) {
                    val avgSim = group.drop(1).map { (_, q) -> calculateSimilarity(group[0].second, q) }.average()
                    val simColor = when {
                        avgSim >= 0.95 -> MaterialTheme.semantic.error
                        avgSim >= 0.85 -> MaterialTheme.semantic.warning
                        else -> MaterialTheme.colorScheme.primary
                    }
                    Badge(
                        containerColor = simColor.copy(alpha = 0.15f),
                        contentColor = simColor
                    ) {
                        Text("${(avgSim * 100).toInt()}%", fontSize = 11.sp)
                    }
                }
            }

            // 题目列表
            group.forEachIndexed { index, (bankId, question) ->
                val bankName = banks.find { b -> b.id == bankId }?.name ?: bankId
                val isReference = index == 0

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 参考题标签 / 序号
                    if (isReference) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.semantic.infoContainer
                        ) {
                            Text(
                                "参考题",
                                fontSize = 10.sp,
                                color = MaterialTheme.semantic.info,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else {
                        Text(
                            text = "${index + 1}.",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.width(20.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }

                    // 题目内容
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = question.question,
                            fontSize = 14.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = bankName,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 删除按钮（非参考题可删除）
                    if (!isReference) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    repo.deleteQuestions(bankId, setOf(question.id))
                                    repo.removeWrongRecordByQuestionId(question.id)
                                    repo.removeFavoriteByQuestionId(question.id)
                                    snackbarHostState.showSnackbar("已删除")
                                }
                            }
                        ) {
                            Icon(
                                Icons.Filled.DeleteOutline,
                                contentDescription = "删除",
                                tint = MaterialTheme.semantic.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

fun getTypeLabel(typeName: String): String = when (typeName) {
    "single" -> "单选"
    "multiple" -> "多选"
    "judge" -> "判断"
    "fill" -> "填空"
    "essay" -> "简答"
    else -> typeName
}

fun calculateSimilarity(q1: Question, q2: Question): Double {
    val text1 = q1.question.trim()
    val text2 = q2.question.trim()
    if (text1 == text2) return 1.0
    if (text1.isEmpty() || text2.isEmpty()) return 0.0

    val bigrams1 = text1.toList().windowed(2).toSet()
    val bigrams2 = text2.toList().windowed(2).toSet()
    val intersection = bigrams1.intersect(bigrams2).size
    val union = bigrams1.union(bigrams2).size
    return if (union == 0) 0.0 else intersection * 2.0 / union
}
