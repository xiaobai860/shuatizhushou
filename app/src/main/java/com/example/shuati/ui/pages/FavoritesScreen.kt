package com.example.shuati.ui.pages

import androidx.compose.foundation.clickable
import com.example.shuati.ui.theme.semantic
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import kotlinx.coroutines.CoroutineScope
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.shuati.data.AppRepository
import com.example.shuati.ui.repository
import com.example.shuati.model.QuestionBank
import com.example.shuati.model.FavoriteRecord
import com.example.shuati.model.Question
import com.example.shuati.ui.Routes
import com.example.shuati.ui.components.ScreenTopBar
import com.example.shuati.ui.components.TagChip
import com.example.shuati.ui.components.formatAnswerWithLabel
import com.example.shuati.ui.components.typeLabel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(navController: NavController) {
    val repo = repository()
    val favorites = remember { repo.getFavoriteRecords() }
    val banks = remember { repo.getBanks() }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 题库分类标签
    val allBankIds = favorites.map { it.bankId }.distinct()
    var selectedBankId by remember { mutableStateOf<String?>(null) }

    val filtered = if (selectedBankId != null) {
        favorites.filter { it.bankId == selectedBankId }
    } else {
        favorites
    }

    // 失效收藏检测（收藏的题目所在题库已被删除）
    val validBankIds = banks.map { it.id }.toSet() + setOf("__all__")
    val invalidFavorites = filtered.filter { f ->
        f.bankId !in validBankIds || banks.find { b -> b.id == f.bankId }?.questions?.none { q -> q.id == f.questionId } == true
    }

    Scaffold(

        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = {
            ScreenTopBar(
                "收藏",
                onBack = { navController.popBackStack() },
                subtitle = "共 ${filtered.size} 题 · 想反复看的题都在这里",
                actions = {
                    if (invalidFavorites.isNotEmpty()) {
                        IconButton(onClick = {
                            scope.launch {
                                invalidFavorites.forEach { repo.removeFavorite(it.bankId, it.questionId) }
                                snackbarHostState.showSnackbar("已清除 ${invalidFavorites.size} 条失效收藏")
                            }
                        }) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = "清除失效收藏")
                        }
                    }
                    IconButton(onClick = { navController.navigate(Routes.STATS) }) {
                        Icon(Icons.Filled.BarChart, contentDescription = "统计")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // 题库筛选横排 chip 栏
            if (allBankIds.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = selectedBankId == null,
                        onClick = { selectedBankId = null },
                        label = { Text("全部", fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(Icons.Filled.AllInclusive, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    )
                    allBankIds.forEach { bankId ->
                        val bankName = banks.find { b -> b.id == bankId }?.name ?: bankId
                        FilterChip(
                            selected = selectedBankId == bankId,
                            onClick = { selectedBankId = if (selectedBankId == bankId) null else bankId },
                            label = { Text(bankName.take(8), fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(Icons.Filled.Book, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        )
                    }
                }
            }

            // 失效收藏提示
            if (invalidFavorites.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.semantic.warningContainer,
                    shape = RoundedCornerShape(0.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.semantic.warning, modifier = Modifier.size(18.dp))
                        Text(
                            "发现 ${invalidFavorites.size} 条失效收藏（题库已删除或题目不存在）",
                            fontSize = 13.sp,
                            color = MaterialTheme.semantic.onWarningContainer
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            onClick = {
                                scope.launch {
                                    invalidFavorites.forEach { repo.removeFavorite(it.bankId, it.questionId) }
                                    snackbarHostState.showSnackbar("已清除 ${invalidFavorites.size} 条失效收藏")
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("一键清除", fontSize = 12.sp)
                        }
                    }
                }
            }

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无收藏记录，去刷题时点击⭐收藏吧！", textAlign = TextAlign.Center)
                }
            } else {
                val listState = rememberLazyListState()
                // 批量练习（跨题库使用 source=favorite + questionIds）
                Card(
                    onClick = {
                        val ids = filtered.joinToString(",") { "${it.bankId}::${it.questionId}" }
                        navController.navigate("${Routes.QUIZ}?mode=sequence&source=favorite&questionIds=$ids&showModeSelect=true")
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.semantic.warningContainer)
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.PlayArrow, null, tint = MaterialTheme.semantic.warning, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("批量练习", fontSize = 14.5.sp, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.semantic.onWarningContainer)
                            Text("按当前筛选题库出题 · ${filtered.size} 题", fontSize = 11.sp,
                                color = MaterialTheme.semantic.onWarningContainer.copy(alpha = 0.8f))
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.semantic.warning)
                    }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filtered, key = { "${it.bankId}:${it.questionId}" }) { record ->
                        FavoriteCard(record, banks, repo, navController, snackbarHostState, scope)
                    }
                }
            }
        }
    }
}

@Composable
fun FavoriteCard(
    record: FavoriteRecord,
    banks: List<QuestionBank>,
    repo: AppRepository,
    navController: NavController,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope
) {
    val bankName = banks.find { b -> b.id == record.bankId }?.name ?: record.bankName
    val question = record.question ?: banks.find { b -> b.id == record.bankId }?.questions?.firstOrNull { q -> q.id == record.questionId }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                navController.navigate("${Routes.EDIT_BANK}?bankId=${record.bankId}&editQuestionId=${record.questionId}")
            },
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = bankName,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatTimestamp(record.createdAt),
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            if (question != null) {
                QuestionCard(question, showAnswer = true)
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        text = "题目内容已不存在",
                        modifier = Modifier.padding(12.dp),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    question?.let {
                        Text(
                            typeLabel(it.type),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    question?.tags?.forEach { tag ->
                        TagChip(tag)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // 单题练习（直接顺序作答）
                    TextButton(onClick = {
                        navController.navigate("${Routes.QUIZ}?mode=sequence&source=favorite&questionIds=${record.bankId}::${record.questionId}")
                    }) { Text("练习", fontSize = 13.sp) }
                    // 删除收藏按钮
                    IconButton(
                        onClick = {
                            scope.launch {
                                repo.removeFavorite(record.bankId, record.questionId)
                                snackbarHostState.showSnackbar("已移除收藏")
                            }
                        }
                    ) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = "取消收藏",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

// 带答案展示的 QuestionCard
@Composable
fun QuestionCard(question: Question, showAnswer: Boolean = false) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = question.question,
                fontSize = 14.sp,
                lineHeight = 22.sp
            )

            if (!question.options.isNullOrEmpty()) {
                Spacer(Modifier.height(8.dp))
                question.options.forEachIndexed { index, option ->
                    val letter = (index + 1).toString()
                    val content = option
                    val isCorrect = showAnswer && question.answer?.contains(letter.uppercase()) == true

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$letter. ",
                            fontSize = 13.sp,
                            fontWeight = if (isCorrect) FontWeight.Bold else FontWeight.Normal,
                            color = if (isCorrect) MaterialTheme.semantic.success else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = content,
                            fontSize = 13.sp,
                            color = if (isCorrect) MaterialTheme.semantic.success else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (isCorrect) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.semantic.success, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            if (showAnswer && !question.answer.isNullOrEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "答案：${formatAnswerWithLabel(question)}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

fun formatTimestamp(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}
