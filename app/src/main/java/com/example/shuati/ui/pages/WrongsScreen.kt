package com.example.shuati.ui.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
import com.example.shuati.ui.components.ScreenTopBar
import com.example.shuati.ui.components.TagChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WrongsScreen(navController: NavController, bankId: String? = null) {
    val repo = repository()
    var records by remember { mutableStateOf(emptyList<WrongRecord>()) }
    var masteryTarget by remember { mutableStateOf(repo.getMasteryTarget()) }
    var showTarget by remember { mutableStateOf(false) }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    val allTags = remember { repo.getAllTags() }

    fun reload() {
        records = repo.getWrongRecords().let { list ->
            if (bankId != null) list.filter { it.bankId == bankId } else list
        }
    }
    LaunchedEffect(Unit) { reload() }

    fun getFilteredRecords(): List<WrongRecord> = if (selectedTag == null) records
    else records.filter { rec -> (rec.question?.tags ?: emptyList()).contains(selectedTag) }

    Scaffold(
        topBar = {
            ScreenTopBar(
                if (bankId != null) "错题本" else "错题本",
                onBack = { navController.popBackStack() },
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
                val displayRecords = getFilteredRecords()
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("共 ${displayRecords.size} 道错题", fontWeight = FontWeight.SemiBold)
                    Button(onClick = {
                        // 错题重做：进入 quiz source=wrong
                        navController.navigate("${Routes.QUIZ}?mode=sequence&source=wrong")
                    }) { Text("全部重做") }
                }
                LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(displayRecords) { rec ->
                        val streak = repo.getStreak(rec.bankId, rec.questionId)
                        CardBox {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                Column(Modifier.weight(1f)) {
                                    Text(rec.question?.question ?: "(题目缺失)", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                                    Spacer(Modifier.height(4.dp))
                                    Text("答错 ${rec.wrongCount} 次　连对 $streak/$masteryTarget", fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if ((rec.question?.tags ?: emptyList()).isNotEmpty()) {
                                        Spacer(Modifier.height(4.dp))
                                        Row { (rec.question?.tags ?: emptyList()).forEach { TagChip(it) } }
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
}
