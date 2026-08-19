package com.example.shuati.ui.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.shuati.model.FavoriteRecord
import com.example.shuati.ui.*
import com.example.shuati.ui.components.CardBox
import com.example.shuati.ui.components.EmptyState
import com.example.shuati.ui.components.ScreenTopBar
import com.example.shuati.ui.components.TagChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(navController: NavController) {
    val repo = repository()
    var records by remember { mutableStateOf(emptyList<FavoriteRecord>()) }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    val allTags = remember { repo.getAllTags() }

    fun reload() { records = repo.getFavoriteRecords() }
    LaunchedEffect(Unit) { reload() }

    fun getFilteredRecords(): List<FavoriteRecord> = if (selectedTag == null) records
    else records.filter { rec -> (rec.question?.tags ?: emptyList()).contains(selectedTag) }

    Scaffold(topBar = { ScreenTopBar("我的收藏", onBack = { navController.popBackStack() }) }) { padding ->
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
                EmptyState("还没有收藏的题目")
            } else {
                val displayRecords = getFilteredRecords()
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("共 ${displayRecords.size} 个收藏", fontWeight = FontWeight.SemiBold)
                    Button(onClick = { navController.navigate("${Routes.QUIZ}?mode=sequence&source=favorite") }) {
                        Text("收藏重做")
                    }
                }
                LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(displayRecords) { rec ->
                        CardBox {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                Column(Modifier.weight(1f)) {
                                    Text(rec.question?.question ?: "(题目缺失)", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                                    Spacer(Modifier.height(4.dp))
                                    Text("来自：${rec.bankName}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (!rec.note.isNullOrBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text("备注：${rec.note}", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                                    }
                                    if ((rec.question?.tags ?: emptyList()).isNotEmpty()) {
                                        Spacer(Modifier.height(4.dp))
                                        Row { (rec.question?.tags ?: emptyList()).forEach { TagChip(it) } }
                                    }
                                }
                                IconButton(onClick = {
                                    repo.removeFavorite(rec.bankId, rec.questionId)
                                    reload()
                                }) { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) }
                            }
                        }
                    }
                }
            }
        }
    }
}
