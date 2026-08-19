package com.example.shuati.ui.pages

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.shuati.logic.ExcelWriter
import com.example.shuati.model.QuestionBank
import com.example.shuati.ui.*
import com.example.shuati.ui.components.CardBox
import com.example.shuati.ui.components.EmptyState
import com.example.shuati.ui.components.ScreenTopBar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(navController: NavController) {
    val repo = repository()
    val scope = rememberCoroutineScope()
    var banks by remember { mutableStateOf(emptyList<QuestionBank>()) }
    var searchKeyword by remember { mutableStateOf("") }
    var showDelete by remember { mutableStateOf<QuestionBank?>(null) }
    val context = LocalContext.current

    fun loadBanks() {
        banks = repo.getBanks()
    }
    LaunchedEffect(Unit) { loadBanks() }

    val filtered = remember(banks, searchKeyword) {
        if (searchKeyword.isBlank()) banks
        else banks.filter {
            it.name.contains(searchKeyword, ignoreCase = true) ||
                    it.questions.any { q -> q.question.contains(searchKeyword, ignoreCase = true) }
        }
    }

    Scaffold(
        topBar = {
            ScreenTopBar("题库管理", actions = {
                TextButton(onClick = { navController.navigate(Routes.IMPORT) }) { Text("导入") }
            })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate(Routes.EDIT_BANK) }) {
                Icon(Icons.Filled.Add, contentDescription = "新增")
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = searchKeyword,
                onValueChange = { searchKeyword = it },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                placeholder = { Text("搜索题库 / 题目") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = if (searchKeyword.isNotEmpty()) {
                    { IconButton(onClick = { searchKeyword = "" }) { Icon(Icons.Filled.Clear, contentDescription = "清除") } }
                } else null
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                if (filtered.isEmpty()) {
                    item { EmptyState(if (banks.isEmpty()) "还没有题库，点右下角 + 导入" else "没有匹配的题库") }
                }
                items(filtered) { bank ->
                    val progress = repo.getProgress(bank.id)
                    val completed = progress?.everCompleted ?: false
                    val wrongCount = repo.getWrongRecords().count { it.bankId == bank.id }
                    // 计算进度百分比（该题库最高 streak / 目标连对）
                    val target = repo.getMasteryTarget()
                    val bankQuestions = bank.questions.map { it.id }.toSet()
                    val maxStreak = repo.getMasteryStreaks()
                        .filterKeys { it.startsWith("${bank.id}::") }
                        .values.maxOrNull() ?: 0
                    val pct = if (target > 0) (maxStreak.toFloat() / target).coerceIn(0f, 1f) else 0f
                    // 重复题数
                    val dupGroups = repo.findDuplicateGroups(bank, repo.getDedupThreshold())
                    val dupCount = dupGroups.sumOf { it.size } - dupGroups.size
                    CardBox(onClick = { navController.navigate("${Routes.EDIT_BANK}?bankId=${bank.id}") }) {
                        Box(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(bank.name, fontWeight = FontWeight.Medium, fontSize = 17.sp)
                                    Spacer(Modifier.height(4.dp))
                                    Text("${bank.questions.size} 题" + if (wrongCount > 0) " · $wrongCount 错题" else "",
                                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (completed) {
                                        Spacer(Modifier.height(4.dp))
                                        AssistChip(
                                            onClick = {},
                                            label = { Text("已刷完 100%") },
                                            leadingIcon = { Icon(Icons.Filled.CheckCircle, null, Modifier.size(16.dp)) },
                                            colors = AssistChipDefaults.assistChipColors(
                                                containerColor = MaterialTheme.colorScheme.primaryContainer
                                            )
                                        )
                                    }
                                    // 进度条（仅显示非完成状态时的进度）
                                    if (!completed && bank.questions.isNotEmpty()) {
                                        Spacer(Modifier.height(4.dp))
                                        LinearProgressIndicator(
                                            progress = { pct },
                                            modifier = Modifier.fillMaxWidth().height(4.dp)
                                                .clip(MaterialTheme.shapes.small),
                                            color = MaterialTheme.colorScheme.primary,
                                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        Text("${(pct * 100).toInt()}% 掌握进度",
                                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                if (dupCount > 0) {
                                    Spacer(Modifier.width(8.dp))
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Text(dupCount.toString(), fontSize = 10.sp, color = Color.White)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                navController.navigate("${Routes.QUIZ}?bankId=${bank.id}&mode=sequence&source=bank")
                            }) { Text("刷题") }
                            OutlinedButton(onClick = {
                                navController.navigate("${Routes.QUIZ}?bankId=${bank.id}&mode=sequence&source=all")
                            }) { Text("顺序") }
                            OutlinedButton(onClick = {
                                navController.navigate("${Routes.QUIZ}?bankId=${bank.id}&mode=random&source=all")
                            }) { Text("随机") }
                        }
                        if (wrongCount > 0) {
                            Spacer(Modifier.height(6.dp))
                            Button(
                                onClick = { navController.navigate("${Routes.QUIZ}?bankId=${bank.id}&source=wrong") },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("只练 $wrongCount 道错题") }
                        }
                    }
                }
            }
        }
    }

    if (showDelete != null) {
        val bank = showDelete!!
        AlertDialog(
            onDismissRequest = { showDelete = null },
            title = { Text("删除题库") },
            text = { Text("确定要删除「$bank」吗？\n此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repo.deleteBank(bank.id)
                        loadBanks()
                    }
                    showDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = null }) { Text("取消") }
            }
        )
    }
}
