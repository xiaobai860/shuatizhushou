package com.example.shuati.ui.pages

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.shuati.model.Question
import com.example.shuati.model.QuestionBank
import com.example.shuati.ui.*
import com.example.shuati.ui.components.CardBox
import com.example.shuati.ui.components.EmptyState
import com.example.shuati.ui.components.ScreenTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DedupScreen(navController: NavController, bankId: String? = null) {
    val repo = repository()
    val banks = remember { repo.getBanks() }
    var selectedBankId by remember { mutableStateOf(bankId ?: banks.firstOrNull()?.id ?: "") }
    var crossBank by remember { mutableStateOf(bankId == null) }
    var threshold by remember { mutableStateOf(repo.getDedupThreshold()) }
    var thresholdText by remember { mutableStateOf(repo.getDedupThreshold().toString()) }
    var showThreshold by remember { mutableStateOf(false) }

    // 分组结果：单题库 -> 题目 id 簇；跨题库 -> (bankId,questionId) 簇
    var groups by remember { mutableStateOf(emptyList<Pair<String, List<String>>>()) }
    var crossGroups by remember { mutableStateOf(emptyList<List<Pair<String, String>>>()) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) } // "bankId:questionId"

    fun toKey(bankId: String, qid: String) = "$bankId:$qid"

    fun runDedup() {
        selectedIds = emptySet()
        if (crossBank) {
            crossGroups = repo.findDuplicateGroupsAcrossBanks(banks, threshold)
            groups = emptyList()
        } else {
            banks.find { it.id == selectedBankId }?.let { bank ->
                groups = repo.findDuplicateGroups(bank, threshold).mapIndexed { idx, ids -> (idx + 1).toString() to ids }
            }
            crossGroups = emptyList()
        }
    }

    LaunchedEffect(selectedBankId, crossBank, threshold) { runDedup() }

    val totalDupcount = if (crossBank) crossGroups.sumOf { it.size } else groups.sumOf { it.second.size }

    Scaffold(
        topBar = {
            ScreenTopBar("查重检测", onBack = { navController.popBackStack() },
                actions = {
                    TextButton(onClick = { showThreshold = true; thresholdText = threshold.toString() }) { Text("阈值 ${"%.2f".format(threshold)}") }
                    if (selectedIds.isNotEmpty()) {
                        TextButton(onClick = {
                            if (crossBank) {
                                // 按题库分组删除
                                selectedIds.map { it.substringBefore(":") }.distinct().forEach { bid ->
                                    val ids = selectedIds.filter { it.startsWith("$bid:") }.map { it.substringAfter(":") }.toSet()
                                    repo.deleteQuestions(bid, ids)
                                }
                            } else {
                                banks.find { it.id == selectedBankId }?.let { repo.deleteQuestions(it.id, selectedIds.map { it.substringAfter(":") }.toSet()) }
                            }
                            runDedup()
                        }) { Text("删除选中(${selectedIds.size})") }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (banks.isEmpty()) {
                EmptyState("还没有题库，无法查重")
                return@Scaffold
            }
            // 跨题库开关
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("跨题库查重", fontSize = 13.sp, modifier = Modifier.weight(1f))
                Switch(checked = crossBank, onCheckedChange = { crossBank = it })
            }
            if (!crossBank) {
                var bankExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(bankExpanded, { bankExpanded = !bankExpanded }, Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        banks.find { it.id == selectedBankId }?.name ?: "选择题库", {},
                        readOnly = true, modifier = Modifier.menuAnchor().fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(bankExpanded) }
                    )
                    ExposedDropdownMenu(bankExpanded, { bankExpanded = false }) {
                        banks.forEach { b ->
                            DropdownMenuItem(text = { Text("${b.name} (${b.questions.size})") },
                                onClick = { selectedBankId = b.id; bankExpanded = false })
                        }
                    }
                }
            }

            if (totalDupcount == 0) {
                EmptyState("未发现重复题目（阈值 ${"%.2f".format(threshold)}）")
            } else {
                Text("共发现 $totalDupcount 道疑似重复题目（相似度 ≥ ${"%.2f".format(threshold)}）",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp))
                LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    if (crossBank) {
                        items(crossGroups) { group ->
                            GroupCard(
                                title = "重复簇（${group.size} 题）",
                                items = group.map { (bid, qid) ->
                                    val q = banks.find { it.id == bid }?.questions?.find { it.id == qid }
                                    Triple(bid, qid, q?.question ?: "(未知)")
                                },
                                selectedIds = selectedIds,
                                onToggle = { key -> selectedIds = if (selectedIds.contains(key)) selectedIds - key else selectedIds + key },
                                keyOf = { (bid, qid, _) -> "$bid:$qid" },
                                labelOf = { (bid, _, text) -> "${banks.find { it.id == bid }?.name ?: bid}：$text" }
                            )
                        }
                    } else {
                        items(groups) { (label, ids) ->
                            val bank = banks.find { it.id == selectedBankId }!!
                            GroupCard(
                                title = "第 $label 组（${ids.size} 题）",
                                items = ids.map { qid -> Triple(selectedBankId, qid, bank.questions.find { it.id == qid }?.question ?: "") },
                                selectedIds = selectedIds,
                                onToggle = { key -> selectedIds = if (selectedIds.contains(key)) selectedIds - key else selectedIds + key },
                                keyOf = { (bid, qid, _) -> "$bid:$qid" },
                                labelOf = { (_, _, text) -> text }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showThreshold) {
        AlertDialog(
            onDismissRequest = { showThreshold = false },
            title = { Text("查重阈值") },
            text = {
                Column {
                    Text("相似度 ≥ 阈值即判定为重复。值越小越严格。", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = thresholdText,
                        onValueChange = { thresholdText = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("0.10 ~ 1.00") }, singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    thresholdText.toDoubleOrNull()?.let {
                        threshold = it.coerceIn(0.1, 1.0)
                        repo.saveDedupThreshold(threshold)
                    }
                    showThreshold = false
                    runDedup()
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showThreshold = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun GroupCard(
    title: String,
    items: List<Triple<String, String, String>>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    keyOf: (Triple<String, String, String>) -> String,
    labelOf: (Triple<String, String, String>) -> String
) {
    CardBox {
        Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        items.forEach { item ->
            val key = keyOf(item)
            val checked = selectedIds.contains(key)
            Row(Modifier.fillMaxWidth().clickable { onToggle(key) }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = checked, onCheckedChange = { onToggle(key) })
                Text(labelOf(item), fontSize = 14.sp, modifier = Modifier.weight(1f),
                    color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
