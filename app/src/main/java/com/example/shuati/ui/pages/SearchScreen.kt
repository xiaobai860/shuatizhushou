package com.example.shuati.ui.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.shuati.ui.*
import com.example.shuati.ui.components.CardBox
import com.example.shuati.ui.components.EmptyState
import com.example.shuati.ui.components.ScreenTopBar
import com.example.shuati.ui.components.typeLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(navController: NavController) {
    val repo = repository()
    var keyword by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    val allTags = remember { repo.getAllTags() }

    // 收集所有 (题库, 题目)
    val all = remember {
        repo.getBanks().flatMap { bank -> bank.questions.map { bank to it } }
    }
    val results = remember(keyword, all, selectedTag) {
        val list = if (keyword.isBlank()) emptyList()
        else all.filter { (_, q) ->
            q.question.contains(keyword, ignoreCase = true) ||
                    (q.answer ?: "").contains(keyword, ignoreCase = true) ||
                    q.tags.any { it.contains(keyword, ignoreCase = true) }
        }
        if (selectedTag != null) list.filter { (_, q) -> selectedTag in q.tags } else list
    }

    Scaffold(topBar = { ScreenTopBar("题目搜索", onBack = { navController.popBackStack() }) }) { padding ->
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
            OutlinedTextField(
                value = keyword, onValueChange = { keyword = it },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                placeholder = { Text("搜索题目 / 答案 / 标签") },
                singleLine = true, leadingIcon = { Icon(Icons.Filled.Search, null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                trailingIcon = if (keyword.isNotEmpty()) {
                    { IconButton(onClick = { keyword = "" }) { Icon(Icons.Filled.Clear, null) } }
                } else null
            )
            if (keyword.isBlank()) {
                EmptyState("输入关键词开始搜索")
            } else if (results.isEmpty()) {
                EmptyState("未找到匹配的题目")
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    item { Text("找到 ${results.size} 道题目", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(results) { (bank, q) ->
                        CardBox(onClick = {
                            navController.navigate("${Routes.QUIZ}?bankId=${bank.id}&mode=sequence&source=bank&questionIds=${bank.id}::${q.id}")
                        }) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                Column(Modifier.weight(1f)) {
                                    Text(q.question, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                                    Spacer(Modifier.height(4.dp))
                                    Text("来源：${bank.name}　${typeLabel(q.type)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
