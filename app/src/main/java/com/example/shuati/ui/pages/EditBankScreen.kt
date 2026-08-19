package com.example.shuati.ui.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.shuati.logic.Scoring
import com.example.shuati.model.Question
import com.example.shuati.model.QuestionBank
import com.example.shuati.model.QuestionType
import com.example.shuati.ui.*
import com.example.shuati.ui.components.CardBox
import com.example.shuati.ui.components.ScreenTopBar
import com.example.shuati.ui.components.TagChip
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditBankScreen(navController: NavController, bankId: String?) {
    val repo = repository()
    val bank = bankId?.let { repo.getBankById(it) }
    var showAdd by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<Question?>(null) }
    var keyword by remember { mutableStateOf("") }
    var showRename by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf(bank?.name ?: "") }

    if (bank == null) {
        Scaffold(topBar = { ScreenTopBar("题库", onBack = { navController.popBackStack() }) }) {
            Box(Modifier.fillMaxSize().padding(it), contentAlignment = Alignment.Center) {
                Text("题库不存在")
            }
        }
        return
    }

    val filtered = remember(bank, keyword) {
        if (keyword.isBlank()) bank.questions else bank.questions.filter {
            it.question.contains(keyword, ignoreCase = true) ||
                    (it.answer ?: "").contains(keyword, ignoreCase = true)
        }
    }
    val suspectCount = remember(bank) { bank.questions.count { Scoring.checkIsSuspect(it) } }

    Scaffold(
        topBar = {
            ScreenTopBar(
                bank.name,
                onBack = { navController.popBackStack() },
                actions = {
                    IconButton(onClick = { newName = bank.name; showRename = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = "重命名")
                    }
                }
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = { editTarget = null; showAdd = true }) { Icon(Icons.Filled.Add, null) } }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // 统计条
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatChip("题目", bank.questions.size.toString())
                StatChip("疑似错题", suspectCount.toString(), suspectCount > 0)
                StatChip("标签", bank.questions.flatMap { it.tags }.toSet().size.toString())
            }

            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("搜索题目") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
            )

            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(filtered) { q ->
                    val suspect = Scoring.checkIsSuspect(q)
                    CardBox {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Text(q.question, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                                Spacer(Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    TagChip(typeLabel(q.type))
                                    if (suspect) TagChip("疑似错题")
                                    q.tags.forEach { TagChip(it) }
                                }
                            }
                            IconButton(onClick = { editTarget = q; showAdd = true }) {
                                Icon(Icons.Filled.Edit, null, Modifier.size(18.dp))
                            }
                            IconButton(onClick = {
                                val updated = bank.copy(
                                    questions = bank.questions.filter { it.id != q.id },
                                    updatedAt = System.currentTimeMillis()
                                )
                                repo.updateBank(updated)
                                navController.popBackStack()
                                navController.navigate("${Routes.EDIT_BANK}?bankId=${bank.id}")
                            }) {
                                Icon(Icons.Filled.Delete, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("重命名题库") },
            text = {
                OutlinedTextField(
                    value = newName, onValueChange = { newName = it },
                    singleLine = true, label = { Text("名称") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank() && newName != bank.name) {
                        repo.updateBank(bank.copy(name = newName, updatedAt = System.currentTimeMillis()))
                    }
                    showRename = false
                    navController.popBackStack()
                    navController.navigate("${Routes.EDIT_BANK}?bankId=${bank.id}")
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showRename = false }) { Text("取消") } }
        )
    }

    if (showAdd) {
        QuestionEditorDialog(
            initial = editTarget,
            onDismiss = { showAdd = false },
            onSave = { q ->
                val questions = if (editTarget != null)
                    bank.questions.map { if (it.id == q.id) q else it }
                else bank.questions + q
                repo.updateBank(bank.copy(questions = questions, updatedAt = System.currentTimeMillis()))
                showAdd = false
                navController.popBackStack()
                navController.navigate("${Routes.EDIT_BANK}?bankId=${bank.id}")
            }
        )
    }
}

@Composable
private fun StatChip(label: String, value: String, warn: Boolean = false) {
    Surface(
        color = if (warn) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontWeight = FontWeight.Bold, color = if (warn) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer)
            Text(label, fontSize = 12.sp, color = if (warn) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

/** 新增 / 编辑题目对话框 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuestionEditorDialog(
    initial: Question?,
    onDismiss: () -> Unit,
    onSave: (Question) -> Unit
) {
    var question by remember { mutableStateOf(initial?.question ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: QuestionType.single) }
    var optionsText by remember { mutableStateOf((initial?.options ?: emptyList()).joinToString("\n")) }
    var answer by remember { mutableStateOf(initial?.answer ?: "") }
    var explanation by remember { mutableStateOf(initial?.explanation ?: "") }
    var tagsText by remember { mutableStateOf((initial?.tags ?: emptyList()).joinToString(", ")) }

    val types = QuestionType.values()

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(),
        title = { Text(if (initial == null) "新增题目" else "编辑题目") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(question, { question = it }, label = { Text("题干") }, modifier = Modifier.fillMaxWidth(),
                    minLines = 2)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("题型：", fontSize = 13.sp)
                    Spacer(Modifier.width(4.dp))
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
                        OutlinedTextField(
                            typeLabel(type), {}, readOnly = true,
                            modifier = Modifier.menuAnchor(), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }
                        )
                        ExposedDropdownMenu(expanded, { expanded = false }) {
                            types.forEach {
                                DropdownMenuItem(text = { Text(typeLabel(it)) }, onClick = { type = it; expanded = false })
                            }
                        }
                    }
                }
                if (type == QuestionType.single || type == QuestionType.multiple) {
                    OutlinedTextField(optionsText, { optionsText = it }, label = { Text("选项（每行一个）") },
                        modifier = Modifier.fillMaxWidth().heightIn(max = 140.dp), minLines = 2)
                }
                if (type == QuestionType.judge) {
                    OutlinedTextField(answer, { answer = it }, label = { Text("答案（正确 / 错误）") }, modifier = Modifier.fillMaxWidth())
                } else {
                    OutlinedTextField(answer, { answer = it }, label = { Text("答案（多选用逗号分隔）") }, modifier = Modifier.fillMaxWidth())
                }
                OutlinedTextField(explanation, { explanation = it }, label = { Text("解析（可选）") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                OutlinedTextField(tagsText, { tagsText = it }, label = { Text("标签（逗号分隔，可选）") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val options = if (type == QuestionType.single || type == QuestionType.multiple)
                    optionsText.lines().map { it.trim() }.filter { it.isNotEmpty() } else emptyList()
                val tags = tagsText.split(Regex("[,，]")).map { it.trim() }.filter { it.isNotEmpty() }
                val q = (initial ?: Question(id = UUID.randomUUID().toString(), question = "")).copy(
                    question = question.trim(),
                    type = type,
                    options = options,
                    answer = answer.trim(),
                    explanation = explanation.trim().ifEmpty { null },
                    tags = tags,
                    incomplete = (type == QuestionType.single || type == QuestionType.multiple) &&
                            (options.isEmpty() || answer.isBlank())
                )
                onSave(q)
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
