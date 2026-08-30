package com.example.shuati.ui.pages

import androidx.compose.foundation.layout.*
import com.example.shuati.ui.theme.semantic
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.shuati.data.AppRepository
import com.example.shuati.ui.repository
import com.example.shuati.logic.Scoring
import com.example.shuati.model.Question
import com.example.shuati.model.QuestionBank
import com.example.shuati.model.QuestionType
import com.example.shuati.ui.components.TagChip
import kotlinx.coroutines.launch
import java.util.UUID
import com.example.shuati.ui.components.ScreenTopBar
import com.example.shuati.ui.components.ShuatiSwitch

const val ITEMS_PER_PAGE = 50

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditBankScreen(navController: NavController, bankId: String?, editQuestionId: String = "") {
    val repo = repository()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 初始化题库
    if (bankId != null && !repo.getBanks().any { it.id == bankId }) {
        LaunchedEffect(bankId) {
            repo.addBank(QuestionBank(id = bankId, name = bankId, questions = emptyList()))
        }
    }
    val banks = repo.getBanks()
    val currentBank = banks.find { it.id == bankId } ?: run {
        navController.popBackStack()
        return
    }

    // 分页状态
    var displayCount by remember { mutableStateOf(ITEMS_PER_PAGE) }
    val allQuestions = currentBank.questions

    // Tab：0=全部题目 1=疑似错题
    var tab by remember { mutableStateOf(0) }
    var keyword by remember { mutableStateOf("") }
    val filteredAll = remember(allQuestions, keyword) {
        if (keyword.isBlank()) allQuestions
        else allQuestions.filter { it.question.contains(keyword, ignoreCase = true) }
    }
    val displayedQuestions = filteredAll.take(displayCount)
    val hasMore = displayCount < filteredAll.size

    // 疑似错题（含数据质量问题）
    val suspectQuestions = remember(allQuestions) {
        allQuestions.filter { Scoring.checkIsSuspect(it) }
    }

    // 找到 editQuestionId 对应的索引
    val editTargetIndex = remember(editQuestionId) {
        allQuestions.indexOfFirst { it.id == editQuestionId }.takeIf { it >= 0 }
    }

    // 编辑题目浮层
    var editingQuestion by remember { mutableStateOf<Question?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    // 题库设置（名称 + AI 默认提示语）
    var showBankSettings by remember { mutableStateOf(false) }
    var bankNameDraft by remember { mutableStateOf("") }
    var bankHintDraft by remember { mutableStateOf("") }

    // 删除确认
    var showDeleteConfirm by remember { mutableStateOf<Question?>(null) }

    // 编辑后滚动到目标位置
    LaunchedEffect(editingQuestion, editTargetIndex) {
        if (editingQuestion != null && editTargetIndex != null) {
            // 确保目标题目在当前可见范围内
            if (editTargetIndex >= displayCount) {
                displayCount = editTargetIndex + 1
            }
        }
    }
    // 自动打开编辑对话框
    LaunchedEffect(editQuestionId) {
        if (editQuestionId.isNotBlank()) {
            val target = allQuestions.firstOrNull { it.id == editQuestionId }
            if (target != null) editingQuestion = target
        }
    }

    fun updateBank(newQuestions: List<Question>, msg: String) {
        val newBank = currentBank.copy(questions = newQuestions, updatedAt = System.currentTimeMillis())
        repo.updateBank(newBank)
        scope.launch { snackbarHostState.showSnackbar(msg) }
    }

    Scaffold(

        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = {
            ScreenTopBar(
                currentBank.name,
                onBack = { navController.popBackStack() },
                subtitle = "${allQuestions.size} 题 · 更新于 ${java.text.SimpleDateFormat("MM-dd", java.util.Locale.getDefault()).format(java.util.Date(currentBank.updatedAt))}",
                actions = {
                    IconButton(onClick = {
                        bankNameDraft = currentBank.name
                        bankHintDraft = currentBank.aiHint ?: ""
                        showBankSettings = true
                    }) {
                        Icon(Icons.Filled.Edit, contentDescription = "题库设置")
                    }
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "添加题目")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 统计信息
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem("总题数", allQuestions.size.toString())
                    StatItem("已掌握", allQuestions.count { q -> repo.getStreak(bankId!!, q.id) >= repo.getMasteryTarget() }.toString())
                    StatItem("待学习", allQuestions.count { q -> repo.getStreak(bankId!!, q.id) < repo.getMasteryTarget() }.toString())
                }
            }

            // Tab 切换
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text("全部题目 (${allQuestions.size})") }
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text("疑似错题${if (suspectQuestions.isNotEmpty()) " (${suspectQuestions.size})" else ""}") }
                )
            }

            if (tab == 0) {
                // 搜索框
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    placeholder = { Text("搜索题目") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = if (keyword.isNotEmpty()) {
                        { IconButton(onClick = { keyword = "" }) { Icon(Icons.Filled.Clear, contentDescription = "清除") } }
                    } else null
                )

                // 题目列表（分页）
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(displayedQuestions, key = { it.id }) { question ->
                        QuestionItem(
                            question = question,
                            bankId = bankId!!,
                            repo = repo,
                            onEdit = { editingQuestion = question },
                            onDelete = { showDeleteConfirm = question }
                        )
                    }

                    // 加载更多的触发器
                    if (hasMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                TextButton(onClick = {
                                    displayCount += ITEMS_PER_PAGE
                                }) {
                                    Text("加载更多 (${filteredAll.size - displayCount} 条剩余)")
                                }
                            }
                        }
                    } else if (filteredAll.isNotEmpty()) {
                        item {
                            Text(
                                "共 ${filteredAll.size} 道题",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                textAlign = TextAlign.Center,
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            } else {
                // 疑似错题 Tab
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    "发现 ${suspectQuestions.size} 道疑似错题",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "包括：选项重复、多选答案未用分隔符、ID 缺失/重复、缺少必填项等。可点击「编辑」修正。",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    if (suspectQuestions.any { it.idProblem }) {
                        item {
                            Button(
                                onClick = {
                                    scope.launch {
                                        val fixed = fixQuestionIds(currentBank)
                                        val fixedCount = fixed.zip(currentBank.questions).count { (a, b) -> a.id != b.id }
                                        updateBank(fixed, "已修正 $fixedCount 个题目 ID")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("一键修正重复 / 缺失 ID")
                            }
                        }
                    }
                    items(suspectQuestions, key = { it.id }) { question ->
                        SuspectQuestionItem(
                            question = question,
                            reasons = suspectReasons(question),
                            bankId = bankId!!,
                            repo = repo,
                            onEdit = { editingQuestion = question },
                            onDelete = { showDeleteConfirm = question }
                        )
                    }
                    if (suspectQuestions.isEmpty()) {
                        item {
                            Text(
                                "没有疑似错题，数据质量良好",
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                textAlign = TextAlign.Center,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }

    // 添加/编辑题目对话框
    if (editingQuestion != null) {
        QuestionEditDialog(
            question = editingQuestion!!,
            onSave = { updated ->
                scope.launch {
                    val oldId = editingQuestion!!.id
                    val newQuestions = allQuestions.map { if (it.id == oldId) updated else it }
                    updateBank(newQuestions, "题目已更新")
                    editingQuestion = null
                }
            },
            onDismiss = { editingQuestion = null }
        )
    }

    if (showAddDialog) {
        QuestionEditDialog(
            question = null,
            onSave = { newQ ->
                scope.launch {
                    val newQuestions = allQuestions + newQ
                    updateBank(newQuestions, "题目已添加")
                    showAddDialog = false
                }
            },
            onDismiss = { showAddDialog = false }
        )
    }

    if (showDeleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("确认删除") },
            text = { Text("确定要删除这道题吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repo.deleteQuestions(bankId!!, setOf(showDeleteConfirm!!.id))
                        showDeleteConfirm = null
                        snackbarHostState.showSnackbar("题目已删除")
                    }
                }) { Text("删除", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text("取消") }
            }
        )
    }

    if (showBankSettings) {
        AlertDialog(
            onDismissRequest = { showBankSettings = false },
            title = { Text("题库设置") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = bankNameDraft, onValueChange = { bankNameDraft = it },
                        label = { Text("题库名称") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = bankHintDraft, onValueChange = { bankHintDraft = it },
                        label = { Text("AI 默认提示语（可选）") },
                        placeholder = { Text("如：注册会计师考试") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "该提示会在「AI 解析」时自动带入，让解析更贴合本题库领域。",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repo.updateBank(
                            currentBank.copy(
                                name = bankNameDraft.trim().ifBlank { currentBank.name },
                                aiHint = bankHintDraft.trim().takeIf { it.isNotBlank() }
                            )
                        )
                        showBankSettings = false
                        snackbarHostState.showSnackbar("已保存")
                    }
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showBankSettings = false }) { Text("取消") } }
        )
    }
}

/** 计算疑似错题的具体问题标记（与小程序 edit-bank 检测逻辑等价） */
fun suspectReasons(q: Question): List<String> {
    val reasons = mutableListOf<String>()
    if (q.idProblem) {
        reasons += if (q.id.startsWith("__missing")) "ID 缺失" else "ID 重复"
    }
    if (q.question.isBlank()) reasons += "题干缺失"
    if (q.answer.isNullOrBlank()) reasons += "答案缺失"
    if (q.type == QuestionType.single || q.type == QuestionType.multiple) {
        val opts = (q.options ?: emptyList()).map { it.trim() }.filter { it.isNotEmpty() }
        if (opts.size < 2) reasons += "选项不足 2 个"
        if (opts.size != opts.toSet().size) reasons += "存在重复选项"
        if (q.type == QuestionType.multiple && !q.answer.isNullOrBlank()) {
            val a = q.answer.trim()
            // 多选但答案未用分隔符（如只写一个字母）→ 疑似
            if (!Regex("[,，|｜、]").containsMatchIn(a) && a.length <= 2) reasons += "多选答案未用分隔符"
        }
    }
    return reasons.distinct()
}

/** 一键修正缺失/重复的题目 ID（等价小程序 suggestIdForFix） */
fun fixQuestionIds(bank: QuestionBank): List<Question> {
    val questions = bank.questions
    val idCount = HashMap<String, Int>()
    questions.forEach { q -> idCount[q.id] = (idCount[q.id] ?: 0) + 1 }
    val dupIds = idCount.filter { it.value > 1 }.keys
    val used = questions.map { it.id }.toHashSet()
    val counters = HashMap<String, Int>()
    var counter = 1
    return questions.map { q ->
        val needFix = q.id.startsWith("__missing") || dupIds.contains(q.id)
        if (!needFix) return@map q
        val newId = if (q.id.startsWith("__missing")) {
            while (used.contains("Q$counter")) counter++
            "Q$counter"
        } else {
            val cnt = (counters[q.id] ?: 0) + 1
            counters[q.id] = cnt
            if (cnt == 1) q.id else "${q.id}_dup$cnt"
        }
        used.add(newId)
        q.copy(id = newId, idProblem = false)
    }
}

@Composable
fun QuestionItem(question: Question, bankId: String, repo: AppRepository, onEdit: () -> Unit, onDelete: () -> Unit) {
    val streak = repo.getStreak(bankId, question.id)
    val masteryTarget = repo.getMasteryTarget()
    val isMastered = streak >= masteryTarget

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = question.question,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 3
                )
                Spacer(Modifier.width(8.dp))
                if (isMastered) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.semantic.success)
                }
            }

            if (!question.options.isNullOrEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "答案：${question.answer ?: ""}",
                    fontSize = 12.sp,
                    color = if (isMastered) MaterialTheme.semantic.success else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (question.tags.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    question.tags.forEach { tag ->
                        TagChip(tag)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "错${streak}次 | 目标${masteryTarget}次",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onEdit, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                    Text("编辑", fontSize = 12.sp)
                }
                TextButton(onClick = onDelete, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.semantic.error)
                    Text("删除", fontSize = 12.sp, color = MaterialTheme.semantic.error)
                }
            }
        }
    }
}

/** 疑似错题卡片：展示问题原因标记 + 编辑/删除 */
@Composable
fun SuspectQuestionItem(
    question: Question,
    reasons: List<String>,
    bankId: String,
    repo: AppRepository,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f))
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = question.question,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 3
                )
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                reasons.forEach { reason ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            reason,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = "ID: ${question.id}",
                fontSize = 11.sp,
                color = Color.Gray
            )

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onEdit, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                    Text("编辑修正", fontSize = 12.sp)
                }
                TextButton(onClick = onDelete, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.semantic.error)
                    Text("删除", fontSize = 12.sp, color = MaterialTheme.semantic.error)
                }
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(text = label, fontSize = 12.sp, color = Color.Gray)
    }
}

private fun typeLabel(type: QuestionType): String = when (type) {
    QuestionType.single -> "单选题"
    QuestionType.multiple -> "多选题"
    QuestionType.judge -> "判断题"
    QuestionType.fill -> "填空题"
    QuestionType.essay -> "简答题"
}

@Composable
fun QuestionEditDialog(
    question: Question?,
    onSave: (Question) -> Unit,
    onDismiss: () -> Unit
) {
    var qId by remember(question) { mutableStateOf(question?.id ?: "") }
    var qTypeStr by remember(question) { mutableStateOf(question?.type?.name ?: "single") }
    var qQuestion by remember(question) { mutableStateOf(question?.question ?: "") }
    var qAnswer by remember(question) { mutableStateOf<String?>(question?.answer) }
    var qAnalysis by remember(question) { mutableStateOf(question?.explanation ?: "") }
    var qTags by remember(question) { mutableStateOf((question?.tags ?: emptyList()).joinToString(",")) }
    var qOrderFree by remember(question) { mutableStateOf(question?.orderFree ?: false) }
    var typeMenuOpen by remember { mutableStateOf(false) }
    var idError by remember { mutableStateOf<String?>(null) }
    val optionTexts = remember { mutableStateListOf<String>() }

    // 初始化选项
    LaunchedEffect(question) {
        optionTexts.clear()
        if (question != null) {
            question.options?.forEach { optionTexts.add(it) }
        }
        while (optionTexts.size < 2) optionTexts.add("")
    }

    val qType = QuestionType.valueOf(qTypeStr)
    val letters = listOf('A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J')

    // 填空答案的多空数量（| 分隔）
    val fillParts = (qAnswer ?: "").split(Regex("[|｜]")).map { it.trim() }.filter { it.isNotEmpty() }
    val isMultiFill = qType == QuestionType.fill && fillParts.size > 1

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (question == null) "添加题目" else "编辑题目") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // 题目 ID
                OutlinedTextField(
                    value = qId,
                    onValueChange = {
                        qId = it.trim()
                        idError = if (it.isBlank()) "ID 不能为空" else null
                    },
                    label = { Text("题目 ID") },
                    singleLine = true,
                    isError = idError != null,
                    modifier = Modifier.fillMaxWidth()
                )
                if (idError != null) {
                    Text(idError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }

                // 题型选择
                ExposedDropdownMenuBox(
                    expanded = typeMenuOpen,
                    onExpandedChange = { typeMenuOpen = it }
                ) {
                    OutlinedTextField(
                        value = typeLabel(qType),
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        label = { Text("题型") }
                    )
                    ExposedDropdownMenu(
                        expanded = typeMenuOpen,
                        onDismissRequest = { typeMenuOpen = false }
                    ) {
                        listOf(
                            QuestionType.single to "单选题",
                            QuestionType.multiple to "多选题",
                            QuestionType.judge to "判断题",
                            QuestionType.fill to "填空题",
                            QuestionType.essay to "简答题"
                        ).forEach { (t, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { qTypeStr = t.name; typeMenuOpen = false }
                            )
                        }
                    }
                }

                // 题目内容
                OutlinedTextField(
                    value = qQuestion,
                    onValueChange = { qQuestion = it },
                    label = { Text("题目内容") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )

                // 选项（动态增删）
                if (qType == QuestionType.single || qType == QuestionType.multiple) {
                    Text("选项（A-J，最多10个，可动态增减）", fontSize = 13.sp, color = Color.Gray)
                    optionTexts.forEachIndexed { index, text ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${letters[index]}.",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.width(24.dp)
                            )
                            OutlinedTextField(
                                value = text,
                                onValueChange = { optionTexts[index] = it },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            if (optionTexts.size > 2) {
                                IconButton(
                                    onClick = { optionTexts.removeAt(index) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "删除选项", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                    if (optionTexts.size < 10) {
                        TextButton(
                            onClick = { optionTexts.add("") },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Text("添加选项")
                        }
                    }

                    // 答案选择
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("答案：", fontSize = 14.sp)
                        optionTexts.forEachIndexed { index, _ ->
                            FilterChip(
                                selected = qAnswer?.contains(letters[index].toString()) == true,
                                onClick = {
                                    if (qType == QuestionType.single) {
                                        qAnswer = letters[index].toString()
                                    } else {
                                        qAnswer = if (qAnswer?.contains(letters[index].toString()) == true) {
                                            qAnswer?.replace(letters[index].toString(), "")
                                        } else {
                                            (qAnswer ?: "") + letters[index]
                                        }
                                    }
                                },
                                label = { Text(letters[index].toString()) }
                            )
                        }
                    }
                } else if (qType == QuestionType.judge) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf("正确" to true, "错误" to false).forEach { (label, value) ->
                            FilterChip(
                                selected = qAnswer == label,
                                onClick = { qAnswer = label },
                                label = { Text(label) }
                            )
                        }
                    }
                } else {
                    // 填空 / 简答答案
                    OutlinedTextField(
                        value = qAnswer ?: "",
                        onValueChange = { qAnswer = it },
                        label = { Text(if (qType == QuestionType.fill) "答案（多空用 | 分隔，如：尼罗河|6853）" else "答案要点") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                    if (isMultiFill) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("答题不限顺序", fontSize = 14.sp)
                                Text("开启后填写的空可任意顺序匹配", fontSize = 12.sp, color = Color.Gray)
                            }
                            ShuatiSwitch(checked = qOrderFree, onCheckedChange = { qOrderFree = it })
                        }
                    }
                }

                // 解析
                OutlinedTextField(
                    value = qAnalysis,
                    onValueChange = { qAnalysis = it },
                    label = { Text("解析（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                // 标签
                OutlinedTextField(
                    value = qTags,
                    onValueChange = { qTags = it },
                    label = { Text("标签（可选，逗号分隔）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (qQuestion.isBlank()) return@Button
                    if (qId.isBlank()) return@Button
                    val options = when (qType) {
                        QuestionType.single, QuestionType.multiple -> optionTexts.filter { it.isNotBlank() }
                        else -> emptyList()
                    }
                    val answer = when (qType) {
                        QuestionType.judge -> qAnswer
                        else -> qAnswer?.trim()
                    }
                    val orderFree = qType == QuestionType.fill && qOrderFree
                    val tags = qTags.split(Regex("[,，、]"))
                        .map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                    val newQuestion = question?.copy(
                        id = qId,
                        type = qType,
                        question = qQuestion,
                        answer = answer,
                        explanation = qAnalysis.trim().ifEmpty { null },
                        options = options.ifEmpty { null },
                        tags = tags,
                        orderFree = orderFree,
                        idProblem = false,
                        incomplete = false
                    ) ?: Question(
                        id = qId,
                        type = qType,
                        question = qQuestion,
                        answer = answer,
                        options = options.ifEmpty { null },
                        explanation = qAnalysis.trim().ifEmpty { null },
                        tags = tags,
                        orderFree = orderFree
                    )
                    onSave(newQuestion)
                }
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
