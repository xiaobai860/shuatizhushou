package com.example.shuati.ui.pages

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import java.io.IOException
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(navController: NavController) {
    val repo = repository()
    val context = LocalContext.current
    var banks by remember { mutableStateOf(emptyList<QuestionBank>()) }
    var keyword by remember { mutableStateOf("") }
    var showDelete by remember { mutableStateOf<QuestionBank?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var clearTarget by remember { mutableStateOf<QuestionBank?>(null) }
    var exportTarget by remember { mutableStateOf<QuestionBank?>(null) }

    fun reload() { banks = repo.getBanks() }
    LaunchedEffect(Unit) { reload() }

    // 导出 Excel：SAF 选择保存位置
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
        exportTarget?.let { bank ->
            if (uri != null) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        ExcelWriter.writeBank(bank, os)
                    }
                    Toast.makeText(context, "已导出：${bank.name}.xlsx", Toast.LENGTH_SHORT).show()
                } catch (e: IOException) {
                    Toast.makeText(context, "导出失败：${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            exportTarget = null
        }
    }
    LaunchedEffect(exportTarget) {
        exportTarget?.let { bank ->
            exportLauncher.launch("${bank.name}.xlsx")
        }
    }

    val filtered = remember(banks, keyword) {
        if (keyword.isBlank()) banks else banks.filter {
            it.name.contains(keyword, ignoreCase = true) ||
                    it.questions.any { q -> q.question.contains(keyword, ignoreCase = true) }
        }
    }

    Scaffold(
        topBar = { ScreenTopBar("题库管理", onBack = { navController.popBackStack() },
            actions = { TextButton(onClick = { showCreate = true }) { Text("新建") } }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate(Routes.IMPORT) }) {
                Icon(Icons.Filled.Add, contentDescription = "导入")
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                placeholder = { Text("搜索题库 / 题目") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = if (keyword.isNotEmpty()) {
                    { IconButton(onClick = { keyword = "" }) { Icon(Icons.Filled.Clear, contentDescription = "清除") } }
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
                    val completed = repo.getProgress(bank.id)?.everCompleted ?: false
                    val wrongCount = repo.getWrongRecords().count { it.bankId == bank.id }
                    CardBox(onClick = { navController.navigate("${Routes.EDIT_BANK}?bankId=${bank.id}") }) {
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
                            }
                            IconButton(onClick = { showDelete = bank }) {
                                Icon(Icons.Filled.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                navController.navigate("${Routes.QUIZ}?bankId=${bank.id}&mode=sequence&source=bank")
                            }) { Text("刷题") }
                            OutlinedButton(onClick = {
                                navController.navigate("${Routes.QUIZ}?bankId=${bank.id}&mode=sequence&source=bank&filterUnanswered=true")
                            }) { Text("未答") }
                            OutlinedButton(onClick = {
                                navController.navigate("${Routes.QUIZ}?bankId=${bank.id}&mode=browse&source=browse")
                            }) { Text("浏览") }
                            OutlinedButton(onClick = { navController.navigate("${Routes.WRONGS}?bankId=${bank.id}") }) { Text("错题") }
                            OutlinedButton(onClick = { navController.navigate("${Routes.DEDUP}?bankId=${bank.id}") }) { Text("查重") }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { exportTarget = bank }) { Text("导出Excel") }
                            TextButton(onClick = { clearTarget = bank }) { Text("清除进度") }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }

    if (showDelete != null) {
        AlertDialog(
            onDismissRequest = { showDelete = null },
            title = { Text("删除题库") },
            text = { Text("确定删除「${showDelete!!.name}」？该操作不可恢复（错题记录将保留）。") },
            confirmButton = {
                TextButton(onClick = {
                    repo.deleteBank(showDelete!!.id)
                    showDelete = null
                    reload()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDelete = null }) { Text("取消") } }
        )
    }

    if (clearTarget != null) {
        AlertDialog(
            onDismissRequest = { clearTarget = null },
            title = { Text("清除进度") },
            text = { Text("确定清除「${clearTarget!!.name}」的刷题进度？错题与收藏不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    repo.clearProgress(clearTarget!!.id)
                    clearTarget = null
                    reload()
                }) { Text("清除") }
            },
            dismissButton = { TextButton(onClick = { clearTarget = null }) { Text("取消") } }
        )
    }

    if (showCreate) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("新建空题库") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("题库名称") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("创建后将进入编辑页手工添加题目。", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = name.trim()
                    if (n.isNotBlank()) {
                        val res = repo.addBank(QuestionBank(id = UUID.randomUUID().toString(), name = n, questions = emptyList()))
                        if (res.success) {
                            showCreate = false
                            reload()
                            val created = repo.getBanks().find { it.name == n }
                            created?.let { navController.navigate("${Routes.EDIT_BANK}?bankId=${it.id}") }
                        } else {
                            Toast.makeText(context, res.message ?: "创建失败", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("取消") } }
        )
    }
}
