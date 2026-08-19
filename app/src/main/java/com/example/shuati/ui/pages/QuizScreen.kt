package com.example.shuati.ui.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.shuati.data.AppRepository
import com.example.shuati.logic.Scoring
import com.example.shuati.model.Question
import com.example.shuati.model.QuestionBank
import com.example.shuati.model.QuestionType
import com.example.shuati.ui.*
import com.example.shuati.ui.components.ScreenTopBar
import com.example.shuati.ui.components.TagChip
import com.example.shuati.ui.components.CardBox
import kotlinx.coroutines.delay
import java.util.UUID

/**
 * 答题页。等价小程序 pages/quiz。
 * source:
 *   bank(普通刷题) / wrong(错题重做) / favorite(收藏重做) / plan(学习计划)
 *   all(全部题库) / browse-*(浏览模式，source 仅用于标识，mode=browse 控制行为)
 * mode:
 *   sequence(顺序) / random(随机) / continue(续刷断点) / browse(浏览，不判分)
 * 额外参数 filterUnanswered=true 时仅刷未答过的题。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(
    navController: NavController,
    bankId: String?,
    mode: String,
    source: String,
    planId: String?,
    filterUnanswered: Boolean = false,
    questionIds: String = "",
    showModeSelect: Boolean = false
) {
    val repo = repository()

    // 模式选择：showModeSelect=true 时先让用户选 顺序/随机/浏览
    var selMode by remember { mutableStateOf<String?>(if (showModeSelect) null else mode) }
    val isBrowse = selMode == "browse"

    // 解析 questionIds 参数：逗号分隔的题库ID列表，格式为 "bankId::questionId,bankId::questionId,..."
    val specifiedQuestions = remember(questionIds, repo) {
        if (questionIds.isBlank()) emptyList<Pair<String, com.example.shuati.model.Question>>()
        else {
            val banks = repo.getBanks()
            val qMap = HashMap<String, com.example.shuati.model.Question>()
            banks.forEach { b -> b.questions.forEach { q -> qMap["${b.id}::${q.id}"] = q } }
            questionIds.split(",").mapNotNull { entry ->
                val q = qMap[entry.trim()] ?: return@mapNotNull null
                val bid = entry.split("::")[0]
                bid to q
            }
        }
    }

    // 只刷未做题：本地可切换（初始取导航参数，如结果页「只刷未做」入口）
    var filterLocal by remember { mutableStateOf(filterUnanswered) }

    // 计算本次作答序列（含真实 bankId）
    val queue = remember(bankId, selMode, source, planId, filterLocal, specifiedQuestions) {
        buildQueue(repo, bankId, selMode ?: "", source, planId, filterLocal, specifiedQuestions)
    }

    var index by remember { mutableStateOf(0) }
    var resumedOnce by remember { mutableStateOf(false) }
    var showJump by remember { mutableStateOf(false) }

    // 续刷/浏览：从断点开始
    LaunchedEffect(queue) {
        if (!resumedOnce) {
            resumedOnce = true
            when {
                source == "bank" && mode == "continue" && bankId != null -> {
                    val resumed = repo.getProgress(bankId)?.answeredIds?.size ?: 0
                    index = resumed.coerceAtMost(queue.lastIndex.coerceAtLeast(0))
                }
                isBrowse && planId != null -> {
                    val bi = repo.getPlanProgress(planId)?.sessionBrowseIndex ?: 0
                    index = bi.coerceAtMost(queue.lastIndex.coerceAtLeast(0))
                }
            }
        }
    }

    // 题库名（模式选择页与答题页共用）
    val bankName = resolveBankName(repo, source, bankId, planId, selMode == "browse")

    // 模式选择页
    if (selMode == null) {
        val bankTotal = queue.size
        val doneCount = when {
            bankId == "__all__" || source == "all" -> repo.getProgress("__all__")?.answeredIds?.size ?: 0
            bankId != null -> repo.getProgress(bankId)?.answeredIds?.size ?: 0
            else -> 0
        }
        val hasProgress = doneCount > 0 || (bankId != null && (repo.getBrowseProgress(bankId) ?: 0) > 0)
        Scaffold(topBar = { ScreenTopBar("选择刷题模式", onBack = { navController.popBackStack() }) }) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(bankName.ifEmpty { "题库" }, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("共 $bankTotal 道题，请选择刷题模式", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                listOf(
                    "sequence" to "顺序模式" to "从第一题开始，按顺序作答",
                    "random" to "随机模式" to "随机打乱顺序作答",
                    "browse" to "浏览模式" to "直接显示答案，逐题浏览"
                ).forEach { (pair, desc) ->
                    val m = pair.first
                    val label = pair.second
                    CardBox(onClick = { selMode = m }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                when (m) {
                                    "sequence" -> Icons.Filled.Sort
                                    "random" -> Icons.Filled.Shuffle
                                    else -> Icons.Filled.Book
                                },
                                null, tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(label, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                                Text(desc, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                // 跳过已答题目 / 继续浏览（有进度时显示）
                if (!filterUnanswered || hasProgress) {
                    Row(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                            .toggleable(value = filterLocal, onValueChange = { filterLocal = it })
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Switch(checked = filterLocal, onCheckedChange = { filterLocal = it })
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("跳过已答题目 / 继续浏览", fontWeight = FontWeight.Medium)
                            if (doneCount > 0)
                                Text("已答 $doneCount / $bankTotal 题", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // 清除进度
                if (hasProgress) {
                    OutlinedButton(
                        onClick = {
                            when {
                                bankId == "__all__" || source == "all" -> repo.clearProgress("__all__")
                                bankId != null -> repo.clearProgress(bankId)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("清除刷题进度") }
                }
            }
        }
        return
    }

    if (queue.isEmpty()) {
        Scaffold(topBar = { ScreenTopBar("答题", onBack = { navController.popBackStack() }) }) {
            Box(Modifier.fillMaxSize().padding(it), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("没有可作答的题目")
                    if (filterLocal) {
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { filterLocal = false }) {
                            Text("取消「只刷未做题」，显示全部")
                        }
                    }
                }
            }
        }
        return
    }

    val current = queue[index]

    // 状态
    var selected by remember(current.q.id) { mutableStateOf(initialAnswer(current.q)) }
    var submitted by remember(current.q.id) { mutableStateOf(isBrowse) } // 浏览模式默认“已展示”
    var autoNext by remember { mutableStateOf(true) }
    var elapsed by remember { mutableStateOf(0) }
    var questionElapsed by remember { mutableStateOf(0) }
    var correctCount by remember { mutableStateOf(0) }
    var wrongCount by remember { mutableStateOf(0) }
    val answeredThisSession = remember { mutableStateListOf<String>() }
    var finished by remember { mutableStateOf(false) }
    var favState by remember(current.q.id) { mutableStateOf(repo.isFavorite(current.bankId, current.q.id)) }
    var showExitConfirm by remember { mutableStateOf(false) }

    // 填空多空输入与顺序无关开关（随题目重置；初始取题目 orderFree 属性）
    val fillSlotN = Scoring.fillSlotCount(current.q)
    var fillInputs by remember(current.q.id, fillSlotN) { mutableStateOf(List(fillSlotN) { "" }) }
    var fillOrderFree by remember(current.q.id) { mutableStateOf(current.q.orderFree) }

    // 判分：填空走多空判分（支持顺序无关），其余走通用判分
    fun judgeAnswer(q: Question, sel: Any?): Boolean = when (q.type) {
        QuestionType.fill -> Scoring.judgeFill(fillInputs, q, fillOrderFree)
        else -> Scoring.judge(sel, q)
    }

    // 只刷未做题开关：切换后从第一题重建队列
    fun toggleFilter() {
        filterLocal = !filterLocal
        index = 0
        showJump = false
        submitted = isBrowse
        selected = initialAnswer(current.q)
    }

    // 浏览模式记录浏览进度
    LaunchedEffect(index, isBrowse) {
        if (isBrowse && planId != null) repo.setPlanBrowseIndex(planId, index)
    }

    // 计时（仅判分模式计时；本题用时在提交后暂停）
    if (!isBrowse) {
        LaunchedEffect(Unit) {
            while (!finished) {
                delay(1000)
                elapsed++
                if (!submitted) questionElapsed++
            }
        }
    }

    // 退出前保存本次刷题历史（中途退出也会记录；浏览模式仅在浏览完成时记录）
    fun saveHistoryAndExit(finishedBrowse: Boolean = false) {
        val histBankId = if (source == "all" || bankId == "__all__") "__all__" else (bankId ?: "__all__")
        val total = if (isBrowse) { if (finishedBrowse) queue.size else 0 } else answeredThisSession.size
        if (!finished && total > 0) {
            repo.addHistoryRecord(
                com.example.shuati.model.QuizHistory(
                    id = UUID.randomUUID().toString(),
                    bankId = histBankId, bankName = bankName,
                    total = total, correct = if (isBrowse) 0 else correctCount,
                    wrong = if (isBrowse) 0 else wrongCount,
                    durationSec = elapsed, mode = selMode ?: mode,
                    accuracy = if (total > 0) (if (isBrowse) 0 else correctCount * 100 / total) else 0,
                    finishedAt = System.currentTimeMillis(),
                    createdAt = System.currentTimeMillis()
                )
            )
        }
        navController.popBackStack()
    }
    BackHandler(onBack = { showExitConfirm = true })

    fun commitProgress(correct: Boolean) {
        if (isBrowse) return // 浏览模式不记录判分/错题/进度
        val q = current.q
        if (correct) repo.incrementStreak(current.bankId, q.id)
        else {
            repo.resetStreak(current.bankId, q.id)
            repo.addWrongRecord(
                com.example.shuati.model.WrongRecord(
                    bankId = current.bankId, bankName = bankName,
                    questionId = q.id, question = q,
                    recordedAt = System.currentTimeMillis(), createdAt = System.currentTimeMillis()
                )
            )
        }
        if (repo.getStreak(current.bankId, q.id) >= repo.getMasteryTarget()) {
            repo.removeWrongRecord(current.bankId, q.id)
        }
        when (source) {
            "plan" -> planId?.let { repo.savePlanAnswer(it, q.id) }
            "bank", "continue" -> bankId?.let {
                val done = queue.take(index + 1).map { it.q.id }
                val newAnswered = (repo.getProgress(it)?.answeredIds ?: emptyList()) + done
                repo.saveProgress(it, newAnswered.distinct(), index >= queue.lastIndex)
            }
            // 全部题库：进度统一记到 __all__（namespaced，避免跨题库 id 冲突）
            "all" -> {
                val done = queue.take(index + 1).map { "${it.bankId}::${it.q.id}" }
                val newAnswered = (repo.getProgress("__all__")?.answeredIds ?: emptyList()) + done
                repo.saveProgress("__all__", newAnswered.distinct(), index >= queue.lastIndex)
            }
        }
    }

    Scaffold(
        topBar = {
            ScreenTopBar(
                bankName.ifEmpty { if (isBrowse) "浏览" else "答题" },
                onBack = { saveHistoryAndExit() },
                actions = {
                    // 跳题浮层
                    IconButton(onClick = { showJump = true }) {
                        Icon(Icons.Filled.ListAlt, contentDescription = "跳题")
                    }
                    // 只刷未做题开关（与 FlashOn 相邻；点击重建队列）
                    IconButton(onClick = { toggleFilter() }) {
                        Icon(
                            if (filterLocal) Icons.Filled.FilterAlt else Icons.Filled.FilterAltOff,
                            contentDescription = "只刷未做题",
                            tint = if (filterLocal) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }
                    if (!isBrowse) {
                        IconButton(onClick = { autoNext = !autoNext }) {
                            Icon(if (autoNext) Icons.Filled.FlashOn else Icons.Filled.FlashOff, contentDescription = "自动下一题")
                        }
                    }
                    IconButton(onClick = {
                        favState = repo.toggleFavorite(
                            com.example.shuati.model.FavoriteRecord(
                                bankId = current.bankId, bankName = bankName, questionId = current.q.id,
                                question = current.q, createdAt = System.currentTimeMillis()
                            )
                        )
                    }) {
                        Icon(
                            if (favState) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = "收藏",
                            tint = if (favState) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LinearProgressIndicator(
                progress = { (index + if (submitted && !isBrowse) 1 else 0).toFloat() / queue.size },
                modifier = Modifier.fillMaxWidth().height(4.dp)
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    .clickable { showJump = true },
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("第 ${index + 1} / ${queue.size} 题", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (isBrowse) "浏览模式"
                    else "本题 ${questionElapsed}s · 总 ${elapsed}s",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                Modifier.fillMaxSize().padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()).weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 题型标签
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        typeLabel(current.q.type),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }

                CardBox {
                    Text(current.q.question, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 24.sp)
                    if (current.q.tags.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            current.q.tags.forEach { TagChip(it) }
                        }
                    }
                }

                when (current.q.type) {
                    QuestionType.single, QuestionType.multiple -> {
                        val chosen = (selected as? Set<*>)?.filterIsInstance<Int>()?.toSet() ?: emptySet()
                        current.q.options?.forEachIndexed { i, opt ->
                            val letter = ('A' + i).toString()
                            val isSel = chosen.contains(i)
                            val isCorrectOption = Scoring.parseAnswerIndices(current.q.answer, current.q.options).contains(i)
                            val containerColor = when {
                                !submitted -> if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                isSel && isCorrectOption -> MaterialTheme.colorScheme.primaryContainer
                                isSel && !isCorrectOption -> MaterialTheme.colorScheme.errorContainer
                                !isSel && isCorrectOption -> MaterialTheme.colorScheme.tertiaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                            Surface(
                                color = containerColor, shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth().clickable(enabled = !submitted) {
                                    selected = if (current.q.type == QuestionType.single) setOf(i)
                                    else (chosen + i).toSet()
                                }
                            ) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("$letter.", fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
                                    Text(opt, modifier = Modifier.weight(1f))
                                    if (submitted) {
                                        when {
                                            isSel && isCorrectOption -> Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                                            isSel && !isCorrectOption -> Icon(Icons.Filled.Cancel, null, tint = MaterialTheme.colorScheme.error)
                                            !isSel && isCorrectOption -> Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.tertiary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    QuestionType.judge -> {
                        val chosen = selected as? Boolean?
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            listOf(true to "正确", false to "错误").forEach { (value, label) ->
                                val isSel = chosen == value
                                OutlinedButton(
                                    onClick = { if (!submitted) selected = value },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (isSel && !submitted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                    )
                                ) { Text(label) }
                            }
                        }
                    }
                    QuestionType.fill -> {
                        // 多空填空输入（每空一个输入框）
                        fillInputs.forEachIndexed { i, v ->
                            val slotCorrect = submitted && !isBrowse &&
                                Scoring.fillSlotCorrectness(fillInputs, current.q, fillOrderFree).getOrNull(i) == true
                            val slotWrong = submitted && !isBrowse &&
                                Scoring.fillSlotCorrectness(fillInputs, current.q, fillOrderFree).getOrNull(i) == false
                            OutlinedTextField(
                                value = v,
                                onValueChange = { newVal ->
                                    if (!submitted) fillInputs = fillInputs.toMutableList().also { it[i] = newVal }
                                },
                                label = { Text(if (fillSlotN > 1) "第 ${i + 1} 空" else "输入答案") },
                                enabled = !submitted,
                                isError = slotWrong,
                                supportingText = when {
                                    slotWrong -> { { Text("答错") } }
                                    slotCorrect -> { { Text("答对", color = MaterialTheme.colorScheme.primary) } }
                                    else -> null
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        // 多空且未提交：显示「不限答案顺序」开关（切换后持久化到题库，下次刷到自动恢复）
                        if (fillSlotN > 1 && !submitted) {
                            fun toggleOrderFree(next: Boolean) {
                                fillOrderFree = next
                                // 持久化到题库：__all__ 模式下 current.bankId 指向真实题库
                                val realBankId = current.bankId.ifEmpty { bankId }
                                val targetBank = repo.getBankById(realBankId ?: "") ?: return
                                val qIdx = targetBank.questions.indexOfFirst { it.id == current.q.id }
                                if (qIdx < 0) return
                                val qs = targetBank.questions.toMutableList()
                                qs[qIdx] = qs[qIdx].copy(orderFree = next)
                                repo.updateBank(targetBank.copy(questions = qs))
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable { toggleOrderFree(!fillOrderFree) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Switch(
                                    checked = fillOrderFree,
                                    onCheckedChange = { toggleOrderFree(it) },
                                    modifier = Modifier.scale(0.8f)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("不限答案顺序", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    QuestionType.essay -> {
                        OutlinedTextField(
                            value = (selected as? String) ?: "",
                            onValueChange = { if (!submitted) selected = it },
                            label = { Text("输入要点") },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                            enabled = !submitted
                        )
                    }
                }

                if (submitted) {
                    CardBox {
                        if (isBrowse) {
                            // 浏览模式：直接展示答案卡片（含解析）
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text("正确答案", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(formatAnswer(current.q), fontWeight = FontWeight.Medium, fontSize = 15.sp)
                            if (!current.q.explanation.isNullOrBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text("解析：${current.q.explanation}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)
                            }
                        } else {
                            val correct = judgeAnswer(current.q, selected)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (correct) Icons.Filled.CheckCircle else Icons.Filled.Cancel, null,
                                    tint = if (correct) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(if (correct) "回答正确" else "回答错误", fontWeight = FontWeight.SemiBold,
                                    color = if (correct) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("正确答案：${formatAnswer(current.q)}", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            if (current.q.type == QuestionType.fill && fillSlotN > 1) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "（${if (fillOrderFree) "已开启不限顺序" else "按顺序匹配"}）",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (!current.q.explanation.isNullOrBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text("解析：${current.q.explanation}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)
                            }
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isBrowse) {
                    // 浏览模式：不判分，仅上一题/下一题/退出
                    OutlinedButton(onClick = { showExitConfirm = true }, modifier = Modifier.weight(1f)) { Text("退出") }
                    Button(
                        onClick = {
                            if (index >= queue.lastIndex) saveHistoryAndExit(true)
                            else { index++; submitted = true; selected = initialAnswer(queue[index].q) }
                        },
                        modifier = Modifier.weight(2f)
                    ) { Text(if (index >= queue.lastIndex) "完成浏览" else "下一题") }
                } else if (!submitted) {
                    OutlinedButton(onClick = { showExitConfirm = true }, modifier = Modifier.weight(1f)) { Text("退出") }
                    Button(onClick = {
                        val isEmpty = when (current.q.type) {
                            QuestionType.fill -> fillInputs.none { it.isNotBlank() }
                            else -> selected == initialAnswer(current.q)
                        }
                        if (isEmpty) return@Button
                        val correct = judgeAnswer(current.q, selected)
                        commitProgress(correct)
                        submitted = true
                        if (correct) correctCount++ else wrongCount++
                        answeredThisSession.add(current.q.id)
                    }, modifier = Modifier.weight(2f)) { Text("提交") }
                } else {
                    Button(
                        onClick = {
                            if (index >= queue.lastIndex) {
                                finished = true
                                val histBankId = if (source == "all" || bankId == "__all__") "__all__" else (bankId ?: "__all__")
                                val total = answeredThisSession.size
                                val acc = if (total > 0) (correctCount * 100 / total) else 0
                                repo.addHistoryRecord(
                                    com.example.shuati.model.QuizHistory(
                                        id = UUID.randomUUID().toString(),
                                        bankId = histBankId, bankName = bankName,
                                        total = total, correct = correctCount,
                                        wrong = wrongCount, durationSec = elapsed, mode = mode,
                                        accuracy = acc, finishedAt = System.currentTimeMillis(),
                                        createdAt = System.currentTimeMillis()
                                    )
                                )
                                navController.navigate(
                                    "${Routes.RESULT}?bankId=$histBankId&mode=$mode&source=$source&planId=$planId" +
                                            "&correct=$correctCount&wrong=$wrongCount&total=${answeredThisSession.size}&duration=$elapsed"
                                ) { popUpTo(Routes.QUIZ) { inclusive = true } }
                            } else {
                                index++
                                submitted = false
                                questionElapsed = 0
                                selected = initialAnswer(queue[index].q)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (index >= queue.lastIndex) "查看结果" else "下一题") }
                }
            }
        }
    }

    // 跳题浮层
    if (showJump) {
        ModalBottomSheet(onDismissRequest = { showJump = false }) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("跳转到题目", fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 12.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(queue.indices.toList()) { i ->
                        val answered = answeredThisSession.contains(queue[i].q.id)
                        val isCurrent = i == index
                        Surface(
                            onClick = {
                                index = i
                                submitted = isBrowse
                                selected = initialAnswer(queue[i].q)
                                showJump = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = when {
                                isCurrent -> MaterialTheme.colorScheme.primary
                                answered -> MaterialTheme.colorScheme.tertiaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ) {
                            Column(Modifier.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${i + 1}", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                    color = if (isCurrent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                                Text(shortTypeLabel(queue[i].q.type), fontSize = 9.sp,
                                    color = if (isCurrent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // 退出确认弹窗（与小程序一致：中途退出前提示进度已保存）
    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text("退出刷题") },
            text = { Text("已答题目的进度已自动保存，确认退出？") },
            confirmButton = {
                TextButton(onClick = {
                    showExitConfirm = false
                    saveHistoryAndExit()
                }) { Text("退出") }
            },
            dismissButton = { TextButton(onClick = { showExitConfirm = false }) { Text("继续刷题") } }
        )
    }
}

// ─── 内部类型与辅助 ─────────────────────────────────────────────

private data class QuizItem(
    val bankId: String,
    val q: Question
)

private fun initialAnswer(q: Question): Any? = when (q.type) {
    QuestionType.single, QuestionType.multiple -> emptySet<Int>()
    QuestionType.judge -> null
    else -> ""
}

// 题型名称（完整/短标签）
private fun typeLabel(type: QuestionType): String = when (type) {
    QuestionType.single -> "单选题"
    QuestionType.multiple -> "多选题"
    QuestionType.judge -> "判断题"
    QuestionType.fill -> "填空题"
    QuestionType.essay -> "简答题"
}

private fun shortTypeLabel(type: QuestionType): String = when (type) {
    QuestionType.single -> "单选"
    QuestionType.multiple -> "多选"
    QuestionType.judge -> "判断"
    QuestionType.fill -> "填空"
    QuestionType.essay -> "简答"
}

private fun formatAnswer(q: Question): String = when (q.type) {
    QuestionType.single, QuestionType.multiple -> {
        val idx = Scoring.parseAnswerIndices(q.answer, q.options).sorted()
        idx.joinToString("") { ('A' + it).toString() } + " " +
                idx.joinToString("、") { q.options?.get(it) ?: "" }
    }
    QuestionType.fill -> {
        val parts = (q.answer ?: "").split(Regex("[|｜]"))
            .map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size > 1) parts.mapIndexed { i, a -> "第${i + 1}空：$a" }.joinToString("　")
        else parts.firstOrNull() ?: ""
    }
    else -> q.answer ?: ""
}

private fun resolveBankName(
    repo: AppRepository,
    source: String,
    bankId: String?,
    planId: String?,
    isBrowse: Boolean
): String = when (source) {
    "bank", "continue" -> bankId?.let { repo.getBankById(it)?.name } ?: (if (isBrowse) "浏览" else "")
    "wrong" -> "错题重做"
    "favorite" -> "收藏重做"
    "all" -> "全部题库"
    "plan" -> repo.getStudyPlans().find { it.id == planId }?.bankName ?: "学习计划"
    else -> if (isBrowse) "浏览" else ""
}

private fun buildQueue(
    repo: AppRepository,
    bankId: String?,
    mode: String,
    source: String,
    planId: String?,
    filterUnanswered: Boolean,
    specifiedQuestions: List<Pair<String, com.example.shuati.model.Question>> = emptyList()
): List<QuizItem> {
    val effectiveMode = if (source == "all" || source == "browse") "sequence" else mode
    // 优先使用指定的题目列表（来自 questionIds 参数）
    if (specifiedQuestions.isNotEmpty()) {
        val ordered = when (effectiveMode) {
            "random" -> specifiedQuestions.shuffled()
            else -> specifiedQuestions
        }
        return ordered.map { (bid, q) -> QuizItem(bid, q) }
    }
    return when (source) {
        "wrong" -> repo.getWrongRecords().mapNotNull { rec ->
            rec.question?.let { QuizItem(rec.bankId, it) }
        }
        "favorite" -> repo.getFavoriteRecords().mapNotNull { rec ->
            rec.question?.let { QuizItem(rec.bankId, it) }
        }
        "all" -> {
            // 全部题库：跨题库聚合；只刷未做题时按 __all__ 进度（namespaced）过滤
            val banks = repo.getBanks()
            // 跳过不完整题目（选项/答案缺失），与小程序一致
            val all = banks.flatMap { b ->
                b.questions.filter { !it.incomplete }.map { QuizItem(b.id, it) }
            }
            val base = if (filterUnanswered) {
                val doneSet = repo.getProgress("__all__")?.answeredIds?.toSet() ?: emptySet()
                all.filter { "${it.bankId}::${it.q.id}" !in doneSet }
            } else all
            if (effectiveMode == "random") base.shuffled() else base
        }
        "plan" -> {
            val plan = planId?.let { repo.getStudyPlans().find { p -> p.id == it } }
            val bank = plan?.bankId?.let { repo.getBankById(it) }
            val pp = planId?.let { repo.getPlanProgress(it) }
            val ids = pp?.sessionQuestionIds ?: emptyList()
            ids.mapNotNull { id -> bank?.questions?.find { q -> q.id == id }?.let { QuizItem(bank.id, it) } }
        }
        else -> {
            // 首页「浏览模式」入口使用 bankId=__all__，聚合全部题库（保留真实题库 id 便于进度/错题归属）
            val isAll = bankId == "__all__"
            val bank = if (isAll) null else bankId?.let { repo.getBankById(it) }
            var base = when {
                bank != null -> bank.questions.filter { !it.incomplete }.map { QuizItem(bank.id, it) }
                isAll -> repo.getBanks().flatMap { b ->
                    b.questions.filter { !it.incomplete }.map { QuizItem(b.id, it) }
                }
                else -> emptyList()
            }
            if (filterUnanswered && base.isNotEmpty()) {
                val answered = if (isAll)
                    (repo.getProgress("__all__")?.answeredIds?.toSet() ?: emptySet())
                else
                    (repo.getProgress(bank!!.id)?.answeredIds ?: emptyList())
                base = if (isAll) base.filter { "${it.bankId}::${it.q.id}" !in answered }
                else base.filter { it.q.id !in answered }
            }
            // 浏览模式且题库允许乱序
            val ordered = when {
                effectiveMode == "random" -> base.shuffled()
                source == "browse" && !isAll && bank?.questions?.any { it.orderFree } == true -> base.shuffled()
                else -> base
            }
            ordered
        }
    }
}
