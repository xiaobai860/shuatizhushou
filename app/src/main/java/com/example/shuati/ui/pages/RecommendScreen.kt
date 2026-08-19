package com.example.shuati.ui.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.shuati.logic.Recommend
import com.example.shuati.model.WrongRecord
import com.example.shuati.ui.*
import com.example.shuati.ui.components.CardBox
import com.example.shuati.ui.components.EmptyState
import com.example.shuati.ui.components.ScreenTopBar
import com.example.shuati.ui.components.TagChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecommendScreen(navController: NavController) {
    val repo = repository()
    val banks = remember { repo.getBanks() }

    val wrongs = remember { repo.getWrongRecords() }
    val recGroups = remember(wrongs) { Recommend.groupByRoot(wrongs) }
    val recommended = remember(wrongs) { Recommend.recommend(wrongs, 15) }

    // 平铺：按根题分组展示，每组内的小问连续
    val display = remember(recGroups) { recGroups }

    Scaffold(
        topBar = { ScreenTopBar("智能推荐", onBack = { navController.popBackStack() }) },
        floatingActionButton = {
            if (recommended.isNotEmpty())
                ExtendedFloatingActionButton(
                    onClick = {
                        // 以第一题所在题库进入错题重做（近似小程序：用错题作为队列）
                        val first = recommended.first()
                        navController.navigate("${Routes.QUIZ}?bankId=${first.bankId}&mode=sequence&source=bank")
                    },
                    icon = { Icon(Icons.Filled.PlayArrow, null) },
                    text = { Text("开始复习") }
                )
        }
    ) { padding ->
        if (wrongs.isEmpty()) {
            EmptyState("暂无推荐：多刷几道题后，系统会优先推荐你的薄弱点")
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                item {
                    Text("为你优先推荐 ${recommended.size} 道待巩固题目（依据错题次数 × 时间衰减，已按题型轮转保证多样性）",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                display.forEachIndexed { gi, group ->
                    item {
                        Text("第 ${gi + 1} 组（${group.size} 题）", fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    items(group) { rec -> RecItem(rec, banks) { navController.navigate("${Routes.QUIZ}?bankId=${rec.bankId}&mode=sequence&source=bank") } }
                }
            }
        }
    }
}

@Composable
private fun RecItem(rec: WrongRecord, banks: List<com.example.shuati.model.QuestionBank>, onClick: () -> Unit) {
    val bankName = banks.find { it.id == rec.bankId }?.name ?: ""
    CardBox(onClick = onClick) {
        Text(rec.question?.question ?: "(题目缺失)", fontWeight = FontWeight.Medium, fontSize = 15.sp)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TagChip("错 ${rec.wrongCount} 次")
            TagChip(Recommend.typeLabel(rec.question?.type))
            if (bankName.isNotBlank()) TagChip(bankName)
        }
    }
}
