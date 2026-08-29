package com.example.shuati.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
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
import com.example.shuati.ui.components.Badge
import com.example.shuati.ui.components.CardBox
import com.example.shuati.ui.components.ChevronTrailing
import com.example.shuati.ui.components.EmptyState
import com.example.shuati.ui.components.HeroCard
import com.example.shuati.ui.components.ListRow
import com.example.shuati.ui.components.SectionTitle
import com.example.shuati.ui.components.SoftButton
import com.example.shuati.ui.components.StatTile
import com.example.shuati.ui.components.ThinProgress
import com.example.shuati.ui.theme.ShuatiSpacing
import com.example.shuati.ui.theme.semantic

// ─────────────────────────── 题库中心 ───────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryTabScreen(navController: NavController) {
    val repo = repository()
    var banks by remember { mutableStateOf(emptyList<QuestionBank>()) }
    LaunchedEffect(Unit) { banks = repo.getBanks() }
    val totalQuestions = banks.sumOf { it.questions.size }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("题库", fontSize = 19.sp, fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { navController.navigate(Routes.SEARCH) }) {
                        Icon(Icons.Filled.Search, "搜索", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
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
                                Text(
                                    "$total 题" + if (answered > 0) " · 已答 $answered" else "",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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
    LaunchedEffect(Unit) {
        wrongCount = repo.getWrongRecords().size
        favCount = repo.getFavoriteRecords().size
        historyCount = repo.getHistoryRecords().size
        planCount = repo.getStudyPlans().count { !it.archived }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("学习", fontSize = 19.sp, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MineTabScreen(navController: NavController) {
    val repo = repository()
    val ctx = LocalContext.current
    var storageUsed by remember { mutableStateOf(0L) }
    var remindOn by remember { mutableStateOf(true) }
    var reducedMotion by remember { mutableStateOf(false) }
    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull()
    }
    LaunchedEffect(Unit) { storageUsed = repo.storageUsedBytes() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("我的", fontSize = 19.sp, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
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
                                "数据保存在本机 · v$version",
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
                        Text("已用", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(10.dp))
                    ThinProgress(progress = (storageUsed / 1024.0 / 10240.0).toFloat().coerceIn(0f, 1f))
                    Spacer(Modifier.height(6.dp))
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
                        subtitle = "跟随系统，支持深色模式",
                        trailing = { Text("自动", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    )
                    ListRow(
                        icon = { Icon(Icons.Filled.Palette, null, Modifier.size(18.dp)) },
                        iconBackground = MaterialTheme.colorScheme.surfaceContainer,
                        iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        title = "动态取色",
                        subtitle = "从壁纸提取主题色，正确/错误色保持稳定",
                        trailing = {
                            Switch(
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
                        trailing = { Switch(checked = remindOn, onCheckedChange = { remindOn = it }) }
                    )
                    ListRow(
                        icon = { Icon(Icons.Filled.PlayCircleOutline, null, Modifier.size(18.dp)) },
                        iconBackground = MaterialTheme.colorScheme.surfaceContainer,
                        iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        title = "减少动效",
                        subtitle = "关闭过渡动画",
                        trailing = { Switch(checked = reducedMotion, onCheckedChange = { reducedMotion = it }) },
                        showDivider = false
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
                        icon = { Icon(Icons.Filled.Info, null, Modifier.size(18.dp)) },
                        iconBackground = MaterialTheme.colorScheme.surfaceContainer,
                        iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        title = "版本",
                        subtitle = "v$version · 柔和渐变 · 亲和活力",
                        showDivider = false
                    )
                }
            }
        }
    }
}

// ─────────────────────────── 通用私有组件 ───────────────────────────

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
