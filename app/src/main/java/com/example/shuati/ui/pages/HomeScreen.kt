package com.example.shuati.ui.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.shuati.model.QuestionBank
import com.example.shuati.ui.*
import com.example.shuati.ui.components.CardBox
import com.example.shuati.ui.components.EmptyState
import com.example.shuati.ui.components.typeLabel
import kotlinx.coroutines.launch

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
        topBar = { ScreenTopBarHome() }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // 统计概览
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("题库", banks.size.toString(), Modifier.weight(1f))
                    StatCard("题目", totalQuestions.toString(), Modifier.weight(1f))
                    StatCard("错题", wrongCount.toString(), Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("今日连续", todayStreak.toString() + "天", Modifier.weight(1f))
                    StatCard("进行中计划", planCount.toString(), Modifier.weight(1f))
                    StatCard("收藏", favCount.toString(), Modifier.weight(1f))
                }
            }

            // 快捷入口
            item {
                Text("快捷入口", fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                    modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickEntry(Icons.Filled.Book, "题库管理", Modifier.weight(1f)) { navController.navigate(Routes.LIBRARY) }
                    QuickEntry(Icons.Filled.Upload, "导入题库", Modifier.weight(1f)) { navController.navigate(Routes.IMPORT) }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickEntry(Icons.Filled.Star, "智能推荐", Modifier.weight(1f)) { navController.navigate(Routes.RECOMMEND) }
                    QuickEntry(Icons.Filled.CalendarMonth, "学习计划", Modifier.weight(1f)) { navController.navigate(Routes.STUDY_PLAN) }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickEntry(Icons.Filled.BugReport, "查重检测", Modifier.weight(1f)) { navController.navigate(Routes.DEDUP) }
                    QuickEntry(Icons.Filled.Backup, "备份还原", Modifier.weight(1f)) { navController.navigate(Routes.BACKUP) }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickEntry(Icons.Filled.AutoStories, "全部题库", Modifier.weight(1f)) {
                        navController.navigate("${Routes.QUIZ}?bankId=__all__&mode=sequence&source=all")
                    }
                    QuickEntry(Icons.Filled.Visibility, "浏览模式", Modifier.weight(1f)) {
                        navController.navigate("${Routes.QUIZ}?bankId=__all__&mode=browse&source=browse")
                    }
                }
            }

            // 最近题库
            item {
                Text("最近题库", fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
            }
            if (banks.isEmpty()) {
                item { EmptyState("还没有题库，去「导入题库」添加吧") }
            } else {
                items(banks.sortedByDescending { it.updatedAt }) { bank ->
                    CardBox(onClick = { navController.navigate("${Routes.EDIT_BANK}?bankId=${bank.id}") }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(bank.name, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                                Text("${bank.questions.size} 题", fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = {
                                navController.navigate("${Routes.QUIZ}?bankId=${bank.id}&mode=sequence&source=bank")
                            }) {
                                Text("开始刷题")
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun ScreenTopBarHome() {
    TopAppBar(title = { Text("刷题", fontWeight = FontWeight.SemiBold) })
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    CardBox(modifier = modifier) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 22.sp,
            color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun QuickEntry(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    CardBox(modifier = modifier.clickable { onClick() }) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(label, fontSize = 13.sp)
        }
    }
}
