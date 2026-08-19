package com.example.shuati.ui.pages

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.shuati.ui.*
import com.example.shuati.ui.components.CardBox
import com.example.shuati.ui.components.ScreenTopBar
import kotlin.math.roundToInt

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
    durationSec: Int
) {
    val accuracy = if (total > 0) (correct * 100f / total).roundToInt() else 0

    Scaffold(topBar = { ScreenTopBar("答题结果") }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                "$accuracy%",
                fontSize = 56.sp, fontWeight = FontWeight.Bold,
                color = when {
                    accuracy >= 80 -> MaterialTheme.colorScheme.primary
                    accuracy >= 60 -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.error
                }
            )
            Text("本次作答 $total 题　正确 $correct　错误 $wrong",
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            CardBox(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ResultStat("正确", correct.toString(), MaterialTheme.colorScheme.primary)
                    ResultStat("错误", wrong.toString(), MaterialTheme.colorScheme.error)
                    ResultStat("用时", "${durationSec}s", MaterialTheme.colorScheme.tertiary)
                }
            }

            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        // 再来一次：重置进度（断点模式则清空 session）
                        if (source == "bank" && mode == "continue" && bankId != null) {
                            // 继续模式不清 everCompleted，仅清空本次 session，从头开始
                        }
                        navController.navigate(
                            "${Routes.QUIZ}?bankId=$bankId&mode=$mode&source=$source&planId=$planId"
                        ) { popUpTo(Routes.RESULT) { inclusive = true } }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Refresh, null)
                    Spacer(Modifier.width(8.dp))
                    Text("再来一次")
                }
                OutlinedButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("返回上一页") }
                OutlinedButton(
                    onClick = { navController.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } } },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("回到首页") }
            }
        }
    }
}

@Composable
private fun ResultStat(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = color)
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
