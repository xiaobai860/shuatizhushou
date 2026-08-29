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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.shuati.model.QuestionBank
import com.example.shuati.ui.*
import com.example.shuati.ui.components.Badge
import com.example.shuati.ui.components.CardBox
import com.example.shuati.ui.components.EmptyState
import com.example.shuati.ui.components.HeroCard
import com.example.shuati.ui.components.ListRow
import com.example.shuati.ui.components.SectionTitle
import com.example.shuati.ui.components.SoftButton
import com.example.shuati.ui.components.StatTile
import com.example.shuati.ui.components.ThinProgress
import com.example.shuati.ui.theme.ShuatiSpacing
import com.example.shuati.ui.theme.semantic

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    val repo = repository()
    var banks by remember { mutableStateOf(emptyList<QuestionBank>()) }
    var totalQuestions by remember { mutableStateOf(0) }
    var wrongCount by remember { mutableStateOf(0) }
    var favCount by remember { mutableStateOf(0) }
    var planCount by remember { mutableStateOf(0) }
    var todayStreak by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        val bs = repo.getBanks()
        banks = bs
        totalQuestions = bs.sumOf { it.questions.size }
        wrongCount = repo.getWrongRecords().size
        favCount = repo.getFavoriteRecords().size
        planCount = repo.getStudyPlans().count { !it.archived }
        todayStreak = repo.getTodayStreak()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { HomeTopBar(streak = todayStreak) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = ShuatiSpacing.PageHorizontal),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
        ) {
            // 今日目标 Hero
            item {
                HeroCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "已连续打卡 $todayStreak 天",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(5.dp))
                            Text(
                                "今天还没开始",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.5).sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                if (wrongCount > 0) "有 $wrongCount 道错题等着再看一眼" else "去刷一组题，错的会自动进错题本",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(14.dp))
                            SoftButton(
                                text = "继续刷题",
                                compact = true,
                                modifier = Modifier.width(120.dp),
                                onClick = {
                                    navController.navigate("${Routes.QUIZ}?bankId=__all__&mode=sequence&source=all&showModeSelect=true")
                                }
                            )
                        }
                        RingLabel(progress = if (todayStreak > 0) (todayStreak % 100) / 100f else 0f, streak = todayStreak)
                    }
                }
            }

            // 数据总览
            item {
                Spacer(Modifier.height(6.dp))
                SectionTitle("数据总览")
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    StatTile(banks.size.toString(), "题库数", Modifier.weight(1f), valueColor = MaterialTheme.colorScheme.primary)
                    StatTile(totalQuestions.toString(), "题目总数", Modifier.weight(1f))
                    StatTile(wrongCount.toString(), "错题", Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    StatTile("$todayStreak 天", "连续天数", Modifier.weight(1f), valueColor = MaterialTheme.colorScheme.tertiary)
                    StatTile(planCount.toString(), "进行中计划", Modifier.weight(1f))
                    StatTile(favCount.toString(), "收藏", Modifier.weight(1f))
                }
            }

            // 快速开始
            item {
                Spacer(Modifier.height(6.dp))
                SectionTitle("快速开始")
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickTile(Icons.Filled.AutoStories, "全部题库", "1,284 题", Modifier.weight(1f)) {
                        navController.navigate("${Routes.QUIZ}?bankId=__all__&mode=sequence&source=all&showModeSelect=true")
                    }
                    QuickTile(Icons.Filled.Visibility, "浏览模式", "直接看答案", Modifier.weight(1f)) {
                        navController.navigate("${Routes.QUIZ}?bankId=__all__&mode=browse&source=browse")
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickTile(
                        Icons.Filled.Refresh, "错题回顾", "$wrongCount 题待巩固",
                        Modifier.weight(1f),
                        tileColor = MaterialTheme.semantic.errorContainer,
                        iconColor = MaterialTheme.semantic.onErrorContainer
                    ) { navController.navigate(Routes.WRONGS) }
                    QuickTile(
                        Icons.Filled.Flag, "学习计划", "$planCount 个进行中",
                        Modifier.weight(1f),
                        tileColor = MaterialTheme.colorScheme.secondaryContainer,
                        iconColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) { navController.navigate(Routes.STUDY_PLAN) }
                }
            }

            // 最近题库
            item {
                Spacer(Modifier.height(6.dp))
                SectionTitle("最近题库", action = "全部", onAction = { navController.navigate(Routes.TAB_LIBRARY) })
            }
            if (banks.isEmpty()) {
                item { EmptyState("还没有题库，去「导入题库」添加吧") }
            } else {
                items(banks.sortedByDescending { it.updatedAt }.take(3)) { bank ->
                    val progress = repo.getProgress(bank.id)
                    val answered = progress?.answeredIds?.size ?: 0
                    val total = bank.questions.size
                    val pct = if (total > 0) (answered.toFloat() / total).coerceIn(0f, 1f) else 0f
                    val completed = progress?.everCompleted ?: false

                    CardBox(onClick = { navController.navigate("${Routes.EDIT_BANK}?bankId=${bank.id}") }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(bank.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    "$total 题" + if (answered > 0) " · 已答 $answered" else "",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (completed) {
                                Badge(
                                    "已刷完",
                                    color = MaterialTheme.semantic.successContainer,
                                    textColor = MaterialTheme.semantic.onSuccessContainer
                                )
                            } else {
                                TextButton(onClick = {
                                    navController.navigate("${Routes.QUIZ}?bankId=${bank.id}&mode=sequence&source=bank&showModeSelect=true")
                                }) {
                                    Text("开始刷题", fontSize = 13.sp)
                                }
                            }
                        }
                        if (total > 0) {
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ThinProgress(progress = pct, modifier = Modifier.weight(1f), height = 4.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "${(pct * 100).toInt()}%",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 顶部问候栏：问候 + 连续天数 + 头像 */
@Composable
private fun HomeTopBar(streak: Int) {
    TopAppBar(
        title = {
            Column {
                Text("下午好", fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
                Text(
                    if (streak > 0) "已连续打卡 $streak 天" else "今天也来刷几道吧",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        actions = {
            IconButton(onClick = { /* 通知预留 */ }) {
                Icon(Icons.Filled.Notifications, contentDescription = "通知", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(
                Modifier
                    .padding(end = 12.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "我",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
    )
}

/** Hero 右侧环形打卡标 */
@Composable
private fun RingLabel(progress: Float, streak: Int) {
    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            strokeWidth = 6.dp
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$streak", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
            Text("天", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 快速开始瓦片 */
@Composable
private fun QuickTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    tileColor: Color = MaterialTheme.colorScheme.primaryContainer,
    iconColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
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
