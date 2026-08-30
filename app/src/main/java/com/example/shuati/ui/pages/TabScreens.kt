package com.example.shuati.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.shuati.model.QuestionBank
import com.example.shuati.ui.*
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import com.example.shuati.ui.components.Badge
import com.example.shuati.ui.components.CardBox
import com.example.shuati.ui.components.ChevronTrailing
import com.example.shuati.ui.components.EmptyState
import com.example.shuati.ui.components.HeroCard
import com.example.shuati.ui.components.ListRow
import com.example.shuati.ui.components.SectionTitle
import com.example.shuati.ui.components.SoftButton
import com.example.shuati.ui.components.StatTile
import com.example.shuati.ui.components.ShuatiSwitch
import com.example.shuati.data.AppRepository.StorageBreakdown
import com.example.shuati.ui.components.ThinProgress
import com.example.shuati.ui.theme.ShuatiSpacing
import com.example.shuati.ui.theme.semantic

// ─────────────────────────── 题库中心 ───────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryTabScreen(navController: NavController) {
    val repo = repository()
    var banks by remember { mutableStateOf(emptyList<QuestionBank>()) }
    var wrongs by remember { mutableStateOf(emptyList<com.example.shuati.model.WrongRecord>()) }
    var favs by remember { mutableStateOf(emptyList<com.example.shuati.model.FavoriteRecord>()) }
    LaunchedEffect(Unit) {
        banks = repo.getBanks()
        wrongs = repo.getWrongRecords()
        favs = repo.getFavoriteRecords()
    }
    val totalQuestions = banks.sumOf { it.questions.size }
    // 每题库重做/收藏数
    val wrongByBank = wrongs.groupingBy { it.bankId }.eachCount()
    val favByBank = favs.groupingBy { it.bankId }.eachCount()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(52.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "题库",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f).padding(start = 12.dp)
                    )
                    IconButton(onClick = { navController.navigate(Routes.SEARCH) }) {
                        Icon(Icons.Filled.Search, "搜索", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = ShuatiSpacing.PageHorizontal),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
        ) {
            // 汇总条
            item {
                CardBox {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    banks.size.toString(),
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = (-0.8).sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("个题库", fontSize = 11.5.sp, modifier = Modifier.padding(bottom = 3.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    totalQuestions.toString(),
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = (-0.8).sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("道题", fontSize = 11.5.sp, modifier = Modifier.padding(bottom = 3.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(8.dp))
                            ThinProgress(progress = 0.62f, color = MaterialTheme.colorScheme.tertiary)
                        }
                        Spacer(Modifier.width(14.dp))
                        RingSummary(progress = 0.62f, label = "62%")
                    }
                }
            }

            // 快捷入口
            item {
                Spacer(Modifier.height(4.dp))
                SectionTitle("快捷入口")
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickTile(Icons.Filled.Folder, "题库管理", "新建 · 批量", Modifier.weight(1f),
                        MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer
                    ) { navController.navigate(Routes.LIBRARY) }
                    QuickTile(Icons.Filled.Download, "导入题库", "Excel 模板", Modifier.weight(1f),
                        MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer
                    ) { navController.navigate(Routes.IMPORT) }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickTile(Icons.Filled.CopyAll, "查重检测", "相似度 v2", Modifier.weight(1f),
                        MaterialTheme.semantic.infoContainer, MaterialTheme.semantic.onInfoContainer
                    ) { navController.navigate(Routes.DEDUP) }
                    QuickTile(Icons.Filled.AutoStories, "全部题库", "$totalQuestions 题", Modifier.weight(1f),
                        MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        navController.navigate("${Routes.QUIZ}?bankId=__all__&mode=sequence&source=all&showModeSelect=true")
                    }
                }
            }

            // 我的题库
            item {
                Spacer(Modifier.height(4.dp))
                SectionTitle("我的题库", count = "${banks.size} 个", action = "管理", onAction = { navController.navigate(Routes.LIBRARY) })
            }
            if (banks.isEmpty()) {
                item { EmptyState("还没有题库，去「导入题库」添加吧") }
            } else {
                items(banks.sortedByDescending { it.updatedAt }) { bank ->
                    val progress = repo.getProgress(bank.id)
                    val answered = progress?.answeredIds?.size ?: 0
                    val total = bank.questions.size
                    val pct = if (total > 0) (answered.toFloat() / total).coerceIn(0f, 1f) else 0f
                    val completed = progress?.everCompleted ?: false

                    CardBox(onClick = { navController.navigate("${Routes.EDIT_BANK}?bankId=${bank.id}") }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(bank.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Spacer(Modifier.width(6.dp))
                                    if (completed) {
                                        Badge("已刷完", color = MaterialTheme.semantic.successContainer, textColor = MaterialTheme.semantic.onSuccessContainer)
                                    }
                                }
                                Spacer(Modifier.height(3.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "$total 题" + if (answered > 0) " · 已答 $answered" else "",
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    val wN = wrongByBank[bank.id] ?: 0
                                    val fN = favByBank[bank.id] ?: 0
                                    if (wN > 0) {
                                        Spacer(Modifier.width(8.dp))
                                        Badge("错题 $wN", color = MaterialTheme.semantic.errorContainer, textColor = MaterialTheme.semantic.onErrorContainer)
                                    }
                                    if (fN > 0) {
                                        Spacer(Modifier.width(4.dp))
                                        Badge("收藏 $fN", color = MaterialTheme.semantic.warningContainer, textColor = MaterialTheme.semantic.onWarningContainer)
                                    }
                                }
                            }
                            TextButton(onClick = {
                                navController.navigate("${Routes.QUIZ}?bankId=${bank.id}&mode=sequence&source=bank&showModeSelect=true")
                            }) { Text("刷题", fontSize = 13.sp) }
                        }
                        if (total > 0) {
                            Spacer(Modifier.height(8.dp))
                            ThinProgress(progress = pct, height = 4.dp)
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────── 学习中心 ───────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyTabScreen(navController: NavController) {
    val repo = repository()
    var wrongCount by remember { mutableStateOf(0) }
    var favCount by remember { mutableStateOf(0) }
    var historyCount by remember { mutableStateOf(0) }
    var planCount by remember { mutableStateOf(0) }
    // 本周活跃 / 掌握度
    var historyList by remember { mutableStateOf(emptyList<com.example.shuati.model.QuizHistory>()) }
    var masteredN by remember { mutableStateOf(0) }
    var wrongN by remember { mutableStateOf(0) }
    var totalN by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        wrongCount = repo.getWrongRecords().size
        favCount = repo.getFavoriteRecords().size
        historyList = repo.getHistoryRecords()
        historyCount = historyList.size
        planCount = repo.getStudyPlans().count { !it.archived }
        // 掌握度：连对次数达标的题数 / 错题数 / 全部题库题数
        val target = repo.getMasteryTarget()
        masteredN = repo.getMasteryStreaks().count { it.value >= target }
        wrongN = repo.getWrongRecords().distinctBy { "${it.bankId}::${it.questionId}" }.size
        totalN = repo.getBanks().sumOf { it.questions.size }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(52.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "学习",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = ShuatiSpacing.PageHorizontal),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
        ) {
            // 智能推荐 Hero
            item {
                HeroCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "智能推荐",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(5.dp))
                            Text(
                                "为你精选值得复习的题",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.4).sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "综合错题、收藏、正确率与新题计算",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(14.dp))
                            SoftButton(
                                text = "查看推荐",
                                compact = true,
                                modifier = Modifier.width(112.dp),
                                onClick = { navController.navigate(Routes.RECOMMEND) }
                            )
                            // 推荐构成 chips（从错题/收藏/历史推导）
                            val recomErr = wrongCount
                            val recomFav = favCount
                            val recomNew = historyCount
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                AssistChip(
                                    onClick = { navController.navigate(Routes.RECOMMEND) },
                                    label = { Text("高频错题 $recomErr", fontSize = 10.5.sp) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                    )
                                )
                                AssistChip(
                                    onClick = { navController.navigate(Routes.RECOMMEND) },
                                    label = { Text("收藏未掌握 $recomFav", fontSize = 10.5.sp) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                    )
                                )
                                AssistChip(
                                    onClick = { navController.navigate(Routes.RECOMMEND) },
                                    label = { Text("新题 $recomNew", fontSize = 10.5.sp) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                    )
                                )
                            }
                        }
                        Icon(
                            Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(46.dp)
                        )
                    }
                }
            }

            // 本周活跃（周打卡日历）
            item {
                WeekActivityCard(historyList)
            }

            // 掌握度（分段条）
            item {
                MasteryCard(masteredN = masteredN, wrongN = wrongN, totalN = totalN)
            }

            // 学习记录
            item {
                Spacer(Modifier.height(4.dp))
                SectionTitle("学习记录")
            }
            item {
                Column(Modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface)) {
                    ListRow(
                        icon = { Icon(Icons.Filled.Flag, null, Modifier.size(18.dp)) },
                        iconBackground = MaterialTheme.colorScheme.secondaryContainer,
                        iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        title = "学习计划",
                        subtitle = "进行中的学习计划",
                        trailing = { ChevronTrailing(planCount.toString()) },
                        onClick = { navController.navigate(Routes.STUDY_PLAN) }
                    )
                    ListRow(
                        icon = { Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp)) },
                        iconBackground = MaterialTheme.semantic.errorContainer,
                        iconColor = MaterialTheme.semantic.onErrorContainer,
                        title = "错题回顾",
                        subtitle = "答错的题会自动收进来",
                        trailing = { ChevronTrailing(wrongCount.toString()) },
                        onClick = { navController.navigate(Routes.WRONGS) }
                    )
                    ListRow(
                        icon = { Icon(Icons.Filled.Star, null, Modifier.size(18.dp)) },
                        iconBackground = MaterialTheme.semantic.warningContainer,
                        iconColor = MaterialTheme.semantic.onWarningContainer,
                        title = "收藏题目",
                        subtitle = "想反复看的题",
                        trailing = { ChevronTrailing(favCount.toString()) },
                        onClick = { navController.navigate(Routes.FAVORITES) }
                    )
                    ListRow(
                        icon = { Icon(Icons.Filled.History, null, Modifier.size(18.dp)) },
                        iconBackground = MaterialTheme.semantic.infoContainer,
                        iconColor = MaterialTheme.semantic.onInfoContainer,
                        title = "刷题历史",
                        subtitle = "每一次练习的记录",
                        trailing = { ChevronTrailing(historyCount.toString()) },
                        onClick = { navController.navigate(Routes.HISTORY) },
                        showDivider = false
                    )
                }
            }
        }
    }
}

// ─────────────────────────── 我的 ───────────────────────────

/** 本周活跃：周打卡日历 + 题数统计 */
@Composable
private fun WeekActivityCard(history: List<com.example.shuati.model.QuizHistory>) {
    val today = LocalDate.now()
    val monday = today.with(DayOfWeek.MONDAY)
    val zone = ZoneId.systemDefault()
    val counts = (0..6).associate { off ->
        val d = monday.plusDays(off.toLong())
        val n = history.count { h ->
            val hd = Instant.ofEpochMilli(h.finishedAt).atZone(zone).toLocalDate()
            hd == d
        }
        d to n
    }
    val punchDays = counts.values.count { it > 0 }
    val totalQ = counts.values.sum()

    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("本周活跃", fontSize = 15.5.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("共 $totalQ 题 · $punchDays 天打卡", fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            counts.forEach { (d, n) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        when (d.dayOfWeek) {
                            DayOfWeek.MONDAY -> "一"; DayOfWeek.TUESDAY -> "二"; DayOfWeek.WEDNESDAY -> "三"
                            DayOfWeek.THURSDAY -> "四"; DayOfWeek.FRIDAY -> "五"; DayOfWeek.SATURDAY -> "六"
                            else -> "日"
                        },
                        fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                when {
                                    n > 0 && d == today -> MaterialTheme.colorScheme.primary
                                    n > 0 -> MaterialTheme.colorScheme.primaryContainer
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (n > 0) n.toString() else "·",
                            fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            color = when {
                                n > 0 && d == today -> MaterialTheme.colorScheme.onPrimary
                                n > 0 -> MaterialTheme.colorScheme.onPrimaryContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    if (d == today) {
                        Spacer(Modifier.height(3.dp))
                        Text("今天", fontSize = 9.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

/** 掌握度：已掌握 / 模糊 / 未掌握 三段条 + 图例 */
@Composable
private fun MasteryCard(masteredN: Int, wrongN: Int, totalN: Int) {
    val colors = listOf(
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.semantic.warning,
        MaterialTheme.colorScheme.surfaceVariant
    )
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("掌握度", fontSize = 15.5.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("$totalN 题", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        if (totalN == 0) {
            // 无题库：完整空白进度条（轨道色），不显示均分占比
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        } else {
            val fuzzy = (totalN - masteredN - wrongN).coerceAtLeast(0)
            val segs = listOf(masteredN, fuzzy, wrongN)
            val sum = segs.sum().coerceAtLeast(1)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                segs.forEachIndexed { i, v ->
                    Box(
                        Modifier
                            .weight((v.toFloat() / sum).coerceAtLeast(0.01f))
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(colors[i])
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        val fuzzy = (totalN - masteredN - wrongN).coerceAtLeast(0)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            MasteryLegend(colors[0], "已掌握", masteredN)
            MasteryLegend(colors[1], "模糊", fuzzy)
            MasteryLegend(colors[2], "未掌握", wrongN)
        }
    }
}

@Composable
private fun MasteryLegend(color: Color, label: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.width(5.dp))
        Text("$label $count", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MineTabScreen(navController: NavController) {
    val repo = repository()
    val ctx = LocalContext.current
    var storageUsed by remember { mutableStateOf(0L) }
    var storageBreakdown by remember { mutableStateOf(StorageBreakdown(0, 0, 0)) }
    var remindOn by remember { mutableStateOf(true) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    val pkgInfo = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0) }.getOrNull()
    }
    val version = pkgInfo?.versionName
    // minSdk 34 ≥ API 28(P)，longVersionCode 始终可用，无需回退到已弃用的 versionCode
    val buildNumber = pkgInfo?.longVersionCode?.toString()
    LaunchedEffect(Unit) {
        storageUsed = repo.storageUsedBytes()
        storageBreakdown = repo.storageBreakdown()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(52.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "我的",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = ShuatiSpacing.PageHorizontal),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
        ) {
            // 用户卡
            item {
                HeroCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("我", color = MaterialTheme.colorScheme.onPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("我的题库", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "数据保存在本机",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 数据管理
            item {
                Spacer(Modifier.height(4.dp))
                SectionTitle("数据管理")
            }
            item {
                Column(Modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface)) {
                    ListRow(
                        icon = { Icon(Icons.Filled.BarChart, null, Modifier.size(18.dp)) },
                        iconBackground = MaterialTheme.colorScheme.primaryContainer,
                        iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        title = "统计分析",
                        subtitle = "正确率 · 趋势 · 分布",
                        trailing = { ChevronTrailing() },
                        onClick = { navController.navigate(Routes.STATS) }
                    )
                    ListRow(
                        icon = { Icon(Icons.Filled.Search, null, Modifier.size(18.dp)) },
                        iconBackground = MaterialTheme.semantic.infoContainer,
                        iconColor = MaterialTheme.semantic.onInfoContainer,
                        title = "题目搜索",
                        subtitle = "关键词 · 标签",
                        trailing = { ChevronTrailing() },
                        onClick = { navController.navigate(Routes.SEARCH) }
                    )
                    ListRow(
                        icon = { Icon(Icons.Filled.Backup, null, Modifier.size(18.dp)) },
                        iconBackground = MaterialTheme.colorScheme.secondaryContainer,
                        iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        title = "备份与还原",
                        subtitle = "导出 / 还原全部数据",
                        trailing = { ChevronTrailing() },
                        onClick = { navController.navigate(Routes.BACKUP) },
                        showDivider = false
                    )
                }
            }

            // 存储空间
            item {
                Spacer(Modifier.height(4.dp))
                SectionTitle("存储空间")
            }
            item {
                CardBox {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("本地数据", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                formatBytes(storageUsed),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    // 颜色图例（放在进度条前面，对应下方三段数据）
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.weight(1f)) { StorageDetail("题库", storageBreakdown.banks, MaterialTheme.colorScheme.primary) }
                        Box(Modifier.weight(1f)) { StorageDetail("记录", storageBreakdown.records, MaterialTheme.colorScheme.tertiary) }
                        Box(Modifier.weight(1f)) { StorageDetail("其他", storageBreakdown.other, MaterialTheme.colorScheme.outlineVariant) }
                    }
                    Spacer(Modifier.height(10.dp))
                    // 进度条：题库 / 记录 / 其他 三段占比（三段之和 = 真实总占用）
                    val totalBytes = (storageBreakdown.banks + storageBreakdown.records + storageBreakdown.other).toFloat().coerceAtLeast(1f)
                    Row(Modifier.fillMaxWidth().height(10.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Box(Modifier.weight((storageBreakdown.banks / totalBytes).coerceAtLeast(0.001f)).height(10.dp)
                            .clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.primary))
                        Box(Modifier.weight((storageBreakdown.records / totalBytes).coerceAtLeast(0.001f)).height(10.dp)
                            .clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.tertiary))
                        Box(Modifier.weight((storageBreakdown.other / totalBytes).coerceAtLeast(0.001f)).height(10.dp)
                            .clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.outlineVariant))
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "数据保存在本机，可在「备份还原」中导出备份",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 偏好设置
            item {
                Spacer(Modifier.height(4.dp))
                SectionTitle("偏好设置")
            }
            item {
                Column(Modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface)) {
                    ListRow(
                        icon = { Icon(Icons.Filled.DarkMode, null, Modifier.size(18.dp)) },
                        iconBackground = MaterialTheme.colorScheme.surfaceContainer,
                        iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        title = "主题",
                        subtitle = when (repo.themeMode) {
                            "light" -> "浅色"
                            "dark" -> "深色"
                            else -> "跟随系统"
                        },
                        trailing = {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        },
                        onClick = { showThemeDialog = true }
                    )
                    ListRow(
                        icon = { Icon(Icons.Filled.Palette, null, Modifier.size(18.dp)) },
                        iconBackground = MaterialTheme.colorScheme.surfaceContainer,
                        iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        title = "动态取色",
                        subtitle = "从壁纸提取主题色，正确/错误色保持稳定",
                        trailing = {
                            ShuatiSwitch(
                                checked = repo.dynamicColor,
                                onCheckedChange = { repo.saveDynamicColor(it) }
                            )
                        }
                    )
                    ListRow(
                        icon = { Icon(Icons.Filled.Notifications, null, Modifier.size(18.dp)) },
                        iconBackground = MaterialTheme.colorScheme.surfaceContainer,
                        iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        title = "每日提醒",
                        subtitle = "每天 21:00 提醒打卡",
                        trailing = { ShuatiSwitch(checked = remindOn, onCheckedChange = { remindOn = it }) }
                    )
                }
            }

            // AI 解析
            item {
                Spacer(Modifier.height(4.dp))
                SectionTitle("AI 解析")
            }
            item {
                Column(Modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface)) {
                    ListRow(
                        icon = { Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp)) },
                        iconBackground = MaterialTheme.colorScheme.surfaceContainer,
                        iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        title = "AI 大模型配置",
                        subtitle = "配置模型与联网搜索，生成题目解析",
                        trailing = { ChevronTrailing() },
                        onClick = { navController.navigate(Routes.AI_SETTINGS) }
                    )
                }
            }

            // 关于
            item {
                Spacer(Modifier.height(4.dp))
                SectionTitle("关于")
            }
            item {
                Column(Modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface)) {
                    ListRow(
                        icon = { Icon(Icons.AutoMirrored.Filled.HelpOutline, null, Modifier.size(18.dp)) },
                        iconBackground = MaterialTheme.colorScheme.surfaceContainer,
                        iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        title = "使用帮助",
                        subtitle = "导入题库 · 答题 · 错题复习",
                        trailing = { ChevronTrailing() },
                        onClick = { showHelpDialog = true }
                    )
                    ListRow(
                        icon = { Icon(Icons.Filled.Info, null, Modifier.size(18.dp)) },
                        iconBackground = MaterialTheme.colorScheme.surfaceContainer,
                        iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        title = "版本",
                        subtitle = "v$version · build $buildNumber",
                        showDivider = false
                    )
                }
            }
        }
    }

    // 使用帮助
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text("使用帮助") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(
                        "📥 导入题库" to "题库页 → 导入题库，选择 Excel 文件即可",
                        "✏️ 开始刷题" to "题库行点「刷题」，支持顺序 / 随机 / 浏览",
                        "🔁 错题复习" to "答错自动进错题本，连对 3 次自动移除",
                        "⭐ 收藏题目" to "答题时点亮星标，可批量练习",
                        "🗓 学习计划" to "设置每日目标，打卡养成习惯",
                        "💾 备份数据" to "我的 → 备份与还原，导出 JSON 防丢失"
                    ).forEach { (t, d) ->
                        Column {
                            Text(t, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(d, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showHelpDialog = false }) { Text("知道了") } }
        )
    }

    // 主题模式选择
    if (showThemeDialog) {
        var selected by remember { mutableStateOf(repo.themeMode) }
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("选择主题") },
            text = {
                Column {
                    listOf(
                        "system" to "跟随系统",
                        "light" to "浅色",
                        "dark" to "深色"
                    ).forEach { (value, label) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selected = value }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selected == value, onClick = { selected = value })
                            Spacer(Modifier.width(6.dp))
                            Text(label, fontSize = 15.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    repo.saveThemeMode(selected)
                    showThemeDialog = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("取消") }
            }
        )
    }
}

// ─────────────────────────── 通用私有组件 ───────────────────────────

/** 存储明细小项（须在 Row/Column 内调用，weight 由父级 Row 分配） */
@Composable
private fun StorageDetail(label: String, bytes: Long, color: Color) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(5.dp))
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(2.dp))
        Text(formatBytes(bytes), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface)
    }
}

/** 汇总环 */
@Composable
private fun RingSummary(progress: Float, label: String) {
    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.tertiary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            strokeWidth = 5.dp
        )
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** 快捷入口瓦片 */
@Composable
private fun QuickTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    tileColor: Color,
    iconColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tileColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column {
            Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 字节数格式化为可读字符串 */
private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024L -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}
