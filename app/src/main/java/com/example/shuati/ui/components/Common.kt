package com.example.shuati.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 统一页头（含返回按钮），等价小程序各页面顶部 */
@Composable
fun ScreenTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

/** 通用卡片容器 */
@Composable
fun CardBox(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

/** 空状态占位 */
@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 15.sp
        )
    }
}

/** 题型中文标签 + 颜色 */
fun typeLabel(type: com.example.shuati.model.QuestionType): String = when (type) {
    com.example.shuati.model.QuestionType.single -> "单选"
    com.example.shuati.model.QuestionType.multiple -> "多选"
    com.example.shuati.model.QuestionType.judge -> "判断"
    com.example.shuati.model.QuestionType.fill -> "填空"
    com.example.shuati.model.QuestionType.essay -> "简答"
}

/**
 * 答案展示：将答案字母映射为"字母+选项文本"（等价小程序 formatAnswerWithLabel）。
 * 单选/多选/判断 → "A 橘子、C 葡萄"；填空/简答 → 直接返回答案文本。
 * 兼容无分隔符的多字母答案（如 "AC"）及超出选项范围的字母（原样返回）。
 */
fun formatAnswerWithLabel(q: com.example.shuati.model.Question): String {
    val type = q.type
    if (type == com.example.shuati.model.QuestionType.fill ||
        type == com.example.shuati.model.QuestionType.essay
    ) {
        return q.answer ?: ""
    }
    val opts = q.options ?: emptyList()
    val mapOne: (String) -> String = { raw ->
        val letter = raw.trim().uppercase()
        val idx = letter.firstOrNull()?.code?.minus('A'.code) ?: -1
        if (idx in opts.indices && opts[idx].isNotBlank()) "$letter ${opts[idx]}" else letter
    }
    val raw = q.answer ?: ""
    val parts = raw.split(Regex("[,，|｜、\\s]+")).map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.size > 1) {
        return parts.joinToString("、") { mapOne(it) }
    }
    val single = parts.firstOrNull() ?: raw
    // 无分隔符的多字母答案（如多选 "AC"）：逐字母映射
    if (single.length > 1 && single.all { it.isLetter() }) {
        return single.map { mapOne(it.toString()) }.joinToString("、")
    }
    return mapOne(single)
}

/** 简单文本标签 Chip，可选自定义颜色 */
@Composable
fun TagChip(text: String, containerColor: Color? = null, labelColor: Color? = null) {
    Surface(
        color = containerColor ?: MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            fontSize = 12.sp,
            color = labelColor ?: MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
