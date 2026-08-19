package com.example.shuati.ui.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
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
import com.example.shuati.ui.components.ScreenTopBar
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

    fun reload() { plans = repo.getStudyPlans() }
    LaunchedEffect(Unit) { reload() }

    val active = plans.filter { !it.archived }
    val archived = plans.filter { it.archived }

    Scaffold(
        topBar = { ScreenTopBar("学习计划", onBack = { navController.popBackStack() }) },
        floatingActionButton = { FloatingActionButton(onClick = { showCreate = true }) { Icon(Icons.Filled.Add, null) } }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            if (active.isEmpty() && archived.isEmpty()) {
                item { EmptyState("还没有学习计划，点右下角创建一个") }
            }
            if (active.isNotEmpty()) {
                item { Text("进行中", fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
                items(active) { plan ->
                    PlanCard(
                        plan, repo, navController,
                        onArchive = { repo.archiveStudyPlan(plan.id); reload() },
                        onEdit = { editing = plan }
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
}

@Composable
private fun PlanCard(
    plan: StudyPlan,
    repo: com.example.shuati.data.AppRepository,
    navController: NavController,
    onArchive: () -> Unit,
    onEdit: () -> Unit,
    archived: Boolean = false
) {
    val pp = remember(plan.id) { repo.getPlanProgress(plan.id) }
    val today = dateFmt.format(Date())
    val isStudy = plan.mode == "study"
    val isExpired = plan.endDate != null && today > plan.endDate

    val todayStarted = pp?.sessionDate == today && pp.sessionQuestionIds.isNotEmpty()
    val sessionDone = pp?.sessionQuestionIds?.let { it.size > 0 && (pp.sessionAnsweredCount >= it.size || pp.sessionBrowseIndex >= it.size) } ?: false
    val punchedToday = pp?.punchInDates?.contains(today) ?: false

    val totalRounds = if (plan.cycleDays > 0) ceil(plan.totalQuestions.toDouble() / (plan.dailyCount * plan.cycleDays)).toInt() else 1
    val answeredTotal = pp?.answeredIds?.size ?: 0
    val roundNo = if (plan.dailyCount * plan.cycleDays > 0)
        (answeredTotal / (plan.dailyCount * plan.cycleDays)) + 1 else 1

    CardBox {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(plan.title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Spacer(Modifier.height(4.dp))
                Text("${plan.bankName} · ${if (isStudy) "练习模式" else "浏览模式"} · ${if (plan.orderType == "random") "随机" else "顺序"}",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("每日 ${plan.dailyCount} 题" +
                        (if (plan.dailyTargetCeiling > 0) "（上限 ${plan.dailyTargetCeiling}）" else "") +
                        " · 每轮 ${plan.cycleDays} 天 · 第 $roundNo/$totalRounds 轮",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("开始 $today · 截止 ${plan.endDate ?: "不限"}",
                    fontSize = 13.sp, color = if (isExpired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                if (punchedToday) {
                    Spacer(Modifier.height(4.dp))
                    AssistChip(onClick = {}, label = { Text("今日已打卡") },
                        leadingIcon = { Icon(Icons.Filled.CheckCircle, null, Modifier.size(16.dp)) })
                }
                if (isExpired) {
                    Spacer(Modifier.height(4.dp))
                    AssistChip(onClick = {}, label = { Text("已过期") },
                        leadingIcon = { Icon(Icons.Filled.Archive, null, Modifier.size(16.dp)) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.errorContainer))
                }
            }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, null) }
            IconButton(onClick = onArchive) { Icon(Icons.Filled.Archive, null) }
        }

        Spacer(Modifier.height(10.dp))
        // 28 天打卡日历（每轮 cycleDays 天，展示 4 轮 × cycleDays）
        PunchCalendar(plan, pp, today)
        Spacer(Modifier.height(10.dp))

        Button(
            onClick = {
                if (isExpired) return@Button
                val ids = buildSessionIds(repo, plan)
                if (pp == null || pp.sessionDate != today) {
                    repo.initPlanSession(plan.id, ids, today, pp?.answeredIds ?: emptyList())
                }
                val startIdx = if (isStudy) pp?.sessionAnsweredCount ?: 0 else pp?.sessionBrowseIndex ?: 0
                val firstId = ids.getOrNull(startIdx)
                if (firstId != null) {
                    val realMode = if (isStudy) plan.orderType else "browse"
                    navController.navigate("${Routes.QUIZ}?bankId=${plan.bankId}&mode=$realMode&source=plan&planId=${plan.id}")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !archived && !isExpired
        ) {
            Icon(Icons.Filled.PlayArrow, null)
            Spacer(Modifier.width(8.dp))
            Text(when {
                isExpired -> "计划已过期"
                !todayStarted -> "开始今日任务"
                sessionDone -> "今日已完成（重做）"
                else -> "继续今日任务"
            })
        }
    }
}

@Composable
private fun PunchCalendar(plan: StudyPlan, pp: com.example.shuati.model.PlanProgress?, today: String) {
    val cols = plan.cycleDays.coerceAtLeast(1)
    val rows = 4
    val totalCells = cols * rows
    // 计算当前轮起始日（基于 startDate 与 cycleDays）
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
    var selectedBankId by remember { mutableStateOf(initial?.bankId ?: banks.firstOrNull()?.id ?: "") }
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
            Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("计划名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                ExposedDropdownMenuBox(bankExpanded, { bankExpanded = !bankExpanded }) {
                    OutlinedTextField(
                        banks.find { it.id == selectedBankId }?.name ?: "选择题库", {},
                        readOnly = true, modifier = Modifier.menuAnchor().fillMaxWidth(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(bankExpanded) }
                    )
                    ExposedDropdownMenu(bankExpanded, { bankExpanded = false }) {
                        banks.forEach { b ->
                            DropdownMenuItem(text = { Text(b.name) }, onClick = { selectedBankId = b.id; bankExpanded = false })
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
                        isAllBanks = initial?.isAllBanks ?: false,
                        enabled = initial?.enabled ?: true,
                        archived = initial?.archived ?: false,
                        createdAt = initial?.createdAt ?: System.currentTimeMillis()
                    )
                    repo.saveStudyPlan(plan)
                    onSaved()
                }
            }) { Text(if (initial == null) "创建" else "保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
