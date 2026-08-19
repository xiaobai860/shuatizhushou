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
    val recommended = remember(wrongs, banks) {
        Recommend.recommendWithTags(wrongs, 15, banks)
    }

    // bankId -> List<Question> map for question lookup
    val qMap = remember(banks) {
        banks.associate { bank -> bank.id to bank.questions }
    }

    Scaffold(
        topBar = { ScreenTopBar("智能推荐", onBack = { navController.popBackStack() }) },
        floatingActionButton = {
            if (recommended.isNotEmpty())
                ExtendedFloatingActionButton(
                    onClick = {
                        val ids = recommended.map { "${it.bankId}::${it.questionId}" }.joinToString(",")
                        navController.navigate("${Routes.QUIZ}?bankId=${recommended[0].bankId}&mode=sequence&source=bank&questionIds=$ids")
                    },
                    icon = { Icon(Icons.Filled.PlayArrow, null) },
                    text = { Text("一键练习") }
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

                // 按题型分组，预计算每组题型
                val groupedByType = recGroups.map { grp ->
                    val type = grp.firstOrNull()?.let { r ->
                        qMap[r.bankId]?.firstOrNull { q -> q.id == r.questionId }?.type
                            ?: QuestionType.single
                    } ?: QuestionType.single
                    type to grp
                }.sortedBy { (t, _) -> t }

                groupedByType.forEach { (type, grpList) ->
                    item {
                        Text("${typeLabel(type)} · 共 ${grpList.size} 题",
                            fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                    }
                    items(grpList, key = { it.questionId }) { rec ->
                        RecItem(rec, qMap, banks) {
                            navController.navigate("${Routes.QUIZ}?bankId=${rec.bankId}&mode=sequence&source=bank&questionIds=${rec.bankId}::${rec.questionId}")
                        }
                    }
                }
            }
        }
    }
}

private fun typeLabel(t: QuestionType): String = when (t) {
    QuestionType.single -> "单选题"
    QuestionType.multiple -> "多选题"
    QuestionType.judge -> "判断题"
    QuestionType.fill -> "填空题"
    QuestionType.essay -> "简答题"
}

@Composable
private fun RecItem(
    rec: WrongRecord,
    qMap: Map<String, List<com.example.shuati.model.Question>>,
    banks: List<com.example.shuati.model.QuestionBank>,
    onClick: () -> Unit
) {
    val bankName = banks.find { it.id == rec.bankId }?.name ?: ""
    val q = qMap[rec.bankId]?.firstOrNull { it.id == rec.questionId }
    val reasons = remember(rec) { Recommend.computeReasons(rec, banks) }

    CardBox(onClick = onClick) {
        Column {
            Text(q?.question ?: "(题目缺失)", fontWeight = FontWeight.Medium, fontSize = 15.sp)

            if (q != null) {
                // 显示选项（如有）
                if (!q.options.isNullOrEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Column {
                        q.options!!.withIndex().forEach { (i, opt) ->
                            val letter = ('A' + i).toString()
                            val isCorrect = q.answer?.contains(letter) == true
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
            }

            Spacer(Modifier.height(6.dp))
            RowWrap(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                reasons.forEach { r -> TagChip(r) }
                TagChip("错 ${rec.wrongCount} 次",
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    labelColor = MaterialTheme.colorScheme.error)
                TagChip(typeLabel(q?.type ?: QuestionType.single))
                if (bankName.isNotBlank()) TagChip(bankName)
            }
        }
    }
}

@Composable
private fun RowWrap(
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    content: @Composable RowScope.() -> Unit
) {
    Row(horizontalArrangement = horizontalArrangement, verticalAlignment = Alignment.CenterVertically) {
        content()
    }
}
