package com.example.shuati.ui.pages

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.shuati.logic.Recommend
import com.example.shuati.model.QuestionType
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
    val favorites = remember { repo.getFavoriteRecords() }
    val history = remember { repo.getHistoryRecords() }
    val recommended = remember(banks, wrongs, favorites, history) {
        Recommend.buildRecommendations(banks, wrongs, favorites, history) { bid, qid ->
            repo.getStreak(bid, qid)
        }
    }

    Scaffold(
        topBar = { ScreenTopBar("智能推荐", onBack = { navController.popBackStack() }) },
        floatingActionButton = {
            if (recommended.isNotEmpty())
                ExtendedFloatingActionButton(
                    onClick = {
                        val ids = recommended.joinToString(",") { "${it.bankId}::${it.question.id}" }
                        navController.navigate("${Routes.QUIZ}?bankId=${recommended[0].bankId}&mode=sequence&source=bank&questionIds=$ids")
                    },
                    icon = { Icon(Icons.Filled.PlayArrow, null) },
                    text = { Text("一键练习 ${recommended.size} 题") }
                )
        }
    ) { padding ->
        if (recommended.isEmpty()) {
            EmptyState("暂无推荐：多刷几道题后，系统会优先推荐你的薄弱点")
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                item {
                    CardBox {
                        Column {
                            Text("推荐逻辑", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "1. 错题（间隔重复：越久未练越优先）\n" +
                                        "2. 收藏题\n" +
                                        "3. 低正确率题库（<70%）\n" +
                                        "4. 新题库探索\n" +
                                        "5. 题型多样性兜底\n\n" +
                                        "共推荐 ${recommended.size} 题，综合分数越高越靠前。",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }

                items(recommended, key = { "${it.bankId}_${it.question.id}" }) { item ->
                    RecItem(
                        item = item,
                        onClick = {
                            navController.navigate("${Routes.QUIZ}?bankId=${item.bankId}&mode=sequence&source=bank&questionIds=${item.bankId}::${item.question.id}")
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RecItem(item: Recommend.RecommendItem, onClick: () -> Unit) {
    val q = item.question
    CardBox(onClick = onClick) {
        Column {
            Text(q.question, fontWeight = FontWeight.Medium, fontSize = 15.sp)

            // 显示选项（如有）
            if (!q.options.isNullOrEmpty()) {
                Spacer(Modifier.height(4.dp))
                Column {
                    q.options!!.withIndex().forEach { (i, opt) ->
                        val letter = ('A' + i).toString()
                        val isCorrect = ScoringHints.isAnswer(q, i)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = if (isCorrect) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.size(22.dp).clip(MaterialTheme.shapes.small)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(letter, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                        color = if (isCorrect) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(opt, fontSize = 13.sp, color = if (isCorrect) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }

            // 显示答案
            if (!q.answer.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("参考答案：", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    Text(q.answer!!, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                reasonChip(item.reason)
                TagChip(Recommend.typeLabel(q.type))
                if (item.bankName.isNotBlank()) TagChip(item.bankName)
            }
        }
    }
}

/** 按推荐原因渲染不同颜色的标签（等价小程序 reasonColor） */
@Composable
private fun reasonChip(reason: String) {
    val container: androidx.compose.ui.graphics.Color
    val label: androidx.compose.ui.graphics.Color
    when {
        reason.startsWith("错题") -> {
            container = MaterialTheme.colorScheme.errorContainer
            label = MaterialTheme.colorScheme.error
        }
        reason == "已收藏" -> {
            container = MaterialTheme.colorScheme.tertiaryContainer
            label = MaterialTheme.colorScheme.tertiary
        }
        reason == "新题库探索" -> {
            container = MaterialTheme.colorScheme.primaryContainer
            label = MaterialTheme.colorScheme.primary
        }
        reason.contains("正确率") -> {
            container = MaterialTheme.colorScheme.secondaryContainer
            label = MaterialTheme.colorScheme.secondary
        }
        else -> {
            container = MaterialTheme.colorScheme.surfaceVariant
            label = MaterialTheme.colorScheme.onSurfaceVariant
        }
    }
    TagChip(reason, containerColor = container, labelColor = label)
}

/** 判断第 i 个选项是否为正确答案（按答案字母匹配） */
private object ScoringHints {
    fun isAnswer(q: com.example.shuati.model.Question, optionIndex: Int): Boolean {
        val answer = q.answer?.trim()?.uppercase() ?: return false
        val letter = ('A' + optionIndex).toString()
        return answer.contains(letter)
    }
}
