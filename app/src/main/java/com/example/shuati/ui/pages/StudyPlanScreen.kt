package com.example.shuati.ui.pages


import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.shuati.model.StudyPlan
import com.example.shuati.ui.*
import com.example.shuati.ui.components.CardBox
import com.example.shuati.ui.components.EmptyState
import com.example.shuati.ui.theme.semantic
import com.example.shuati.ui.components.ScreenTopBar
import com.example.shuati.ui.components.Badge
import com.example.shuati.ui.components.HeroCard
import com.example.shuati.ui.components.RingProgress
import com.example.shuati.ui.components.SoftButton
import com.example.shuati.ui.components.ThinProgress
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.ceil

private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyPlanScreen(navController: NavController) {
    val repo = repository()
    var plans by remember { mutableStateOf(emptyList<StudyPlan>()) }
    var showCreate by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<StudyPlan?>(null) }
    var showDeleteDialog by remember { mutableStateOf<StudyPlan?>(null) }

    fun reload() { plans = repo.getStudyPlans() }
    LaunchedEffect(Unit) { reload() }

    val active = plans.filter { !it.archived }
    val archived = plans.filter { it.archived }
    val invalidPlans = active.filter { repo.getBankById(it.bankId) == null }
    val validPlans = active.filter { repo.getBankById(it.bankId) != null }

    Scaffold(

        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = { ScreenTopBar("学习计划", onBack = { navController.popBackStack() }) },
        floatingActionButton = { FloatingActionButton(onClick = { showCreate = true }) { Icon(Icons.Filled.Add, null) } }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            if (invalidPlans.isNotEmpty()) {
                item {
                    CardBox {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("已失效（关联题库已被删除）", fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = {
                                invalidPlans.forEach {
                                    repo.deleteStudyPlan(it.id)
                                    repo.clearPlanProgress(it.id)
                                }
                                reload()
                            }) {
                                Text("一键清除", color = MaterialTheme.colorScheme.error)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        invalidPlans.forEach { plan ->
                            Text("${plan.title}（${plan.bankName} 已不存在）",
                                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (validPlans.isEmpty() && archived.isEmpty() && invalidPlans.isEmpty()) {
                item { EmptyState("还没有学习计划，点右下角创建一个") }
            }
            if (validPlans.isNotEmpty()) {
                item { Text("进行中", fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
                items(validPlans) { plan ->
                    PlanCard(
                        plan, repo, navController,
                        onArchive = { repo.archiveStudyPlan(plan.id); reload() },
                        onEdit = { editing = plan },
                        onDelete = { showDeleteDialog = plan }
                    )
                }
            }
            if (archived.isNotEmpty()) {
                item { Text("已归档", fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
                items(archived) { plan ->
                    PlanCard(
                        plan, repo, navController,
                        onArchive = { repo.unarchiveStudyPlan(plan.id); reload() },
                        onEdit = { editing = plan },
                        onDelete = { showDeleteDialog = plan },
                        archived = true
                    )
                }
            }
        }
    }

    if (showCreate) {
        PlanEditDialog(
            repo = repo, initial = null,
            onDismiss = { showCreate = false },
            onSaved = { showCreate = false; reload() }
        )
    }
    editing?.let { plan ->
        PlanEditDialog(
            repo = repo, initial = plan,
            onDismiss = { editing = null },
            onSaved = { editing = null; reload() }
        )
    }
    showDeleteDialog?.let { plan ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("删除计划") },
            text = { Text("确定要删除「${plan.title}」吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    repo.deleteStudyPlan(plan.id)
                    repo.clearPlanProgress(plan.id)
                    showDeleteDialog = null
                    reload()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun PlanCard(
    plan: StudyPlan,
    repo: com.example.shuati.data.AppRepository,
    navController: NavController,
    onArchive: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    archived: Boolean = false
) {
    val pp = remember(plan.id) { repo.getPlanProgress(plan.id) }
    val today = dateFmt.format(Date())
    val isStudy = plan.mode == "study"
    val isExpired = plan.endDate != null && today > plan.endDate
    val bankExists = repo.getBankById(plan.bankId) != null

    val todayStarted = pp?.sessionDate == today && pp.sessionQuestionIds.isNotEmpty()
    val sessionDone = pp?.sessionQuestionIds?.let { it.size > 0 && (pp.sessionAnsweredCount >= it.size || pp.sessionBrowseIndex >= it.size) } ?: false
    val punchedToday = pp?.punchInDates?.contains(today) ?: false

    val totalRounds = if (plan.cycleDays > 0) ceil(plan.totalQuestions.toDouble() / (plan.dailyCount * plan.cycleDays)).toInt() else 1
    val answeredTotal = pp?.answeredIds?.size ?: 0
    val roundNo = if (plan.dailyCount * plan.cycleDays > 0)
        (answeredTotal / (plan.dailyCount * plan.cycleDays)) + 1 else 1
    // 预计完成日期
    val daysToFinish = if (plan.dailyCount > 0) ceil(plan.totalQuestions.toDouble() / plan.dailyCount).toInt() else 0
    val estFinishDate = if (daysToFinish > 0) {
        val cal = Calendar.getInstance().apply { time = dateFmt.parse(plan.startDate) ?: Date() }
        cal.add(Calendar.DAY_OF_YEAR, daysToFinish)
        dateFmt.format(cal.time)
    } else null
    // 今日进度（仅当日会话生效；对齐小程序：今日进度 X/目标 + 进度条 + 完成横幅）
    val sessionActive = pp?.sessionDate == today
    val todayProgress = if (sessionActive) {
        if (isStudy) pp.sessionAnsweredCount else pp.sessionBrowseIndex
    } else 0
    val todayTotal = plan.dailyCount
    val isDoneToday = sessionDone || (todayTotal > 0 && todayProgress >= todayTotal)
    val progressText = if (!bankExists) "题库已删除"
    else if (answeredTotal > 0) "已完成 $answeredTotal / ${plan.totalQuestions} 题"
    else "每日 ${plan.dailyCount} 题"

    if (archived) {
        // 已归档：简洁卡
        CardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(plan.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Spacer(Modifier.height(3.dp))
                    Text("${plan.bankName} · $answeredTotal/${plan.totalQuestions} 题",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Badge("已完成", color = MaterialTheme.semantic.successContainer, textColor = MaterialTheme.semantic.onSuccessContainer)
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, null) }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) }
            }
        }
    } else {
        HeroCard {
            // 头部：标题 + 操作
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(plan.title, fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = (-0.3).sp,
                        color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(3.dp))
                    Text("${if (isStudy) "练习" else "浏览"}模式 · ${if (plan.orderType == "random") "随机" else "顺序"} · 每轮 ${plan.cycleDays} 天",
                        fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (punchedToday) {
                    Badge("今日已打卡", color = MaterialTheme.colorScheme.primaryContainer, textColor = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                IconButton(onClick = onArchive) { Icon(Icons.Filled.Archive, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.semantic.error) }
            }

            // 总进度 + 环
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RingProgress(
                    progress = if (plan.totalQuestions > 0) (answeredTotal.toFloat() / plan.totalQuestions).coerceIn(0f, 1f) else 0f,
                    size = 58.dp,
                    strokeWidth = 6.dp,
                    label = "${(answeredTotal * 100 / plan.totalQuestions.coerceAtLeast(1))}%"
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("已完成 $answeredTotal / ${plan.totalQuestions} 题",
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(6.dp))
                    ThinProgress(
                        progress = if (plan.totalQuestions > 0) answeredTotal.toFloat() / plan.totalQuestions else 0f,
                        height = 6.dp
                    )
                    Spacer(Modifier.height(7.dp))
                    Text("第 $roundNo/$totalRounds 轮 · 预计完成 ${estFinishDate ?: "—"}",
                        fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    // 捕获为局部 val：lambda 内无法保留对 plan.endDate 的智能转换
                    val endDate = plan.endDate
                    if (endDate != null && !isExpired) {
                        Spacer(Modifier.height(3.dp))
                        val remainDays = runCatching {
                            val df = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                            // SimpleDateFormat#parse 返回平台类型（Kotlin 视为 Date?），需安全取值
                            val endMs = df.parse(endDate)?.time
                            val nowMs = df.parse(today)?.time
                            if (endMs != null && nowMs != null) (endMs - nowMs) / 86400000L else null
                        }.getOrNull()?.toInt()
                        if (remainDays != null) {
                            Text(
                                if (remainDays == 0) "今天截止" else "还剩 $remainDays 天",
                                fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                                color = if (remainDays <= 3) MaterialTheme.semantic.warning else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))

            PunchCalendar(plan, pp, today)
            Spacer(Modifier.height(8.dp))

            // 今日进度
            if (todayTotal > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("今日进度", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text("$todayProgress/$todayTotal", fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        color = if (isDoneToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.height(6.dp))
                ThinProgress(progress = (todayProgress.toFloat() / todayTotal).coerceIn(0f, 1f), height = 6.dp)
                if (isDoneToday) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, null, Modifier.size(14.dp), tint = MaterialTheme.semantic.success)
                        Spacer(Modifier.width(4.dp))
                        Text("今日目标已完成", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.semantic.success)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            SoftButton(
                text = when {
                    !bankExists -> "题库已删除"
                    isExpired -> "计划已过期"
                    !todayStarted -> "开始今日任务"
                    sessionDone -> "今日已完成（重做）"
                    else -> "继续今日任务"
                },
                icon = Icons.Filled.PlayArrow,
                onClick = {
                    if (isExpired || !bankExists) return@SoftButton
                    val ids = buildSessionIds(repo, plan)
                    // 会话记录统一存复合键「bankId::题目ID」，支持单个或多个题库的计划
                    val storedIds = ids.map { (bid, qid) -> "$bid::$qid" }
                    if (pp == null || pp.sessionDate != today) {
                        repo.initPlanSession(plan.id, storedIds, today, pp?.answeredIds ?: emptyList())
                    }
                    val startIdx = if (isStudy) pp?.sessionAnsweredCount ?: 0 else pp?.sessionBrowseIndex ?: 0
                    val firstPair = ids.getOrNull(startIdx)
                    if (firstPair != null) {
                        val realMode = if (isStudy) plan.orderType else "browse"
                        // 每道题带上自己的题库ID，保证跨题库也能正确定位
                        val remainingIds = ids.subList(startIdx, ids.size)
                            .joinToString(",") { (bid, qid) -> "$bid::$qid" }
                        navController.navigate("${Routes.QUIZ}?bankId=${plan.bankId}&mode=$realMode&source=plan&planId=${plan.id}&questionIds=$remainingIds")
                    }
                },
                enabled = !archived && !isExpired && bankExists
            )
        }
    }
}

@Composable
private fun PunchCalendar(plan: StudyPlan, pp: com.example.shuati.model.PlanProgress?, today: String) {
    val startCal = Calendar.getInstance().apply { time = dateFmt.parse(plan.startDate) ?: Date() }
    // 显示范围：有截止日则到截止日；否则覆盖到预计完成（至少 4 周）
    val endCal = if (plan.endDate != null) {
        Calendar.getInstance().apply { time = dateFmt.parse(plan.endDate) ?: Date() }
    } else {
        val daysToFinish = if (plan.dailyCount > 0) ceil(plan.totalQuestions.toDouble() / plan.dailyCount).toInt() else 0
        val span = if (daysToFinish > 28) daysToFinish else 28
        Calendar.getInstance().apply { time = startCal.time; add(Calendar.DAY_OF_YEAR, span) }
    }
    // 对齐到整周（周一为首列、周日为尾列），保证 7 列占满整行、无右侧空白
    val gridStart = Calendar.getInstance().apply { time = startCal.time }
    while (gridStart.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) gridStart.add(Calendar.DAY_OF_YEAR, -1)
    val gridEnd = Calendar.getInstance().apply { time = endCal.time }
    while (gridEnd.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) gridEnd.add(Calendar.DAY_OF_YEAR, 1)
    val totalDays = ((gridEnd.timeInMillis - gridStart.timeInMillis) / 86400000L).toInt() + 1
    val weeks = totalDays / 7

    val weekLabels = listOf("一", "二", "三", "四", "五", "六", "日")
    Text("打卡日历", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        weekLabels.forEach {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(it, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(weeks) { r ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(7) { c ->
                    val idx = r * 7 + c
                    val cal = Calendar.getInstance().apply { time = gridStart.time; add(Calendar.DAY_OF_YEAR, idx) }
                    val ds = dateFmt.format(cal.time)
                    val inRange = cal.timeInMillis in startCal.timeInMillis..endCal.timeInMillis
                    val isToday = ds == today
                    val punched = pp?.punchInDates?.contains(ds) ?: false
                    val expiredCell = plan.endDate != null && ds > plan.endDate
                    val bg = when {
                        !inRange -> MaterialTheme.colorScheme.surface
                        punched -> MaterialTheme.colorScheme.primary
                        isToday -> MaterialTheme.colorScheme.tertiary
                        expiredCell -> MaterialTheme.colorScheme.surfaceVariant
                        else -> MaterialTheme.colorScheme.outlineVariant
                    }
                    val txtColor = when {
                        !inRange -> MaterialTheme.colorScheme.surface
                        punched || isToday -> MaterialTheme.colorScheme.onPrimary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Box(
                        Modifier.weight(1f).aspectRatio(1f).clip(MaterialTheme.shapes.extraSmall).background(bg),
                        contentAlignment = Alignment.Center
                    ) {
                        if (inRange) Text("${cal.get(Calendar.DAY_OF_MONTH)}", fontSize = 11.sp, color = txtColor)
                    }
                }
            }
        }
    }
    if (plan.endDate != null) {
        Spacer(Modifier.height(4.dp))
        Text("计划周期 ${plan.startDate} ~ ${plan.endDate}",
            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * 构建本次会话的题目列表，返回 (题库ID, 题目ID) 对。
 * 计划覆盖 bankIds 指定的所有题库（单个或多个题库均用同一套逻辑）。
 */
private fun buildSessionIds(
    repo: com.example.shuati.data.AppRepository,
    plan: StudyPlan
): List<Pair<String, String>> {
    val answered = repo.getPlanProgress(plan.id)?.answeredIds ?: emptyList()
    val targetBanks = plan.bankIds.mapNotNull { repo.getBankById(it) }
    if (targetBanks.isEmpty()) return emptyList()

    // 未答题目优先；全部答完则回到全量（进入下一轮）
    val remaining = targetBanks.flatMap { b ->
        b.questions.filter { it.id !in answered }.map { b.id to it.id }
    }
    val pool = if (remaining.isEmpty()) {
        targetBanks.flatMap { b -> b.questions.map { b.id to it.id } }
    } else remaining

    val shuffled = if (plan.orderType == "random") pool.shuffled() else pool
    return shuffled.take(plan.dailyCount)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanEditDialog(
    repo: com.example.shuati.data.AppRepository,
    initial: StudyPlan?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val banks = remember { repo.getBanks() }
    var title by remember { mutableStateOf(initial?.title ?: "") }
    // 题库选择支持多选组合：空集合 = 全部题库；非空 = 仅这些题库
    var selectedBankIds by remember {
        mutableStateOf<Set<String>>(
            initial?.bankIds?.takeIf { it.isNotEmpty() }?.toSet()
                ?: initial?.bankId?.takeIf { it.isNotBlank() }?.let { setOf(it) }
                ?: emptySet()
        )
    }
    var dailyCount by remember { mutableStateOf((initial?.dailyCount ?: 10).toString()) }
    var cycleDays by remember { mutableStateOf((initial?.cycleDays ?: 7).toString()) }
    var mode by remember { mutableStateOf(initial?.mode ?: "study") }
    var orderType by remember { mutableStateOf(initial?.orderType ?: "sequence") }
    var startDate by remember { mutableStateOf(initial?.startDate ?: dateFmt.format(Date())) }
    var endDate by remember { mutableStateOf(initial?.endDate ?: "") }

    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    val startState = rememberDatePickerState(
        initialSelectedDateMillis = runCatching { dateFmt.parse(startDate)?.time }.getOrNull() ?: System.currentTimeMillis()
    )
    val endState = rememberDatePickerState(
        initialSelectedDateMillis = runCatching {
            if (endDate.isNotBlank()) dateFmt.parse(endDate)?.time else dateFmt.parse(startDate)?.time
        }.getOrNull() ?: System.currentTimeMillis()
    )
    // 每日题量上限 = 所选题库题目数之和（未选=全部题库）
    val selectedMax = if (selectedBankIds.isEmpty()) banks.sumOf { it.questions.size }
    else selectedBankIds.sumOf { repo.getBankById(it)?.questions?.size ?: 0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "创建学习计划" else "编辑学习计划") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("计划名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                // 题库选择：支持多选组合，「全部题库」= 清空选择
                Text("题库（可多选）", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedBankIds.isEmpty(),
                        onClick = { selectedBankIds = emptySet() },
                        label = { Text("全部题库") }
                    )
                    banks.forEach { b ->
                        FilterChip(
                            selected = b.id in selectedBankIds,
                            onClick = {
                                selectedBankIds = if (b.id in selectedBankIds) selectedBankIds - b.id
                                else selectedBankIds + b.id
                            },
                            label = { Text("${b.name}（${b.questions.size}）") }
                        )
                    }
                }
                OutlinedTextField(dailyCount, { dailyCount = it.filter { c -> c.isDigit() } },
                    label = { Text("每日题量") }, singleLine = true,
                    supportingText = { Text("最多 $selectedMax 题（已选 ${if (selectedBankIds.isEmpty()) "全部" else selectedBankIds.size} 个题库，共 $selectedMax 题）") },
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(cycleDays, { cycleDays = it.filter { c -> c.isDigit() } },
                    label = { Text("每轮天数") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                DateField("开始日期", startDate, "选择开始日期", onClick = { showStartPicker = true })
                DateField("截止日期", endDate, "留空 = 不限", onClick = { showEndPicker = true })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("模式：", fontSize = 13.sp)
                    Spacer(Modifier.width(4.dp))
                    FilterChip(selected = mode == "study", onClick = { mode = "study" }, label = { Text("练习") })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = mode == "browse", onClick = { mode = "browse" }, label = { Text("浏览") })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("顺序：", fontSize = 13.sp)
                    Spacer(Modifier.width(4.dp))
                    FilterChip(selected = orderType == "sequence", onClick = { orderType = "sequence" }, label = { Text("顺序") })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = orderType == "random", onClick = { orderType = "random" }, label = { Text("随机") })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isBlank()) return@TextButton
                // 未选 = 全部题库（沿用旧语义，用第一个题库作占位）；选中 1 个 = 单题库；选多个 = 多题库组合
                val picked = selectedBankIds.mapNotNull { repo.getBankById(it) }
                val isAll = selectedBankIds.isEmpty()
                val targetBanks = if (isAll) banks else picked
                if (targetBanks.isEmpty()) return@TextButton

                val primary = targetBanks.first()
                val total = targetBanks.sumOf { it.questions.size }
                val plan = StudyPlan(
                    id = initial?.id ?: UUID.randomUUID().toString(),
                    title = title.trim(),
                    bankId = primary.id,
                    bankName = if (isAll) "全部题库" else targetBanks.joinToString("、") { it.name },
                    totalQuestions = total,
                    dailyCount = (dailyCount.toIntOrNull() ?: 1).coerceIn(1, if (selectedMax > 1) selectedMax else 1),
                    mode = mode,
                    orderType = orderType,
                    startDate = startDate,
                    endDate = endDate.ifBlank { null },
                    cycleDays = cycleDays.toIntOrNull() ?: 7,
                    // isAllBanks 表示动态覆盖全部题库（含未来新增）；bankIds 始终写入当前覆盖的题库 ID 列表
                    isAllBanks = isAll,
                    bankIds = targetBanks.map { it.id },
                    enabled = initial?.enabled ?: true,
                    archived = initial?.archived ?: false,
                    createdAt = initial?.createdAt ?: System.currentTimeMillis()
                )
                repo.saveStudyPlan(plan)
                onSaved()
            }) { Text(if (initial == null) "创建" else "保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )

    if (showStartPicker) {
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = { TextButton(onClick = {
                startState.selectedDateMillis?.let { startDate = dateFmt.format(Date(it)) }
                showStartPicker = false
            }) { Text("确定") } },
            dismissButton = { TextButton(onClick = { showStartPicker = false }) { Text("取消") } }
        ) { DatePicker(state = startState) }
    }
    if (showEndPicker) {
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = { TextButton(onClick = {
                endState.selectedDateMillis?.let { endDate = dateFmt.format(Date(it)) }
                showEndPicker = false
            }) { Text("确定") } },
            dismissButton = { TextButton(onClick = { showEndPicker = false }) { Text("取消") } }
        ) { DatePicker(state = endState) }
    }
}

/** 可点击的日期字段（用 Surface 承载点击，避免 OutlinedTextField 吞掉手势导致选择器不弹） */
@Composable
private fun DateField(label: String, value: String, placeholder: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraSmall,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(3.dp))
                Text(
                    if (value.isBlank()) placeholder else value,
                    fontSize = 15.sp,
                    color = if (value.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(Icons.Filled.DateRange, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
