package com.example.shuati.ui.pages

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.shuati.model.QuestionType
import com.example.shuati.ui.*
import com.example.shuati.ui.components.CardBox
import com.example.shuati.ui.components.EmptyState
import com.example.shuati.ui.components.ScreenTopBar
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(navController: NavController) {
    val repo = repository()
    val banks = remember { repo.getBanks() }
    val history = remember { repo.getHistoryRecords() }
    val wrongCount = remember { repo.getWrongRecords().size }
    val favCount = remember { repo.getFavoriteRecords().size }

    val totalQuestions = banks.sumOf { it.questions.size }
    val totalAnswered = history.sumOf { it.total }
    val totalCorrect = history.sumOf { it.correct }
    val overallAcc = if (totalAnswered > 0) (totalCorrect * 100 / totalAnswered) else 0

    // 最近 7 天：按 finishedAt 分桶（使用 accuracy 字段，无则回退 createdAt/手算）
    val fmtDay = SimpleDateFormat("MM-dd", Locale.getDefault())
    val cal = Calendar.getInstance()
    val last7 = (0..6).map { i ->
        cal.add(Calendar.DAY_OF_MONTH, if (i == 0) 0 else -1)
        fmtDay.format(cal.time)
    }.reversed().toMutableList().also { cal.add(Calendar.DAY_OF_MONTH, 6) }

    val daily = last7.map { day ->
        val records = history.filter {
            val ts = if (it.finishedAt > 0) it.finishedAt else it.createdAt
            ts > 0 && fmtDay.format(Date(ts)) == day
        }
        val t = records.sumOf { it.total }
        val c = records.sumOf { it.correct }
        val acc = if (t > 0) records.mapNotNull { if (it.accuracy > 0) it.accuracy else null }
            .let { accs -> if (accs.isNotEmpty()) accs.average().toInt() else (c * 100 / t) } else null
        day to (t to acc)
    }

    // 按题库正确率
    val byBank = history.groupBy { it.bankId }

    // 按题型正确率（遍历题库题目 + 历史模式）
    val typeAcc = QuestionType.entries.map { type ->
        val typeBanks = banks.map { b -> b.id to b.questions.filter { q -> q.type == type } }.toMap()
        val totalType = typeBanks.values.sumOf { it.size }
        type to totalType
    }.filter { it.second > 0 }

    Scaffold(topBar = { ScreenTopBar("学习统计", onBack = { navController.popBackStack() }) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Stat("题库", banks.size.toString(), Modifier.weight(1f))
                    Stat("总题量", totalQuestions.toString(), Modifier.weight(1f))
                    Stat("累计作答", totalAnswered.toString(), Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Stat("总正确率", "$overallAcc%", Modifier.weight(1f))
                    Stat("错题", wrongCount.toString(), Modifier.weight(1f))
                    Stat("收藏", favCount.toString(), Modifier.weight(1f))
                }
            }

            item {
                CardBox {
                    Text("最近 7 天正确率趋势", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Spacer(Modifier.height(10.dp))
                    if (daily.all { it.second.first == 0 }) {
                        Text("近 7 天暂无刷题记录", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            daily.forEach { (day, pair) ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(pair.second?.let { "$it%" } ?: "—", fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                                    Spacer(Modifier.height(2.dp))
                                    Text(pair.first.toString(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(2.dp))
                                    Text(day, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            item { Text("各题库累计正确率", fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                modifier = Modifier.padding(top = 4.dp)) }
            if (history.isEmpty()) {
                item { EmptyState("暂无刷题记录") }
            } else {
                items(byBank.toList()) { (bid, list) ->
                    val bankName = list.first().bankName
                    val t = list.sumOf { it.total }
                    val c = list.sumOf { it.correct }
                    val acc = if (t > 0) (c * 100 / t) else 0
                    CardBox {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(bankName.ifBlank { bid }, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                                Spacer(Modifier.height(4.dp))
                                Text("作答 ${t} 题　正确 ${c}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("$acc%", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { acc / 100f },
                            modifier = Modifier.fillMaxWidth().height(6.dp)
                        )
                    }
                }
            }

            if (typeAcc.isNotEmpty()) {
                item { Text("题型分布", fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                    modifier = Modifier.padding(top = 4.dp)) }
                items(typeAcc) { (type, count) ->
                    CardBox {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(typeLabel(type), fontWeight = FontWeight.Medium, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            Text("$count 题", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    CardBox(modifier) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
