package com.example.shuati.ui.pages


import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.navigation.NavController
import com.example.shuati.data.AppRepository
import com.example.shuati.logic.Scoring
import com.example.shuati.model.Question
import com.example.shuati.model.QuestionBank
import com.example.shuati.model.QuestionType
import com.example.shuati.model.AiProviderConfig
import com.example.shuati.ui.*
import com.example.shuati.ui.components.Badge
import com.example.shuati.ui.components.HeroCard
import com.example.shuati.ui.components.ScreenTopBar
import com.example.shuati.ui.components.SectionTitle
import com.example.shuati.ui.components.TagChip
import com.example.shuati.ui.components.CardBox
import com.example.shuati.ui.components.ShuatiSwitch
import com.example.shuati.ui.components.ThinProgress
import kotlinx.coroutines.delay
import java.util.UUID
import com.example.shuati.ui.theme.semantic
import com.example.shuati.ui.theme.ShuatiMotion
import com.example.shuati.data.AiConfigStore
import com.example.shuati.logic.AiExplainService
import com.example.shuati.model.AiExplanation
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import com.example.shuati.logic.AiExplainService.AiStreamEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val context = LocalContext.current

    // AI 解析状态
    val coroutineScope = rememberCoroutineScope()
    var aiMap by remember { mutableStateOf(mapOf<String, AiExplanation>()) }
    var aiBusy by remember { mutableStateOf(false) }
    var aiError by remember { mutableStateOf<String?>(null) }
    var showAiHintDialog by remember { mutableStateOf(false) }
    var aiHintDraft by remember { mutableStateOf("") }
    // 流式进度状态
    var aiThinking by remember { mutableStateOf<String?>(null) }
    var aiFlow by remember { mutableStateOf("") }
    var aiJob by remember { mutableStateOf<Job?>(null) }
    // 代际计数器：每次发起新请求 +1，用于区分「当前任务」与「已被取消的旧任务」。
    // 连续点击重新生成时，旧任务的 finally 可能晚于新任务执行，若无守卫会把新任务的流式输出清空。
    var aiGen by remember { mutableStateOf(0) }
    var aiRegenerating by remember { mutableStateOf(false) }
    // 多供应商模型选择器状态（默认上次使用）。loadAll 每次都从加密存储读，只调一次避免重复 IO
    val aiSettings = remember { AiConfigStore.loadAll(context) }
    var aiProviders by remember { mutableStateOf(aiSettings.providers) }
    var aiSelProvider by remember { mutableStateOf(aiSettings.selectedProviderId) }

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
    // 练习设置：跳过残缺题（默认开，buildQueue 已按 incomplete 过滤，此处为显式开关）
    var skipIncomplete by remember { mutableStateOf(true) }
    // 模式选择页：当前选中的模式（卡片高亮），点「开始刷题」才真正进入
    var selectedMode by remember { mutableStateOf("sequence") }
    // 题型筛选（按题型刷题，可选；支持多选组合，空集合 = 全部题型）
    var filterTypes by remember { mutableStateOf<Set<QuestionType>>(emptySet()) }
    // 选项乱序（仅选择类题型生效：每题选项重排，判分按正确答案，不影响对错）
    var shuffleOptions by remember { mutableStateOf(false) }
    // 进度刷新版本号：清除进度后自增，触发模式选择页进度重新计算并立即刷新界面
    var progressVersion by remember { mutableStateOf(0) }

    // 计算本次作答序列（含真实 bankId）
    val queue = remember(bankId, selMode, source, planId, filterLocal, skipIncomplete, specifiedQuestions, filterTypes) {
        buildQueue(repo, bankId, selMode ?: "", source, planId, filterLocal, specifiedQuestions, skipIncomplete, filterTypes)
    }

    var index by remember { mutableStateOf(0) }
    var resumedOnce by remember { mutableStateOf(false) }
    var showJump by remember { mutableStateOf(false) }

    // 切题时清理上一次可能残留的流式/思考状态，避免串到新题
    LaunchedEffect(index) {
        aiJob?.cancel()
        aiThinking = null
        aiFlow = ""
        aiError = null
        aiRegenerating = false
        aiBusy = false
    }

    // 记录打开时间，供首页「最近题库」排序（__all__ 不是真实题库，不记录）
    LaunchedEffect(bankId) {
        if (bankId != null && bankId != "__all__") repo.markBankOpened(bankId)
    }

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
                // 普通浏览模式：恢复上次浏览断点（对齐小程序 getBrowseProgress；取消勾选则从头开始）
                isBrowse -> {
                    val bid = if (bankId == "__all__") "__all__" else bankId
                    val bi = if (filterLocal) (bid?.let { repo.getBrowseProgress(it) } ?: 0) else 0
                    index = bi.coerceAtMost(queue.lastIndex.coerceAtLeast(0))
                }
            }
        }
    }

    // 浏览模式：保存浏览断点（对齐小程序 saveBrowseProgress / 计划浏览 setPlanBrowseIndex）
    LaunchedEffect(index, isBrowse) {
        if (isBrowse && resumedOnce && queue.isNotEmpty()) {
            if (planId != null) {
                repo.setPlanBrowseIndex(planId, index)
            } else {
                val bid = if (bankId == "__all__") "__all__" else bankId
                if (bid != null) repo.saveBrowseProgress(bid, index)
            }
        }
    }

    // 题库名（模式选择页与答题页共用）
    val bankName = resolveBankName(repo, source, bankId, planId, selMode == "browse")

    // 模式选择页
    if (selMode == null) {
        val bankTotal = queue.size
        // 进度刷新版本号：清除进度后 ++ 触发 remember 重算，立即反映到界面（否则需退出重进才刷新）
        val doneCount = remember(bankId, source, progressVersion) {
            when {
                bankId == "__all__" || source == "all" -> repo.getProgress("__all__")?.answeredIds?.size ?: 0
                bankId != null -> repo.getProgress(bankId)?.answeredIds?.size ?: 0
                else -> 0
            }
        }
        val hasProgress = remember(bankId, progressVersion) {
            doneCount > 0 || (bankId != null && (repo.getBrowseProgress(bankId) ?: 0) > 0)
        }
        val browseIdx = remember(bankId, progressVersion) { bankId?.let { repo.getBrowseProgress(it) } ?: 0 }
        val pct = if (bankTotal > 0) (doneCount * 100 / bankTotal) else 0
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.navigationBars,
            topBar = { ScreenTopBar("选择刷题模式", onBack = { navController.popBackStack() }) }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 题库概要卡
                HeroCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Layers, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(bankName.ifEmpty { "题库" }, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.height(3.dp))
                            Text("共 $bankTotal 题 · 已答 $doneCount 题 · 剩余 ${(bankTotal - doneCount).coerceAtLeast(0)} 题",
                                fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("$pct%", fontSize = 19.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp,
                                color = MaterialTheme.colorScheme.onSurface)
                            Text("进度", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    ThinProgress(progress = if (bankTotal > 0) doneCount.toFloat() / bankTotal else 0f)
                }

                SectionTitle("选择模式")

                // 模式卡片（选中高亮，点「开始刷题」确认）
                data class ModeSpec(val m: String, val label: String, val desc: String, val isSeq: Boolean)
                listOf(
                    ModeSpec("sequence", "顺序刷题", "按题库原始顺序作答", doneCount > 0),
                    ModeSpec("random", "随机刷题", "打乱顺序，适合查漏补缺", false),
                    ModeSpec("browse", "浏览模式", "直接看答案与解析，不计分", false)
                ).forEach { (m, label, desc, isSeq) ->
                    val selected = selectedMode == m
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (selected) 2.dp else 0.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable { selectedMode = m },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = if (selected) 6.dp else 2.dp
                        )
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceContainer
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    when (m) {
                                        "sequence" -> Icons.AutoMirrored.Filled.Sort
                                        "random" -> Icons.Filled.Shuffle
                                        else -> Icons.Filled.Book
                                    },
                                    null,
                                    tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(label, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                    if (isSeq && !selected) {
                                        Spacer(Modifier.width(6.dp))
                                        Badge("推荐", color = MaterialTheme.colorScheme.primaryContainer,
                                            textColor = MaterialTheme.colorScheme.onPrimaryContainer)
                                    }
                                }
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    when {
                                        m == "sequence" && doneCount > 0 -> "从第 ${doneCount + 1} 题继续"
                                        m == "browse" && browseIdx > 0 -> "上次浏览到第 $browseIdx 题"
                                        else -> desc
                                    },
                                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (m == "browse" && browseIdx > 0) {
                                    Text("上次浏览到第 $browseIdx 题", fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            if (selected) {
                                Text("已选", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                // 题型筛选（可选）：支持多选组合；与「随机」组合即「同题型随机顺序」
                SectionTitle("题型筛选（可选，可多选）")
                val typeFilterChips = listOf(
                    QuestionType.single to "单选",
                    QuestionType.multiple to "多选",
                    QuestionType.judge to "判断",
                    QuestionType.fill to "填空",
                    QuestionType.essay to "简答"
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 「全部」= 清空题型选择
                    FilterChip(
                        selected = filterTypes.isEmpty(),
                        onClick = { filterTypes = emptySet() },
                        label = { Text("全部") }
                    )
                    typeFilterChips.forEach { (t, label) ->
                        FilterChip(
                            selected = t in filterTypes,
                            onClick = {
                                filterTypes = if (t in filterTypes) filterTypes - t else filterTypes + t
                            },
                            label = { Text(label) }
                        )
                    }
                }

                SectionTitle("练习设置")

                // 跳过残缺题目
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .toggleable(value = skipIncomplete, onValueChange = { skipIncomplete = it })
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("跳过残缺题目", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text("自动略过缺少选项或答案的题", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    ShuatiSwitch(checked = skipIncomplete, onCheckedChange = { skipIncomplete = it })
                }
                // 只刷未做过的题
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .toggleable(value = filterLocal, onValueChange = { filterLocal = it })
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("只刷未做过的题", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text(
                            if (doneCount > 0) "本次共 ${(bankTotal - doneCount).coerceAtLeast(0)} 题"
                            else "本次共 $bankTotal 题",
                            fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    ShuatiSwitch(checked = filterLocal, onCheckedChange = { filterLocal = it })
                }
                // 选项乱序（仅选择类题型：每题选项重排显示，判分按正确答案，不影响对错）
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .toggleable(value = shuffleOptions, onValueChange = { shuffleOptions = it })
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("选项乱序", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text("打乱每题选项顺序，检验是否真掌握", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    ShuatiSwitch(checked = shuffleOptions, onCheckedChange = { shuffleOptions = it })
                }

                Spacer(Modifier.height(4.dp))
            }

            // 底部操作栏：开始刷题
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = {
                            when {
                                bankId == "__all__" || source == "all" -> repo.clearProgress("__all__")
                                bankId != null -> repo.clearProgress(bankId)
                            }
                            // 立即刷新进度显示（doneCount / hasProgress / 断点续刷按钮）
                            progressVersion++
                        },
                        enabled = hasProgress
                    ) {
                        Text("清除进度", color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = { selMode = selectedMode },
                        modifier = Modifier.width(180.dp)
                    ) { Text("开始刷题") }
                }
            }
        }
        }
        return
    }

    if (queue.isEmpty()) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = { ScreenTopBar("答题", onBack = { navController.popBackStack() }) }) {
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

    /**
     * 发起一次 AI 解析请求（首生成与重新生成共用）。
     * onAiClick 直接调用本函数；onRegenerate 仍走弹窗、由弹窗「生成」按钮调用本函数。
     */
    fun requestAi(hint: String) {
        val q = current.q
        val bid = current.bankId
        aiJob?.cancel()              // 取消上一次未完成的请求
        val gen = ++aiGen            // 代际：本次请求唯一标识，旧任务 finally 不再能清空本次输出
        aiBusy = true
        aiError = null
        aiFlow = ""
        // 是否「重新生成」：已有解析则保留旧结果并展示重生成动画
        val existing = aiMap[q.id] ?: q.aiExplanation
        aiRegenerating = existing != null
        // 初始进度文案：与 AiExplainService 的新文案风格统一（改动 3）。
        // 首生成走「正在生成解析…」；重新生成保持「正在重新生成解析…」（重新生成期间不随 Thinking 事件刷新）。
        aiThinking = if (aiRegenerating) "正在重新生成解析…" else "正在生成解析…"
        aiJob = coroutineScope.launch {
            try {
                val cfg = AiConfigStore.activeConfig(context)
                val reusePrev = AiConfigStore.loadAll(context).reusePrevOnRegen
                val bankHint = repo.getBanks().firstOrNull { it.id == bid }?.aiHint ?: ""
                val eff = hint.ifBlank { bankHint }
                // 仅当用户开启「重生成携带上次解析」开关时才注入上次答案（否则走全新生成，含重新检索）。
                val prevAnswer = if (reusePrev) existing?.text else null
                AiExplainService.explainStream(cfg, q, eff, prevAnswer = prevAnswer) { ev ->
                    when (ev) {
                        is AiStreamEvent.Thinking -> if (!aiRegenerating) aiThinking = ev.msg
                        is AiStreamEvent.Generating -> {
                            aiThinking = if (aiRegenerating) "正在重新生成解析…" else "正在生成解析…"
                            aiFlow = ""
                        }
                        // Delta 传的是「截至当前的全量快照」，整体替换而非 append，呈现逐字累积的打字机效果
                        is AiStreamEvent.Delta -> aiFlow = ev.text
                        is AiStreamEvent.Done -> {
                            if (gen == aiGen) {
                                aiRegenerating = false
                                repo.saveAiExplanation(bid, q.id, ev.ai)
                                aiMap = aiMap + (q.id to ev.ai)
                                aiFlow = ""
                            }
                        }
                        is AiStreamEvent.Error -> {
                            if (gen == aiGen) {
                                aiError = ev.msg
                                aiFlow = ""
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (gen == aiGen) aiError = e.message ?: "生成失败"
            } finally {
                // 仅当本任务仍是「最新一次请求」时才清理状态；
                // 否则是已被取消的旧任务，其 finally 不能清空新任务正在展示的流式输出。
                if (gen == aiGen) {
                    aiThinking = null
                    aiBusy = false
                    aiRegenerating = false
                    // 流式结束：切换为已保存结果（aiMap 中的完整文本），清理临时流式残留
                    aiFlow = ""
                }
            }
        }
    }

    // 会话结果收集（结果页展示用）：进入时重置，记录同题库上次正确率与已有错题
    val sessionInitialized = remember { mutableStateOf(false) }
    if (!sessionInitialized.value) {
        sessionInitialized.value = true
        QuizSessionResult.reset()
        val histBankId0 = if (source == "all" || bankId == "__all__") "__all__" else bankId
        QuizSessionResult.prevAccuracy = repo.getHistoryRecords()
            .filter { it.bankId == histBankId0 && !(mode == "browse") }
            .maxByOrNull { it.finishedAt }?.accuracy
        QuizSessionResult.newWrongCount = 0
    }
    var existingWrongKeys = remember { repo.getWrongRecords().map { "${it.bankId}::${it.questionId}" }.toSet() }

    // 状态
    var selected by remember(current.q.id) { mutableStateOf(initialAnswer(current.q)) }
    var submitted by remember(current.q.id) { mutableStateOf(isBrowse) } // 浏览模式默认“已展示”
    var autoNext by remember { mutableStateOf(true) }
    var elapsed by remember { mutableStateOf(0) }
    var questionElapsed by remember { mutableStateOf(0) }
    var correctCount by remember { mutableStateOf(0) }
    var wrongCount by remember { mutableStateOf(0) }
    val answeredThisSession = remember { mutableStateListOf<String>() }
    // 每题对错记录（上一题回退时撤销计数用）
    val answeredMap = remember { mutableStateMapOf<String, Boolean>() }
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

    // 自动下一题：提交判分后，若开启 autoNext，延时自动跳到下一题
    // （最后一题不自动跳，留给用户点「查看结果」；手动上一题/下一题或关开关会取消待执行的自动跳）
    LaunchedEffect(submitted, autoNext, index) {
        if (submitted && autoNext && !isBrowse && index < queue.lastIndex) {
            delay(1200)
            if (index < queue.lastIndex) {
                index++
                submitted = false
                questionElapsed = 0
                selected = initialAnswer(queue[index].q)
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
        val fromWrongs = source == "wrong"
        // 每题正确率统计（结果页/答题页展示用）
        repo.recordQuestionResult(current.bankId, q.id, correct)
        if (correct && fromWrongs) {
            // 错题练习：连对计数，达标自动移出错题本（对齐小程序 incrementStreak/removeWrongRecord）
            val streak = repo.incrementStreak(current.bankId, q.id)
            val target = repo.getMasteryTarget()
            if (streak >= target) {
                repo.removeWrongRecord(current.bankId, q.id)
                Toast.makeText(context, "已连对${target}次，移出错题本", Toast.LENGTH_SHORT).show()
            }
        } else if (!correct) {
            // 答错：所有模式（含错题/收藏练习）均记入错题本并累加答错次数
            repo.addWrongRecord(
                com.example.shuati.model.WrongRecord(
                    bankId = current.bankId, bankName = bankName,
                    questionId = q.id, question = q,
                    recordedAt = System.currentTimeMillis(), createdAt = System.currentTimeMillis()
                )
            )
            // 错题练习：答错清零连对次数（对齐小程序 resetStreak）
            if (fromWrongs) repo.resetStreak(current.bankId, q.id)
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
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = {
            ScreenTopBar(
                bankName.ifEmpty { if (isBrowse) "浏览" else "答题" },
                onBack = { saveHistoryAndExit() },
                actions = {
                    // 跳题浮层
                    SemanticIconButton(
                        icon = Icons.AutoMirrored.Filled.ListAlt,
                        contentDescription = "跳题",
                        tooltip = "跳转到指定题号",
                        onClick = { showJump = true }
                    )
                    // 只刷未做题开关（点击重建队列）
                    SemanticIconButton(
                        icon = if (filterLocal) Icons.Filled.FilterAlt else Icons.Filled.FilterAltOff,
                        contentDescription = if (filterLocal) "只刷未做题：开" else "只刷未做题：关",
                        tooltip = "只刷未做题（已做过的题不再出现）",
                        tint = if (filterLocal) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        onClick = { toggleFilter() }
                    )
                    if (!isBrowse) {
                        SemanticIconButton(
                            icon = if (autoNext) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                            contentDescription = if (autoNext) "自动下一题：开" else "自动下一题：关",
                            tooltip = "自动下一题（提交后自动跳到下一题）",
                            onClick = { autoNext = !autoNext }
                        )
                    }
                    SemanticIconButton(
                        icon = if (favState) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = if (favState) "已收藏" else "收藏",
                        tooltip = if (favState) "取消收藏本题" else "收藏本题",
                        tint = if (favState) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        onClick = {
                            favState = repo.toggleFavorite(
                                com.example.shuati.model.FavoriteRecord(
                                    bankId = current.bankId, bankName = bankName, questionId = current.q.id,
                                    question = current.q, createdAt = System.currentTimeMillis()
                                )
                            )
                        }
                    )
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
                        val opts = current.q.options ?: emptyList()
                        // 选项乱序：每题固定一个重排映射（按题 id 记忆，切题重建），判分按原始索引
                        val displayOrder = remember(current.q.id, shuffleOptions) {
                            if (shuffleOptions && opts.size > 1) opts.indices.shuffled() else opts.indices.toList()
                        }
                        displayOrder.forEachIndexed { pos, origIdx ->
                            val opt = opts.getOrNull(origIdx) ?: return@forEachIndexed
                            val letter = ('A' + pos).toString()
                            val isSel = chosen.contains(origIdx)
                            val isCorrectOption = Scoring.parseAnswerIndices(current.q.answer, current.q.options).contains(origIdx)
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
                                    selected = if (current.q.type == QuestionType.single) setOf(origIdx)
                                    // 多选题：再次点击同一选项应取消选择（切换），而非只做「加入」
                                    else if (chosen.contains(origIdx)) (chosen - origIdx).toSet()
                                    else (chosen + origIdx).toSet()
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
                                    slotWrong -> { { Text("再看一眼", color = MaterialTheme.colorScheme.error) } }
                                    slotCorrect -> { { Text("答对", color = MaterialTheme.colorScheme.tertiary) } }
                                    else -> null
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (fillSlotN > 1 && !submitted) {
                            fun toggleOrderFree(next: Boolean) {
                                fillOrderFree = next
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
                                ShuatiSwitch(
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
                    AnswerPanel(
                        isBrowse = isBrowse,
                        q = current.q,
                        correct = judgeAnswer(current.q, selected),
                        fillSlotN = fillSlotN,
                        fillOrderFree = fillOrderFree,
                        qStat = repo.getQuestionStats()["${current.bankId}::${current.q.id}"],
                        aiExplanation = aiMap[current.q.id] ?: current.q.aiExplanation,
                        aiBusy = aiBusy,
                        aiError = aiError,
                        aiThinking = aiThinking,
                        aiStreamingText = if (aiFlow.isNotEmpty()) aiFlow else null,
                        aiRegenerating = aiRegenerating,
                        providers = aiProviders,
                        selectedProviderId = aiSelProvider,
                        selectedModel = aiProviders.firstOrNull { it.id == aiSelProvider }?.selectedModel
                            ?: aiProviders.firstOrNull()?.selectedModel ?: "",
                        appSearchEnabled = AiConfigStore.searchEnabled(AiConfigStore.activeConfig(context)),
                        onModelPicked = { pid, m ->
                            aiProviders = aiProviders.map { if (it.id == pid) it.copy(selectedModel = m) else it }
                            aiSelProvider = pid
                            AiConfigStore.saveProviders(context, aiProviders, pid)
                        },
                        onAiClick = {
                            aiHintDraft = repo.getBanks().firstOrNull { it.id == current.bankId }?.aiHint ?: ""
                            requestAi(aiHintDraft)
                        },
                        onRegenerate = {
                            aiHintDraft = repo.getBanks().firstOrNull { it.id == current.bankId }?.aiHint ?: ""
                            showAiHintDialog = true
                        }
                    )
                }
            }

            if (showAiHintDialog) {
                AlertDialog(
                    onDismissRequest = { showAiHintDialog = false },
                    title = { Text("AI 解析") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "可补充定向提示，如「这是注册会计师考试 / 医学类」，让解析更贴合考点。留空则使用题库默认提示。",
                                fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = aiHintDraft, onValueChange = { aiHintDraft = it },
                                label = { Text("定向提示（可选）") },
                                singleLine = true, modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = { TextButton(onClick = { showAiHintDialog = false; requestAi(aiHintDraft) }) { Text("生成") } },
                    dismissButton = { TextButton(onClick = { showAiHintDialog = false }) { Text("取消") } }
                )
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
                        answeredMap[current.q.id] = correct
                        // 收集分题型统计
                        val tName = typeLabel(current.q.type)
                        val tIdx = QuizSessionResult.typeStats.indexOfFirst { it.type == tName }
                        QuizSessionResult.typeStats = if (tIdx >= 0) {
                            QuizSessionResult.typeStats.mapIndexed { i, s ->
                                if (i == tIdx) s.copy(correct = s.correct + if (correct) 1 else 0, total = s.total + 1) else s
                            }
                        } else {
                            QuizSessionResult.typeStats + TypeStat(
                                tName, if (correct) 1 else 0, 1
                            )
                        }
                        // 收集错题明细 + 新错题计数
                        if (!correct) {
                            val userAnsText = formatUserAnswer(current.q, selected, fillInputs)
                            repo.recordWrongAnswer(current.bankId, current.q.id, userAnsText)
                            if ("${current.bankId}::${current.q.id}" !in existingWrongKeys) {
                                QuizSessionResult.newWrongCount++
                                existingWrongKeys = existingWrongKeys + "${current.bankId}::${current.q.id}"
                            }
                            QuizSessionResult.wrongDetails = QuizSessionResult.wrongDetails + WrongDetail(
                                bankName = bankName,
                                question = current.q.question,
                                userAnswer = formatUserAnswer(current.q, selected, fillInputs),
                                correctAnswer = formatAnswer(current.q),
                                explanation = current.q.explanation.orEmpty()
                            )
                        }
                    }, modifier = Modifier.weight(2f)) { Text("提交") }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        // 上一题：回退到前一题重答（撤销该题计数）
                        if (index > 0) {
                            OutlinedButton(
                                onClick = {
                                    val prevId = queue[index - 1].q.id
                                    answeredMap.remove(prevId)?.let { if (it) correctCount-- else wrongCount-- }
                                    answeredThisSession.remove(prevId)
                                    index--
                                    submitted = false
                                    questionElapsed = 0
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("上一题") }
                        }
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
                            modifier = Modifier.weight(2f)
                        ) { Text(if (index >= queue.lastIndex) "查看结果" else "下一题") }
                    }
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


/** 答案面板：浏览模式展示答案+解析；判分模式展示判定结果+解析；底部附 AI 解析 */
@Composable
private fun AnswerPanel(
    isBrowse: Boolean,
    q: Question,
    correct: Boolean,
    fillSlotN: Int,
    fillOrderFree: Boolean,
    qStat: String? = null,
    aiExplanation: AiExplanation? = null,
    aiBusy: Boolean = false,
    aiError: String? = null,
    aiThinking: String? = null,
    aiStreamingText: String? = null,
    aiRegenerating: Boolean = false,
    // 多供应商模型选择器
    providers: List<AiProviderConfig> = emptyList(),
    selectedProviderId: String = "",
    selectedModel: String = "",
    appSearchEnabled: Boolean = false,
    onModelPicked: (providerId: String, model: String) -> Unit = { _, _ -> },
    onAiClick: () -> Unit = {},
    onRegenerate: () -> Unit = {}
) {
    CardBox {
        if (isBrowse) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.semantic.success)
                Spacer(Modifier.width(8.dp))
                Text("正确答案", fontWeight = FontWeight.SemiBold, color = MaterialTheme.semantic.success)
            }
            Spacer(Modifier.height(8.dp))
            Text(formatAnswer(q), fontWeight = FontWeight.Medium, fontSize = 15.sp)
            val browseOpts = q.options
            if (!browseOpts.isNullOrEmpty()) {
                val correctRaw = q.answer?.trim().orEmpty()
                val correctSet = correctRaw.split(Regex("[,，|｜、\\s]+")).map { it.trim().uppercase() }
                    .filter { it.isNotEmpty() }.toMutableSet()
                if (correctSet.size == 1 && correctSet.first().length > 1) {
                    correctSet.clear()
                    correctSet.addAll(correctRaw.filter { it.isLetter() }.uppercase().map { it.toString() })
                }
                Spacer(Modifier.height(10.dp))
                browseOpts.forEachIndexed { idx, opt ->
                    val letter = ('A'.code + idx).toChar().toString()
                    val isAnswer = correctSet.contains(letter)
                    Surface(
                        color = if (isAnswer) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = if (isAnswer) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline
                            ) {
                                Text(letter, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                    color = if (isAnswer) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(opt, fontSize = 14.sp,
                                fontWeight = if (isAnswer) FontWeight.Bold else FontWeight.Normal,
                                color = if (isAnswer) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (idx < browseOpts.lastIndex) Spacer(Modifier.height(6.dp))
                }
            }
            if (!q.explanation.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text("解析：${q.explanation}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (correct) Icons.Filled.CheckCircle else Icons.Filled.Cancel, null,
                    tint = if (correct) MaterialTheme.semantic.success else MaterialTheme.semantic.error
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (correct) "答对了" else "再看一眼",
                    fontWeight = FontWeight.SemiBold,
                    color = if (correct) MaterialTheme.semantic.success else MaterialTheme.semantic.error
                )
            }
            Spacer(Modifier.height(8.dp))
            Text("正确答案：${formatAnswer(q)}", fontWeight = FontWeight.Medium, fontSize = 14.sp)
            // 你的答案 + 该题正确率
            Spacer(Modifier.height(10.dp))
            Row {
                Column(Modifier.weight(1f)) {
                    Text("你的答案", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        if (correct) formatAnswer(q) else "（未答对）",
                        fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold,
                        color = if (correct) MaterialTheme.semantic.success else MaterialTheme.semantic.error
                    )
                }
                val statArr = qStat?.split(",")
                if (statArr != null && statArr.size == 2) {
                    val c = statArr[0].toIntOrNull() ?: 0
                    val t = statArr[1].toIntOrNull() ?: 0
                    if (t > 0) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text("该题正确率", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "${c * 100 / t}%",
                                fontSize = 13.5.sp, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.semantic.info
                            )
                        }
                    }
                }
            }
            if (q.type == QuestionType.fill && fillSlotN > 1) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "（${if (fillOrderFree) "已开启不限顺序" else "按顺序匹配"}）",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!q.explanation.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text("解析：${q.explanation}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)
            }
        }

        // ── 模型选择器（切换供应商/模型，默认上次使用） ──
        if (providers.isNotEmpty()) {
            val entries = providers.flatMap { p -> p.models.map { m -> p to m } }
            if (entries.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                var modelExpanded by remember { mutableStateOf(false) }
                val curLabel = run {
                    val p = providers.firstOrNull { it.id == selectedProviderId }
                    val m = selectedModel.ifBlank { p?.selectedModel ?: "" }
                    if (p != null && m.isNotBlank()) "${p.name} · $m" else "选择解析模型"
                }
                ExposedDropdownMenuBox(
                    expanded = modelExpanded, onExpandedChange = { modelExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = curLabel, onValueChange = {}, readOnly = true,
                        label = { Text("解析模型") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                        enabled = !aiBusy
                    )
                    ExposedDropdownMenu(expanded = modelExpanded, onDismissRequest = { modelExpanded = false }) {
                        entries.forEach { (p, m) ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("${p.name} · $m", fontSize = 13.sp)
                                        Spacer(Modifier.width(6.dp))
                                        if (appSearchEnabled || AiExplainService.modelHasBuiltinSearch(m)) {
                                            AssistChip(
                                                onClick = { }, label = { Text("联网", fontSize = 10.sp) },
                                                colors = AssistChipDefaults.assistChipColors(
                                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                                    labelColor = MaterialTheme.colorScheme.onTertiaryContainer
                                                ),
                                                modifier = Modifier.height(22.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = { onModelPicked(p.id, m); modelExpanded = false }
                            )
                        }
                    }
                }
                if (selectedModel.isNotBlank() && (appSearchEnabled || AiExplainService.modelHasBuiltinSearch(selectedModel))) {
                    Spacer(Modifier.height(4.dp))
                    Text("✓ 该模型具备联网搜索能力", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.tertiary)
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Text("尚未拉取到模型：请在「AI 大模型配置」中添加供应商并拉取模型", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // ── AI 解析（大模型生成，区别于原题 explanation，默认已落盘保存） ──
        val ai = aiExplanation
        when {
            // 出错：展示错误 + 重试
            aiError != null -> {
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("AI 解析失败", fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(aiError, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 19.sp)
                        Spacer(Modifier.height(6.dp))
                        TextButton(onClick = onRegenerate, modifier = Modifier.align(Alignment.End)) {
                            Text("重试", fontSize = 12.sp)
                        }
                    }
                }
            }
            // 流式逐字生成中（首生成与重新生成共用实时卡）
            aiStreamingText != null -> {
                val answerText = aiStreamingText
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (aiRegenerating) "AI 解析 · 重新生成中" else "AI 解析 · 生成中", fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = MaterialTheme.colorScheme.primary)
                        }
                        if (answerText.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            // 逐字符流式渲染：快照整体替换，光标体现解析进度
                            Text(answerText + "▍", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, lineHeight = 20.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            // 重新生成：保留旧结果（半透明）并展示线性进度条，与首次生成一致的流式加载反馈
            aiRegenerating && ai != null -> {
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("正在重新生成解析…", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.weight(1f))
                            Text(ai.model, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(8.dp))
                        // 旧结果半透明展示，明确告知用户正在替换
                        Text(ai.text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), lineHeight = 20.sp)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            // 首生成：准备进度可视化（检索 / 等待首 token）
            aiThinking != null -> {
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(aiThinking, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            // 已保存结果
            ai != null -> {
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("AI 解析", fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.weight(1f))
                            Text(ai.model, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(ai.text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, lineHeight = 20.sp)
                        if (ai.citations.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text("来源：", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            ai.citations.take(3).forEach { url ->
                                Text("• $url", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, maxLines = 2)
                            }
                        }
                        if (!ai.hintUsed.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text("提示：${ai.hintUsed}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(4.dp))
                        TextButton(onClick = onRegenerate, enabled = !aiBusy, modifier = Modifier.align(Alignment.End)) {
                            if (aiBusy) {
                                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(6.dp))
                            }
                            Text("重新生成", fontSize = 12.sp)
                        }
                    }
                }
            }
            // 未生成：入口按钮
            else -> {
                Spacer(Modifier.height(8.dp))
                Button(onClick = onAiClick, enabled = !aiBusy, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.AutoAwesome, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("AI 解析")
                }
            }
        }
    }
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

/** 格式化用户答案（结果页错题明细展示用） */
private fun formatUserAnswer(
    q: Question,
    selected: Any?,
    fillInputs: List<String>
): String = when (q.type) {
    QuestionType.single, QuestionType.multiple -> {
        val chosen = (selected as? Set<*>)?.filterIsInstance<Int>()?.toSet() ?: emptySet()
        if (chosen.isEmpty()) "未作答"
        else chosen.sorted().joinToString("") { ('A' + it).toString() } + " " +
                chosen.sorted().joinToString("、") { q.options?.get(it) ?: "" }
    }
    QuestionType.judge -> when (selected) {
        true -> "正确"; false -> "错误"; else -> "未作答"
    }
    QuestionType.fill -> fillInputs.map { it.trim() }.joinToString("、").ifEmpty { "未作答" }
    else -> (selected as? String)?.trim().orEmpty().ifEmpty { "未作答" }
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

/**
 * 无文字图标按钮：点击执行动作，长按弹出语义说明（Material3 Tooltip，长按即显示）。
 * 用于顶部栏等只显示图标的操作，让用户无需猜测按钮含义。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SemanticIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    enabled: Boolean = true,
    tooltip: String = contentDescription
) {
    val tooltipState = rememberTooltipState()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(tooltip) } },
        state = tooltipState,
        modifier = modifier
    ) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(icon, contentDescription = contentDescription, tint = tint)
        }
    }
}

private fun buildQueue(
    repo: AppRepository,
    bankId: String?,
    mode: String,
    source: String,
    planId: String?,
    filterUnanswered: Boolean,
    specifiedQuestions: List<Pair<String, com.example.shuati.model.Question>> = emptyList(),
    skipIncomplete: Boolean = true,
    filterTypes: Set<QuestionType> = emptySet()   // 空集合 = 全部题型；非空 = 仅保留这些题型（多选组合）
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
    val base = when (source) {
        "wrong" -> repo.getWrongRecords().mapNotNull { rec ->
            rec.question?.takeIf { q -> !skipIncomplete || !q.incomplete }?.let { QuizItem(rec.bankId, it) }
        }
        "favorite" -> repo.getFavoriteRecords().mapNotNull { rec ->
            rec.question?.takeIf { q -> !skipIncomplete || !q.incomplete }?.let { QuizItem(rec.bankId, it) }
        }
        "all" -> {
            // 全部题库：跨题库聚合；只刷未做题时按 __all__ 进度（namespaced）过滤
            val banks = repo.getBanks()
            banks.flatMap { b ->
                b.questions.filter { q -> !skipIncomplete || !q.incomplete }.map { QuizItem(b.id, it) }
            }
        }
        "plan" -> {
            val plan = planId?.let { repo.getStudyPlans().find { p -> p.id == it } }
            val pp = planId?.let { repo.getPlanProgress(it) }
            val ids = pp?.sessionQuestionIds ?: emptyList()
            // 会话记录统一为复合键「bankId::题目ID」
            ids.mapNotNull { entry ->
                val (bid, qid) = entry.split("::", limit = 2)
                repo.getBankById(bid)?.questions?.find { q -> q.id == qid }?.let { QuizItem(bid, it) }
            }
        }
        else -> {
            // 首页「浏览模式」入口使用 bankId=__all__，聚合全部题库（保留真实题库 id 便于进度/错题归属）
            val isAll = bankId == "__all__"
            val bank = if (isAll) null else bankId?.let { repo.getBankById(it) }
            var list = when {
                bank != null -> bank.questions.filter { q -> !skipIncomplete || !q.incomplete }.map { QuizItem(bank.id, it) }
                isAll -> repo.getBanks().flatMap { b ->
                    b.questions.filter { q -> !skipIncomplete || !q.incomplete }.map { QuizItem(b.id, it) }
                }
                else -> emptyList()
            }
            if (filterUnanswered && list.isNotEmpty()) {
                val answered = if (isAll)
                    (repo.getProgress("__all__")?.answeredIds?.toSet() ?: emptySet())
                else
                    (repo.getProgress(bank!!.id)?.answeredIds ?: emptyList())
                list = if (isAll) list.filter { "${it.bankId}::${it.q.id}" !in answered }
                else list.filter { it.q.id !in answered }
            }
            list
        }
    }
    // 题型筛选（同题型刷题）：先按题型集合过滤（多选组合：命中任一即保留），再决定顺序
    val filtered = if (filterTypes.isEmpty()) base else base.filter { it.q.type in filterTypes }
    val ordered = when {
        effectiveMode == "random" -> filtered.shuffled()
        source == "browse" && bankId != "__all__" &&
            repo.getBankById(bankId ?: "")?.questions?.any { it.orderFree } == true -> filtered.shuffled()
        else -> filtered
    }
    return ordered
}
