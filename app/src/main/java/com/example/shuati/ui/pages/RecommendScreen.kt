package com.example.shuati.ui.pages


import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.shuati.ui.theme.semantic
import com.example.shuati.ui.components.ScreenTopBar
import com.example.shuati.ui.components.HeroCard
import com.example.shuati.ui.components.TagChip
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.clickable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecommendScreen(navController: NavController) {
    val repo = repository()
    val banks = remember { repo.getBanks() }
    val wrongs = remember { repo.getWrongRecords() }
    val favorites = remember { repo.getFavoriteRecords() }
    val history = remember { repo.getHistoryRecords() }
    var refreshTick by remember { mutableStateOf(0) }
    val recommended = remember(banks, wrongs, favorites, history, refreshTick) {
        Recommend.buildRecommendations(banks, wrongs, favorites, history) { bid, qid ->
            repo.getStreak(bid, qid)
        }
    }
    // 更新时间（每次进入/刷新时取当前时间）
    val updatedAt = remember(refreshTick) {
        java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
    }
    val coveredBanks = recommended.map { it.bankId }.distinct().size
    val estMinutes = (recommended.size * 20 / 60.0).toInt().coerceAtLeast(1)

    Scaffold(

        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.navigationBars,
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
                    HeroCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("为你精选", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(5.dp))
                                Text("${recommended.size} 题", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = (-0.5).sp, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.height(4.dp))
                                Text("综合错题、收藏、正确率与新题计算", fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(10.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.Refresh, contentDescription = null,
                                        modifier = Modifier.size(13.dp).clickable { refreshTick++ },
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("今天 $updatedAt 更新", fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(14.dp))
                                    Text("预计 $estMinutes 分钟 · 覆盖 $coveredBanks 题库", fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
                        }
                    }
                }
                item {
                    val errN = recommended.count { it.reason.startsWith("错题") }
                    val warnN = recommended.count { it.reason == "已收藏" }
                    val infoN = recommended.count { it.reason.contains("正确率") }
                    val tertN = recommended.count { it.reason == "新题库探索" }
                    CardBox {
                        Text("推荐构成", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            Box(Modifier.weight(errN.toFloat().coerceAtLeast(0.01f)).height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.semantic.error))
                            Box(Modifier.weight(warnN.toFloat().coerceAtLeast(0.01f)).height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.semantic.warning))
                            Box(Modifier.weight(infoN.toFloat().coerceAtLeast(0.01f)).height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.semantic.info))
                            Box(Modifier.weight(tertN.toFloat().coerceAtLeast(0.01f)).height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.tertiary))
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            LegendDot(MaterialTheme.semantic.error, "高频错题 $errN")
                            LegendDot(MaterialTheme.semantic.warning, "收藏未掌握 $warnN")
                            LegendDot(MaterialTheme.semantic.info, "低正确率 $infoN")
                            LegendDot(MaterialTheme.colorScheme.tertiary, "新题 $tertN")
                        }
                    }
                }

                items(recommended, key = { "${it.bankId}_${it.question.id}" }) { item ->
                    RecItem(
                        item = item,
                        wrongInfo = wrongs.firstOrNull { it.bankId == item.bankId && it.questionId == item.question.id },
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
private fun RecItem(item: Recommend.RecommendItem, wrongInfo: com.example.shuati.model.WrongRecord?, onClick: () -> Unit) {
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
            if (wrongInfo != null) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("错 ${wrongInfo.wrongCount} 次", fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.semantic.error)
                    Spacer(Modifier.width(10.dp))
                    val daysAgo = (System.currentTimeMillis() - wrongInfo.lastWrongAt) / 86400000L
                    Text(
                        "最近答错 ${if (daysAgo <= 0) "今天" else if (daysAgo == 1L) "昨天" else "$daysAgo 天前"}",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("时效加权", fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.semantic.warning)
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
