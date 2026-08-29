package com.example.shuati.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.shuati.model.QuestionType
import com.example.shuati.ui.*
import com.example.shuati.ui.components.CardBox
import com.example.shuati.ui.components.EmptyState
import com.example.shuati.ui.theme.semantic
import com.example.shuati.ui.components.ScreenTopBar
import com.example.shuati.ui.components.HeroCard
import com.example.shuati.ui.components.StatTile
import java.text.SimpleDateFormat
import java.util.*

private val fmtDay = SimpleDateFormat("MM-dd", Locale.getDefault())

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
    val totalTimeSec = history.sumOf { it.durationSec ?: 0 }
    val overallAcc = if (totalAnswered > 0) (totalCorrect * 100 / totalAnswered) else 0

    // 唯一题库列表（排除 __all__）
    val bankList = remember(history) {
        val map = mutableMapOf<String, String>()
        history.forEach { if (it.bankId != "__all__") map[it.bankId] = it.bankName }
        listOf("all" to "全部题库") + map.entries.map { (id, name) -> id to name }
    }

    var selectedBankId by remember { mutableStateOf("all") }
    val filtered = remember(history, selectedBankId) {
        if (selectedBankId == "all") history
        else history.filter { it.bankId == selectedBankId }
    }

    // 核心统计
    val totalSessions = filtered.size
    val totalCorrectFiltered = filtered.sumOf { it.correct }
    val totalAnsweredFiltered = filtered.sumOf { it.total }
    val totalTimeFiltered = filtered.sumOf { it.durationSec ?: 0 }
    val avgAccuracy = if (totalSessions > 0)
        (filtered.sumOf { it.accuracy } / totalSessions) else 0
    val bestAccuracy = if (filtered.isNotEmpty())
        filtered.maxOfOrNull { it.accuracy } ?: 0 else 0
    val overallAccFiltered = if (totalAnsweredFiltered > 0)
        (totalCorrectFiltered * 100 / totalAnsweredFiltered) else 0
    val avgTimePerQ = if (totalAnsweredFiltered > 0) totalTimeFiltered / totalAnsweredFiltered else 0

    // 时间范围筛选（全部 / 本周 / 本月）
    var timeRange by remember { mutableStateOf("all") }
    val weekAgo = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
    val monthAgo = System.currentTimeMillis() - 30L * 24 * 3600 * 1000
    val rangeFiltered = remember(filtered, timeRange) {
        when (timeRange) {
            "week" -> filtered.filter { it.finishedAt >= weekAgo }
            "month" -> filtered.filter { it.finishedAt >= monthAgo }
            else -> filtered
        }
    }
    // 环比：本周（最近7天）vs 上周（再前7天）正确率差
    val weekStart = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
    val prevWeekStart = weekStart - 7L * 24 * 3600 * 1000
    fun accOf(list: List<com.example.shuati.model.QuizHistory>): Int {
        val t = list.sumOf { it.total }
        val c = list.sumOf { it.correct }
        return if (t > 0) c * 100 / t else 0
    }
    val curWeek = filtered.filter { it.finishedAt >= weekStart }
    val prevWeek = filtered.filter { it.finishedAt in prevWeekStart until weekStart }
    val weekDelta = if (curWeek.isNotEmpty() && prevWeek.isNotEmpty()) accOf(curWeek) - accOf(prevWeek) else null

    // 最近7次正确率趋势
    val recent7 = filtered.takeLast(7).reversed()
    val maxTrendAcc = recent7.maxOfOrNull { it.accuracy } ?: 1

    // 正确率分布（4 区间）
    data class AccBandDef(val label: String, val min: Int, val max: Int, val color: Color)
    val accBandDefs = listOf(
        AccBandDef("90%+", 90, 100, MaterialTheme.colorScheme.primary),
        AccBandDef("70~89%", 70, 89, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
        AccBandDef("50~69%", 50, 69, MaterialTheme.colorScheme.tertiary),
        AccBandDef("<50%", 0, 49, MaterialTheme.colorScheme.error)
    )
    data class AccBand(val label: String, val min: Int, val max: Int, val color: Color, var count: Int = 0)
    val accBands = accBandDefs.map { d ->
        AccBand(d.label, d.min, d.max, d.color, filtered.count { it.accuracy in d.min..d.max })
    }
    val maxBandCount = accBands.maxOfOrNull { it.count } ?: 1

    // 刷题模式分布
    val modeLabels = mapOf(
        "sequential" to "顺序",
        "random" to "随机",
        "browse" to "浏览",
        "continue" to "续刷",
        "wrong" to "错题练习"
    )
    val modeCounts = filtered
        .mapNotNull { it.mode }
        .groupingBy { it }
        .eachCount()
        .toList()
        .sortedByDescending { (_, c) -> c }

    // 按题库正确率
    val byBank = filtered.groupBy { it.bankId }

    // 按题型正确率
    val typeAcc = QuestionType.entries.map { type ->
        val typeBanks = banks.map { b -> b.id to b.questions.filter { q -> q.type == type } }.toMap()
        val totalType = typeBanks.values.sumOf { it.size }
        type to totalType
    }.filter { it.second > 0 }

    fun formatTime(sec: Int): String {
        if (sec < 60) return sec.toString() + "秒"
        val m = sec / 60
        val s = sec % 60
        if (sec < 3600) return m.toString() + "分" + if (s > 0) s.toString() + "秒" else ""
        val h = sec / 3600
        val rm = (sec % 3600) / 60
        return h.toString() + "时" + if (rm > 0) rm.toString() + "分" else ""
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = {
            ScreenTopBar(
                "学习统计",
                onBack = { navController.popBackStack() },
                subtitle = "累计 ${totalSessions} 次刷题记录",
                actions = {
                    var showRange by remember { mutableStateOf(false) }
                    TextButton(onClick = { showRange = true }) {
                        Text(
                            when (timeRange) { "week" -> "本周"; "month" -> "本月"; else -> "全部" },
                            fontSize = 12.5.sp
                        )
                    }
                    if (showRange) {
                        AlertDialog(
                            onDismissRequest = { showRange = false },
                            title = { Text("时间范围") },
                            text = {
                                Column {
                                    listOf("all" to "全部时间", "month" to "近 30 天", "week" to "近 7 天").forEach { (v, label) ->
                                        TextButton(onClick = { timeRange = v; showRange = false }, modifier = Modifier.fillMaxWidth()) {
                                            Text(if (timeRange == v) "● $label" else label, color = MaterialTheme.colorScheme.onSurface)
                                        }
                                    }
                                }
                            },
                            confirmButton = { TextButton(onClick = { showRange = false }) { Text("取消") } }
                        )
                    }
                }
            )
        }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // 题库筛选 Tab
            if (bankList.size > 1) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        bankList.forEach { (id, name) ->
                            FilterChip(
                                selected = selectedBankId == id,
                                onClick = { selectedBankId = id },
                                label = { Text(name, fontSize = 12.sp) },
                                modifier = Modifier.height(32.dp)
                            )
                        }
                    }
                }
            }

            // 核心数据卡片（渐变色）
            // 总正确率 Hero + 统计块
            item {
                HeroCard {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("总正确率", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text("$overallAccFiltered%", fontSize = 44.sp, fontWeight = FontWeight.Black,
                            letterSpacing = (-2).sp, lineHeight = 50.sp,
                            color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.TrendingUp, null,
                                tint = if ((weekDelta ?: 0) >= 0) MaterialTheme.semantic.success else MaterialTheme.semantic.error,
                                modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (weekDelta != null) "较上周 ${if (weekDelta >= 0) "+" else ""}$weekDelta 个百分点"
                                else "累计刷题 $totalAnsweredFiltered 题",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    StatTile(totalSessions.toString(), "刷题次数", Modifier.weight(1f))
                    StatTile(totalAnsweredFiltered.toString(), "累计题目", Modifier.weight(1f))
                    StatTile(formatTime(totalTimeFiltered), "总用时", Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    StatTile("$bestAccuracy%", "最高正确率", Modifier.weight(1f), valueColor = MaterialTheme.semantic.success)
                    StatTile("$avgAccuracy%", "平均正确率", Modifier.weight(1f))
                    StatTile(if (avgTimePerQ > 0) "${avgTimePerQ}s" else "—", "平均每题", Modifier.weight(1f))
                }
            }

            // 最近N次正确率趋势
            if (recent7.size >= 2) {
                item {
                    CardBox {
                        Text("近期趋势（最近 ${recent7.size} 次）", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            recent7.forEachIndexed { i, rec ->
                                val h = maxOf(8, (rec.accuracy.toFloat() / maxTrendAcc * 72).toInt())
                                val isLast = i == recent7.size - 1
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${rec.accuracy}%", fontWeight = if (isLast) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 11.sp, color = if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(2.dp))
                                    Box(
                                        modifier = Modifier
                                            .width(32.dp)
                                            .height(h.dp)
                                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                            .background(if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer)
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    val df = SimpleDateFormat("dd日", Locale.getDefault())
                                    Text(df.format(Date(rec.finishedAt)), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            // 正确率分布
            item {
                CardBox {
                    Text("正确率分布", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        accBands.forEach { band ->
                            val label = band.label
                            val count = band.count
                            val color = band.color
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(48.dp))
                                Spacer(Modifier.width(8.dp))
                                Box(modifier = Modifier.weight(1f).height(10.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(5.dp))) {
                                    if (maxBandCount > 0) {
                                        Box(
                                            modifier = Modifier
                                                .height(10.dp)
                                                .fillMaxWidth((count.toFloat() / maxBandCount).coerceAtMost(1f))
                                                .clip(RoundedCornerShape(5.dp))
                                                .background(color)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                Text("$count 次", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // 刷题模式分布
            if (modeCounts.isNotEmpty()) {
                item {
                    Text("刷题模式分布", fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                        modifier = Modifier.padding(top = 4.dp))
                    CardBox {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            modeCounts.forEach { (mode, count) ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(modeLabels[mode] ?: mode, fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium, modifier = Modifier.width(50.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(14.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .fillMaxWidth(
                                                    count.toFloat() / modeCounts.maxOfOrNull { (_, c) -> c }!!
                                                )
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text("$count 次", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                item {
                    val totalT = typeAcc.sumOf { it.second }
                    val typeColors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.secondary,
                        MaterialTheme.colorScheme.tertiary,
                        MaterialTheme.semantic.info,
                        MaterialTheme.colorScheme.surfaceVariant
                    )
                    CardBox {
                        Text("题型分布", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Spacer(Modifier.height(12.dp))
                        if (totalT > 0) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                typeAcc.forEachIndexed { i, (_, count) ->
                                    Box(
                                        Modifier
                                            .weight((count.toFloat() / totalT).coerceAtLeast(0.01f))
                                            .height(10.dp)
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(typeColors[i % typeColors.size])
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            typeAcc.forEachIndexed { i, (type, count) ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(3.dp)).background(typeColors[i % typeColors.size]))
                                    Spacer(Modifier.width(6.dp))
                                    Text(typeLabel(type), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f))
                                    Text("$count 题 · ${if (totalT > 0) count * 100 / totalT else 0}%", fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCell(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 24.sp)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

private fun typeLabel(t: QuestionType): String = when (t) {
    QuestionType.single -> "单选题"
    QuestionType.multiple -> "多选题"
    QuestionType.judge -> "判断题"
    QuestionType.fill -> "填空题"
    QuestionType.essay -> "简答题"
}
