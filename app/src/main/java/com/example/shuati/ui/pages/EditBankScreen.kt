package com.example.shuati.ui.pages

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.shuati.data.AppRepository
import com.example.shuati.model.Question
import com.example.shuati.model.QuestionBank
import com.example.shuati.model.QuestionType
import com.example.shuati.ui.components.TagChip
import kotlinx.coroutines.launch

const val ITEMS_PER_PAGE = 50

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditBankScreen(navController: NavController, bankId: String?) {
    val repo = remember { AppRepository(navController.context) }
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
    val displayedQuestions = allQuestions.take(displayCount)
    val hasMore = displayCount < allQuestions.size

    // 编辑题目浮层
    var editingQuestion by remember { mutableStateOf<Question?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    // 删除确认
    var showDeleteConfirm by remember { mutableStateOf<Question?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentBank.name) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
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
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem("总题数", allQuestions.size.toString())
                    StatItem("已掌握", allQuestions.count { q -> repo.getStreak(bankId!!, q.id) >= repo.getMasteryTarget() }.toString())
                    StatItem("待学习", allQuestions.count { q -> repo.getStreak(bankId!!, q.id) < repo.getMasteryTarget() }.toString())
                }
            }

            // 题目列表（分页）
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp),
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
                                Text("加载更多 (${allQuestions.size - displayCount} 条剩余)")
                            }
                        }
                    }
                } else if (allQuestions.isNotEmpty()) {
                    item {
                        Text(
                            "共 ${allQuestions.size} 道题",
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
        }
    }

    // 添加/编辑题目对话框
    if (editingQuestion != null) {
        QuestionEditDialog(
            question = editingQuestion!!,
            onSave = { updated ->
                scope.launch {
                    val newQuestions = allQuestions.map { if (it.id == updated.id) updated else it }
                    val newBank = currentBank.copy(questions = newQuestions)
                    repo.updateBank(newBank)
                    editingQuestion = null
                    snackbarHostState.showSnackbar("题目已更新")
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
                    val newBank = currentBank.copy(questions = newQuestions)
                    repo.updateBank(newBank)
                    showAddDialog = false
                    snackbarHostState.showSnackbar("题目已添加")
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
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50))
                }
            }

            if (!question.options.isNullOrEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "答案：${question.answer ?: ""}",
                    fontSize = 12.sp,
                    color = if (isMastered) Color(0xFF4CAF50) else Color.Gray
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
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFFFF5252))
                    Text("删除", fontSize = 12.sp, color = Color(0xFFFF5252))
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

@Composable
fun QuestionEditDialog(
    question: Question?,
    onSave: (Question) -> Unit,
    onDismiss: () -> Unit
) {
    var qType by remember(question) { mutableStateOf(question?.type?.name ?: "single") }
    var qQuestion by remember(question) { mutableStateOf(question?.question ?: "") }
    var qAnswer by remember(question) { mutableStateOf<String?>(question?.answer) }
    var qAnalysis by remember(question) { mutableStateOf(question?.explanation ?: "") }
    val optionTexts = remember { mutableStateListOf<String>() }

    // 初始化选项
    LaunchedEffect(question) {
        optionTexts.clear()
        if (question != null) {
            question.options?.forEach { optionTexts.add(it) }
        }
        while (optionTexts.size < 2) optionTexts.add("")
    }

    // 确保选项数量在2-7之间
    LaunchedEffect(optionTexts.size) {
        while (optionTexts.size < 2) optionTexts.add("")
        while (optionTexts.size > 7) optionTexts.removeAt(optionTexts.size - 1)
    }

    val letters = listOf('A', 'B', 'C', 'D', 'E', 'F', 'G')

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (question == null) "添加题目" else "编辑题目") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 题型选择
                ExposedDropdownMenuBox(
                    expanded = false,
                    onExpandedChange = {}
                ) {
                    TextField(
                        value = when (qType) {
                            "single" -> "单选题"
                            "multiple" -> "多选题"
                            "judge" -> "判断题"
                            "fill" -> "填空题"
                            else -> qType
                        },
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        label = { Text("题型") }
                    )
                    ExposedDropdownMenu(
                        expanded = false,
                        onDismissRequest = {}
                    ) {
                        listOf("single" to "单选题", "multiple" to "多选题", "judge" to "判断题", "fill" to "填空题").forEach {
                            DropdownMenuItem(
                                text = { Text(it.second) },
                                onClick = { qType = it.first }
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
                if (qType != "fill") {
                    Text("选项（A-G，最多7个）", fontSize = 13.sp, color = Color.Gray)
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
                    if (optionTexts.size < 7) {
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
                                    if (qType == "single") {
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
                } else {
                    // 填空题答案
                    OutlinedTextField(
                        value = qAnswer ?: "",
                        onValueChange = { qAnswer = it },
                        label = { Text("答案（多个答案用逗号分隔）") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 解析
                OutlinedTextField(
                    value = qAnalysis ?: "",
                    onValueChange = { qAnalysis = it },
                    label = { Text("解析（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (qQuestion.isBlank()) return@Button
                    val options = if (qType == "fill") emptyList() else optionTexts.filter { it.isNotBlank() }
                    val newQuestion = question?.copy(
                        type = QuestionType.valueOf(qType),
                        question = qQuestion,
                        answer = qAnswer?.uppercase(),
                        explanation = qAnalysis,
                        options = options
                    ) ?: Question(
                        id = java.util.UUID.randomUUID().toString(),
                        type = QuestionType.valueOf(qType),
                        question = qQuestion,
                        answer = qAnswer?.uppercase(),
                        options = options,
                        explanation = qAnalysis
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
