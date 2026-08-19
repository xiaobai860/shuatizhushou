package com.example.shuati.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.shuati.data.AppRepository
import com.example.shuati.ui.repository
import com.example.shuati.ui.Routes

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
        accuracy >= 80 -> Color(0xFF4CAF50)
        accuracy >= 60 -> Color(0xFFFB8C00)
        else -> Color(0xFFEF5350)
    }

    val bankName by remember(bankId) { derivedStateOf { repo.getBanks().find { b -> b.id == (bankId ?: "__all__") }?.name ?: "全部题库" } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isBrowse) "浏览完成" else "答题结果") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surface),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 渐变英雄卡片
            Spacer(Modifier.height(24.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF1A237E).copy(alpha = 0.9f),
                                    Color(0xFF3F51B5).copy(alpha = 0.8f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (isBrowse) {
                            Text(
                                "浏览完成",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 18.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = "$total",
                                color = Color.White,
                                fontSize = 72.sp,
                                fontWeight = FontWeight.Black,
                                lineHeight = 80.sp,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Text(
                                "道题已浏览",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 14.sp
                            )
                        } else {
                            Text(
                                accuracyLabel,
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 18.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = "${accuracy}%",
                                color = Color.White,
                                fontSize = 72.sp,
                                fontWeight = FontWeight.Black,
                                lineHeight = 80.sp,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Text(
                                "正确率 · $bankName",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            if (isBrowse) {
                // 浏览模式：仅提示已完整浏览
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50))
                        Spacer(Modifier.width(8.dp))
                        Text("已完整浏览全部 $total 道题目及答案", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            } else {
                // 统计详情：总题数 / 答对 / 答错
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatCard("总题数", "$total", MaterialTheme.colorScheme.onSurface)
                    StatCard("答对", "$correct", Color(0xFF4CAF50))
                    StatCard("答错", "$wrong", Color(0xFFFF5252))
                }

                // 用时与均秒/题
                if (durationSec > 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("用时 ${formatTime(durationSec)}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        if (total > 0) {
                            Text("  ·  均${formatTime(durationSec / total)}/题", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        }
                    }
                }

                // 正确率进度条
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("正确率", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text("$accuracy%", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = accuracyColor)
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { accuracy / 100f },
                            modifier = Modifier.fillMaxWidth().height(10.dp),
                            color = accuracyColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // 按钮组
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val histBankId = if (source == "all" || bankId == "__all__") "__all__" else bankId ?: "__all__"
                        navController.navigate("${Routes.QUIZ}?bankId=$histBankId&mode=$mode&source=$source&planId=$planId")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A237E))
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("重新刷题")
                }

                if (!isBrowse) {
                    Button(
                        onClick = {
                            val histBankId = if (source == "all" || bankId == "__all__") "__all__" else bankId ?: "__all__"
                            navController.navigate("${Routes.QUIZ}?bankId=$histBankId&mode=$mode&source=$source&planId=$planId&filterUnanswered=true")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF303F9F))
                    ) {
                        Icon(Icons.Filled.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("错题重练")
                    }
                }

                OutlinedButton(
                    onClick = {
                        val histBankId = if (source == "all" || bankId == "__all__") "__all__" else bankId ?: "__all__"
                        navController.navigate("${Routes.QUIZ}?bankId=$histBankId&mode=browse&source=$source&planId=$planId")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("浏览回顾")
                }

                OutlinedButton(
                    onClick = {
                        navController.popBackStack(Routes.HOME, false)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Home, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("返回首页")
                }
            }

            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
fun StatCard(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatTime(sec: Int): String {
    if (sec < 60) return "${sec}秒"
    val m = sec / 60
    val s = sec % 60
    return if (s > 0) "${m}分${s}秒" else "${m}分钟"
}
