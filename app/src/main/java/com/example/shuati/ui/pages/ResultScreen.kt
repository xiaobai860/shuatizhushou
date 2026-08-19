package com.example.shuati.ui.pages

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.shuati.data.AppRepository
import com.example.shuati.ui.Routes
import kotlinx.coroutines.launch

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
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val repo = remember { AppRepository(navController.context) }
    var showDialog by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    // 计算正确率（用于颜色状态）
    val accuracy = if (total > 0) correct * 100 / total else 0

    // 颜色状态
    val targetColor by animateColorAsState(
        targetValue = if (accuracy >= 60) Color(0xFF1A237E) else Color(0xFFEF6C00),
        label = "targetColor"
    )
    val currentColor by animateColorAsState(
        targetValue = if (accuracy >= 60) Color(0xFF303F9F) else Color(0xFF7B1FA2),
        label = "currentColor"
    )

    val bankName by remember(bankId) { derivedStateOf { repo.getBanks().find { b -> b.id == (bankId ?: "__all__") }?.name ?: "全部题库" } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("结果") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
                    IconButton(onClick = {
                        // 尝试打开系统设置
                        try {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
                            navController.context.startActivity(intent)
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(navController.context, "无法打开设置", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Filled.Settings, contentDescription = "系统设置")
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
                        Text(
                            "答题结果",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 14.sp,
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
                            "正确率",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // 统计卡片
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatCard("正确", "$correct", Color(0xFF4CAF50))
                StatCard("错误", "$wrong", Color(0xFFFF5252))
                StatCard("用时", "${durationSec}s", Color(0xFF2196F3))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("本次共答 $total 题", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Text("目标正确率 60%", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }

            // 达标提示
            if (total > 0 && accuracy >= 60) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    color = Color(0xFFE8F5E9),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("✓", fontSize = 20.sp, color = Color(0xFF4CAF50), modifier = Modifier.padding(end = 8.dp))
                        Text("恭喜你达到目标正确率！", fontSize = 14.sp, color = Color(0xFF2E7D32))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

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
                    colors = ButtonDefaults.buttonColors(containerColor = targetColor)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("继续练习")
                }

                Button(
                    onClick = {
                        val histBankId = if (source == "all" || bankId == "__all__") "__all__" else bankId ?: "__all__"
                        navController.navigate("${Routes.QUIZ}?bankId=$histBankId&mode=$mode&source=$source&planId=$planId&filterUnanswered=true")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = currentColor)
                ) {
                    Icon(Icons.Filled.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("错题重练")
                }

                OutlinedButton(
                    onClick = {
                        val histBankId = if (source == "all" || bankId == "__all__") "__all__" else bankId ?: "__all__"
                        navController.navigate("${Routes.QUIZ}?bankId=$histBankId&mode=browse&source=$source&planId=$planId")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("浏览回顾")
                }
            }

            Spacer(Modifier.weight(1f))
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { /* Ignore */ },
            title = { Text("设置") },
            text = { Text("设置功能开发中...") },
            confirmButton = {
                TextButton(onClick = { /* Ignore */ }) { Text("确定") }
            }
        )
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("选择操作") },
            text = { Text("请选择要执行的操作") },
            confirmButton = {
                TextButton(onClick = { showSettings = false }) { Text("取消") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSettings = false
                    navController.navigate(Routes.STATS)
                }) { Text("统计") }
            }
        )
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
