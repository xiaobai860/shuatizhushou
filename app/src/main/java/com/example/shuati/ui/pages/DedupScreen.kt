package com.example.shuati.ui.pages

import androidx.compose.foundation.clickable
import com.example.shuati.ui.theme.semantic
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
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
import com.example.shuati.logic.Scoring
import com.example.shuati.ui.repository
import com.example.shuati.model.Question
import com.example.shuati.model.QuestionBank
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.shuati.ui.components.ScreenTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DedupScreen(navController: NavController, initialBankId: String? = null) {
    val repo = repository()
    val banks = remember { repo.getBanks() }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 查重范围：选中的题库 ID 集合（默认全部题库；从某题库直达时默认只选该题库）
    var selectedBankIds by remember {
        mutableStateOf<Set<String>>(
            initialBankId?.let { setOf(it) } ?: banks.map { it.id }.toSet()
        )
    }
    /** 是否处于「全部题库」模式（该状态下忽略 selectedBankIds 的具体勾选，始终覆盖所有题库） */
    var selectAllBanks by remember { mutableStateOf(initialBankId == null) }
    // 相似度阈值 (20-95%，存为 Int)。
    // ⚠️ getDedupThreshold() 返回 0..1 的 Double，必须 ×100 再取整，
    // 否则 0.85.toInt() = 0，滑块会错误地显示为「0%」（修复前就是这个 bug）。
    var threshold by remember {
        mutableStateOf((repo.getDedupThreshold() * 100).toInt().coerceIn(20, 95))
    }
    // 批量选择
    var selectedGroupIdx by remember { mutableStateOf<Int?>(null) }
    var selectedQIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    var isLoading by remember { mutableStateOf(false) }
    var duplicateGroups by remember { mutableStateOf<List<List<Pair<String, Question>>>>(emptyList()) }
    var statsText by remember { mutableStateOf("") }
    var showAllGroups by remember { mutableStateOf(false) }

    val displayedGroups = if (showAllGroups) duplicateGroups else duplicateGroups.take(20)

    fun runDedup() {
        val targetBanks = banks.filter { it.id in selectedBankIds }
        if (targetBanks.isEmpty()) return
        val th = threshold / 100.0
        isLoading = true
        duplicateGroups = emptyList()
        statsText = ""
        scope.launch {
            // 先让进度条渲染出来：查重是 O(n²) 的 CPU 密集计算，若直接在主线程算，
            // isLoading=true 的重组会被阻塞，表现为「点击后卡顿数秒才出现进度条」。
            // 让出一帧后再切到 Default 线程执行，进度条即可立即显示。
            kotlinx.coroutines.delay(16)
            val results = withContext(Dispatchers.Default) {
                if (targetBanks.size == 1) {
                    // 单题库查重
                    val bank = targetBanks[0]
                    repo.findDuplicateGroups(bank, th).map { groupIds ->
                        groupIds.mapNotNull { qid ->
                            bank.questions.firstOrNull { it.id == qid }?.let { bank.id to it }
                        }
                    }.filter { it.size > 1 }
                } else {
                    // 多题库查重（跨题库比对）
                    repo.findDuplicateGroupsAcrossBanks(targetBanks, th).map { groupIds ->
                        // groupIds: List<Pair<bankId, questionId>>
                        groupIds.mapNotNull { (bid, qid) ->
                            targetBanks.find { b -> b.id == bid }?.questions
                                ?.firstOrNull { q -> q.id == qid }
                                ?.let { bid to it }
                        }
                    }.filter { it.size > 1 }
                }
            }
            duplicateGroups = results
            statsText = "发现 ${results.size} 组重复题目（阈值 $threshold%，范围：${targetBanks.size} 个题库）"
            isLoading = false
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // ⚠️ 不要只设 navigationBars：那会覆盖 Scaffold 默认的 insets 处理，
        // 使顶部状态栏 inset 被重复/错误地留白，表现为「头部上下出现空白区域」。
        // 与 AppNavHost 保持一致——顶部栏自行 statusBarsPadding，此处统一用 0。
        contentWindowInsets = WindowInsets(0),
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
                    // 查重范围：题库多选（含「全部题库」），自动换行展示全部题库
                    Text("查重范围（可多选）", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selectAllBanks,
                            onClick = {
                                selectAllBanks = true
                                selectedBankIds = banks.map { it.id }.toSet()
                            },
                            label = { Text("全部题库", fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(Icons.Filled.AllInclusive, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        )
                        banks.forEach { bank ->
                            val checked = selectAllBanks || bank.id in selectedBankIds
                            FilterChip(
                                selected = checked && !selectAllBanks,
                                onClick = {
                                    if (selectAllBanks) {
                                        // 由「全部题库」切换为单选该题库
                                        selectAllBanks = false
                                        selectedBankIds = setOf(bank.id)
                                    } else {
                                        val next = if (bank.id in selectedBankIds) selectedBankIds - bank.id
                                                   else selectedBankIds + bank.id
                                        selectedBankIds = next
                                        // 全部取消勾选时回退到「全部题库」
                                        if (next.isEmpty()) {
                                            selectAllBanks = true
                                            selectedBankIds = banks.map { it.id }.toSet()
                                        }
                                    }
                                },
                                label = { Text(bank.name, fontSize = 12.sp) },
                                leadingIcon = {
                                    Icon(Icons.Filled.Book, contentDescription = null, modifier = Modifier.size(14.dp))
                                }
                            )
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

                    // 查重进行中：显式进度条（计算已切到后台线程，点击后即可立即看到）
                    if (isLoading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("正在比对题目相似度，题库较大时请稍候…", fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    // 操作按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { runDedup() },
                            modifier = Modifier.weight(1f),
                            enabled = !isLoading && (selectAllBanks || selectedBankIds.isNotEmpty())
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                                Spacer(Modifier.width(8.dp))
                                Text("查重中…")
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
                    // 算法说明
                    Text(
                        "算法说明：仅在同题型（单选/多选/判断/填空）之间比对，按题干关键词相似度评分，超过阈值的题目归为一组；批量清除会保留每组第 1 题，并同步清理关联的错题与收藏。",
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                    )
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

                // 相似度徽章（v3：题干 + 选项/答案校验的综合分，与分组判定一致）
                if (group.size > 1) {
                    val avgSim = group.drop(1).map { (_, q) -> Scoring.compareForDedup(group[0].second, q, repo.getDedupThreshold()).score }.average()
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
