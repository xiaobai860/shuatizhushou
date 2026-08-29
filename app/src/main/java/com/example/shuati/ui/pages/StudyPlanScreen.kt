package com.example.shuati.ui.pages


import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
        if (isStudy) pp?.sessionAnsweredCount ?: 0 else pp?.sessionBrowseIndex ?: 0
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
                    if (pp == null || pp.sessionDate != today) {
                        repo.initPlanSession(plan.id, ids, today, pp?.answeredIds ?: emptyList())
                    }
                    val startIdx = if (isStudy) pp?.sessionAnsweredCount ?: 0 else pp?.sessionBrowseIndex ?: 0
                    val firstId = ids.getOrNull(startIdx)
                    if (firstId != null) {
                        val realMode = if (isStudy) plan.orderType else "browse"
                        val remainingIds = ids.subList(startIdx, ids.size).joinToString(",") { "$plan.bankId::$it" }
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
    val cols = plan.cycleDays.coerceAtLeast(1)
    val rows = 4
    val startCal = Calendar.getInstance().apply { time = dateFmt.parse(plan.startDate) ?: Date() }
    val todayCal = Calendar.getInstance().apply { time = dateFmt.parse(today) ?: Date() }
    val daysBetween = ((todayCal.timeInMillis - startCal.timeInMillis) / 86400000L).toInt()
    val currentRound = if (plan.cycleDays > 0) (daysBetween / plan.cycleDays).coerceAtLeast(0) else 0

    Text("打卡日历（每轮 $cols 天）", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(rows) { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(cols) { c ->
                    val cellDate = Calendar.getInstance().apply {
                        time = startCal.time
                        add(Calendar.DAY_OF_YEAR, r * cols + c)
                    }
                    val ds = dateFmt.format(cellDate.time)
                    val isPastOrToday = cellDate.timeInMillis <= todayCal.timeInMillis
                    val isToday = ds == today
                    val punched = pp?.punchInDates?.contains(ds) ?: false
                    val expiredCell = plan.endDate != null && ds > plan.endDate
                    val bg = when {
                        punched -> MaterialTheme.colorScheme.primary
                        isToday -> MaterialTheme.colorScheme.tertiary
                        expiredCell -> MaterialTheme.colorScheme.surfaceVariant
                        isPastOrToday -> MaterialTheme.colorScheme.outlineVariant
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    val txtColor = when {
                        punched || isToday -> MaterialTheme.colorScheme.onPrimary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Surface(
                        color = bg, shape = MaterialTheme.shapes.extraSmall,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("${r * cols + c + 1}", fontSize = 11.sp, color = txtColor)
                        }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    Text("第 ${currentRound + 1} 轮 · 当前轮第 ${(daysBetween % plan.cycleDays).coerceAtLeast(0) + 1} 天",
        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun buildSessionIds(repo: com.example.shuati.data.AppRepository, plan: StudyPlan): List<String> {
    val bank = repo.getBankById(plan.bankId) ?: return emptyList()
    val remaining = bank.questions.filter { !(repo.getPlanProgress(plan.id)?.answeredIds?.contains(it.id) ?: false) }
    val pool = if (remaining.isEmpty()) bank.questions else remaining
    val shuffled = if (plan.orderType == "random") pool.shuffled() else pool
    return shuffled.take(plan.dailyCount).map { it.id }
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
    var selectedBankId by remember { mutableStateOf(initial?.bankId ?: "") }
    var dailyCount by remember { mutableStateOf((initial?.dailyCount ?: 10).toString()) }
    var cycleDays by remember { mutableStateOf((initial?.cycleDays ?: 7).toString()) }
    var dailyTargetCeiling by remember { mutableStateOf((initial?.dailyTargetCeiling ?: 0).toString()) }
    var mode by remember { mutableStateOf(initial?.mode ?: "study") }
    var orderType by remember { mutableStateOf(initial?.orderType ?: "sequence") }
    var startDate by remember { mutableStateOf(initial?.startDate ?: dateFmt.format(Date())) }
    var endDate by remember { mutableStateOf(initial?.endDate ?: "") }
    var bankExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "创建学习计划" else "编辑学习计划") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("计划名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                // 全部题库选项
                FilterChip(
                    selected = selectedBankId.isEmpty(),
                    onClick = { selectedBankId = "" },
                    label = { Text("全部题库") },
                    modifier = Modifier.align(Alignment.Start)
                )
                if (banks.isNotEmpty()) {
                    ExposedDropdownMenuBox(bankExpanded, { bankExpanded = !bankExpanded }) {
                        OutlinedTextField(
                            if (selectedBankId.isEmpty()) "全部题库" else (banks.find { it.id == selectedBankId }?.name ?: "选择题库"),
                            {}, readOnly = true,
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(bankExpanded) }
                        )
                        ExposedDropdownMenu(bankExpanded, { bankExpanded = false }) {
                            banks.forEach { b ->
                                DropdownMenuItem(text = { Text(b.name) },
                                    onClick = { selectedBankId = b.id; bankExpanded = false })
                            }
                        }
                    }
                }
                OutlinedTextField(dailyCount, { dailyCount = it.filter { c -> c.isDigit() } },
                    label = { Text("每日题量") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(dailyTargetCeiling, { dailyTargetCeiling = it.filter { c -> c.isDigit() } },
                    label = { Text("每日上限（0=不限）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(cycleDays, { cycleDays = it.filter { c -> c.isDigit() } },
                    label = { Text("每轮天数") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(startDate, { startDate = it }, label = { Text("开始日期 (YYYY-MM-DD)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(endDate, { endDate = it }, label = { Text("截止日期 (YYYY-MM-DD，留空不限)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
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
                if (selectedBankId.isEmpty() && banks.isNotEmpty()) {
                    // 全部题库：用第一个可用题库作为占位
                    val bank = banks.first()
                    val plan = StudyPlan(
                        id = initial?.id ?: UUID.randomUUID().toString(),
                        title = title.trim(),
                        bankId = bank.id,
                        bankName = bank.name,
                        totalQuestions = bank.questions.size,
                        dailyCount = dailyCount.toIntOrNull() ?: 10,
                        mode = mode,
                        orderType = orderType,
                        startDate = startDate,
                        endDate = endDate.ifBlank { null },
                        cycleDays = cycleDays.toIntOrNull() ?: 7,
                        dailyTargetCeiling = dailyTargetCeiling.toIntOrNull() ?: 0,
                        isAllBanks = true,
                        enabled = initial?.enabled ?: true,
                        archived = initial?.archived ?: false,
                        createdAt = initial?.createdAt ?: System.currentTimeMillis()
                    )
                    repo.saveStudyPlan(plan)
                    onSaved()
                } else {
                    val bank = repo.getBankById(selectedBankId)
                    if (bank != null && title.isNotBlank()) {
                        val plan = StudyPlan(
                            id = initial?.id ?: UUID.randomUUID().toString(),
                            title = title.trim(),
                            bankId = bank.id,
                            bankName = bank.name,
                            totalQuestions = bank.questions.size,
                            dailyCount = dailyCount.toIntOrNull() ?: 10,
                            mode = mode,
                            orderType = orderType,
                            startDate = startDate,
                            endDate = endDate.ifBlank { null },
                            cycleDays = cycleDays.toIntOrNull() ?: 7,
                            dailyTargetCeiling = dailyTargetCeiling.toIntOrNull() ?: 0,
                            isAllBanks = false,
                            enabled = initial?.enabled ?: true,
                            archived = initial?.archived ?: false,
                            createdAt = initial?.createdAt ?: System.currentTimeMillis()
                        )
                        repo.saveStudyPlan(plan)
                        onSaved()
                    }
                }
            }) { Text(if (initial == null) "创建" else "保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
