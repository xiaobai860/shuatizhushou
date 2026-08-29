package com.example.shuati.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Visibility
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
import com.example.shuati.ui.Routes
import com.example.shuati.ui.components.CardBox
import com.example.shuati.ui.components.HeroCard
import com.example.shuati.ui.components.RingProgress
import com.example.shuati.ui.components.SoftButton
import com.example.shuati.ui.components.SoftButtonVariant
import com.example.shuati.ui.components.StatTile
import com.example.shuati.ui.components.ThinProgress
import com.example.shuati.ui.repository
import com.example.shuati.ui.theme.ShuatiSpacing
import com.example.shuati.ui.theme.semantic

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    navController: NavController,
    bankId: String?,
    mode: String,
    source: String,
    planId: String?,
    correct: Int,
    wrong: Int,
    total: Int,
    durationSec: Int,
) {
    val repo = repository()
    val isBrowse = mode == "browse"

    // 计算正确率
    val accuracy = if (total > 0) correct * 100 / total else 0

    // 个性化标签与颜色（等价小程序 getAccuracyLabel/getAccuracyColor）
    val accuracyLabel = when {
        accuracy >= 90 -> "太棒了！"
        accuracy >= 80 -> "表现不错"
        accuracy >= 60 -> "继续加油"
        else -> "需要复习"
    }
    val accuracyColor = when {
        accuracy >= 80 -> MaterialTheme.semantic.success
        accuracy >= 60 -> MaterialTheme.semantic.warning
        else -> MaterialTheme.semantic.error
    }

    val bankName by remember(bankId) {
        derivedStateOf { repo.getBanks().find { b -> b.id == (bankId ?: "__all__") }?.name ?: "全部题库" }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (isBrowse) "浏览完成" else "答题结果", fontSize = 19.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ShuatiSpacing.PageHorizontal),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))

            // Hero：大正确率环 + 标签
            HeroCard {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isBrowse) {
                        Text(
                            "浏览完成",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "$total",
                            fontSize = 64.sp,
                            fontWeight = FontWeight.Black,
                            lineHeight = 72.sp,
                            letterSpacing = (-2).sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "道题已浏览",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                    } else {
                        RingProgress(
                            progress = accuracy / 100f,
                            size = 132.dp,
                            strokeWidth = 10.dp,
                            color = accuracyColor,
                            label = "$accuracy%"
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            accuracyLabel,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.3).sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "正确率 · $bankName",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (isBrowse) {
                Spacer(Modifier.height(16.dp))
                CardBox {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.semantic.success
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "已完整浏览全部 $total 道题目及答案",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            } else {
                // 统计网格
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    StatTile("$total", "总题数", Modifier.weight(1f))
                    StatTile("$correct", "答对", Modifier.weight(1f), valueColor = MaterialTheme.semantic.success)
                    StatTile("$wrong", "答错", Modifier.weight(1f), valueColor = MaterialTheme.semantic.error)
                }
                Spacer(Modifier.height(9.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    StatTile(formatTime(durationSec), "总用时", Modifier.weight(1f))
                    StatTile(if (total > 0) "${formatTime(durationSec / total)}/题" else "—", "均每题", Modifier.weight(1f))
                    StatTile(accuracy.toString(), "正确率", Modifier.weight(1f), valueColor = accuracyColor)
                }

                // 正确率进度条
                Spacer(Modifier.height(16.dp))
                CardBox {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("正确率", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("$accuracy%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = accuracyColor)
                    }
                    Spacer(Modifier.height(10.dp))
                    ThinProgress(progress = accuracy / 100f, height = 10.dp, color = accuracyColor)
                }
            }

            // 按钮组
            Spacer(Modifier.height(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val histBankId = if (source == "all" || bankId == "__all__") "__all__" else bankId ?: "__all__"
                SoftButton(
                    text = "重新刷题",
                    icon = Icons.Filled.PlayArrow,
                    onClick = {
                        navController.navigate("${Routes.QUIZ}?bankId=$histBankId&mode=$mode&source=$source&planId=$planId")
                    }
                )
                if (!isBrowse) {
                    SoftButton(
                        text = "错题重练",
                        icon = Icons.Filled.Replay,
                        variant = SoftButtonVariant.Tonal,
                        onClick = {
                            navController.navigate("${Routes.QUIZ}?bankId=$histBankId&mode=$mode&source=$source&planId=$planId&filterUnanswered=true")
                        }
                    )
                }
                SoftButton(
                    text = "浏览回顾",
                    icon = Icons.Filled.Visibility,
                    variant = SoftButtonVariant.Outlined,
                    onClick = {
                        navController.navigate("${Routes.QUIZ}?bankId=$histBankId&mode=browse&source=$source&planId=$planId")
                    }
                )
                SoftButton(
                    text = "返回首页",
                    icon = Icons.Filled.Home,
                    variant = SoftButtonVariant.Text,
                    onClick = { navController.popBackStack(Routes.HOME, false) }
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun formatTime(sec: Int): String {
    if (sec < 60) return "${sec}秒"
    val m = sec / 60
    val s = sec % 60
    return if (s > 0) "${m}分${s}秒" else "${m}分钟"
}
