package com.example.shuati.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.shuati.ui.theme.ShuatiElevation
import com.example.shuati.ui.theme.ShuatiRadius
import com.example.shuati.ui.theme.ShuatiSpacing

// ===========================================================================
// 顶部栏
// ===========================================================================

/** 统一页头（含返回按钮），支持副标题 */
@Composable
fun ScreenTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Column {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 19.sp, letterSpacing = (-0.3).sp)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
    )
}

// ===========================================================================
// 区块标题
// ===========================================================================

/** 区块标题：标题 + 可选计数 + 可选动作 */
@Composable
fun SectionTitle(
    title: String,
    count: String? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            fontSize = 15.5.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            letterSpacing = (-0.2).sp
        )
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                count,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.weight(1f))
        if (action != null) {
            Text(
                action,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = onAction != null) { onAction?.invoke() }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }
    }
}

// ===========================================================================
// 卡片
// ===========================================================================

/** 标准卡片：圆角 20，surface，e1 阴影，无边框 */
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
        shape = RoundedCornerShape(ShuatiRadius.Lg),
        elevation = CardDefaults.cardElevation(defaultElevation = ShuatiElevation.Card),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(ShuatiSpacing.CardPadding), content = content)
    }
}

/** Hero 渐变卡：由三个 container 色派生，自动适配主题与动态取色 */
@Composable
fun HeroCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ShuatiRadius.Xl))
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.tertiaryContainer
                    )
                )
            )
    ) {
        // 右上角柔光
        Box(
            Modifier
                .size(160.dp)
                .align(Alignment.TopEnd)
                .offset(x = 50.dp, y = (-70).dp)
                .background(Color.White.copy(alpha = 0.28f), CircleShape)
        )
        Column(modifier = Modifier.padding(ShuatiSpacing.CardPadding), content = content)
    }
}

// ===========================================================================
// 列表
// ===========================================================================

/**
 * 列表行：图标容器 + 标题/副标题 + 右侧内容。带分隔线（左侧缩进 14dp）。
 */
@Composable
fun ListRow(
    modifier: Modifier = Modifier,
    icon: @Composable (() -> Unit)? = null,
    title: String,
    subtitle: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = true,
    iconBackground: Color = MaterialTheme.colorScheme.surfaceContainer,
    iconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
                .padding(
                    horizontal = ShuatiSpacing.ListRowHorizontal,
                    vertical = ShuatiSpacing.ListRowVertical
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(iconBackground),
                    contentAlignment = Alignment.Center
                ) {
                    CompositionLocalProvider(LocalContentColor provides iconColor) { icon() }
                }
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        subtitle,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = ShuatiSpacing.ListRowHorizontal),
                color = MaterialTheme.colorScheme.outline,
                thickness = 0.7.dp
            )
        }
    }
}

/** 数值 + chevron 的标准尾部 */
@Composable
fun ChevronTrailing(
    value: String? = null,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    if (value != null) {
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = valueColor)
        Spacer(Modifier.width(2.dp))
    }
    Icon(
        Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(18.dp)
    )
}

// ===========================================================================
// Chip / 标签 / 徽章
// ===========================================================================

/** 筛选 Chip：选中用 secondaryContainer */
@Composable
fun FilterChip2(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                       else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = CircleShape,
        onClick = onClick
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** 题型/小标签（列表内嵌） */
@Composable
fun TagChip(text: String, containerColor: Color? = null, labelColor: Color? = null) {
    Surface(
        color = containerColor ?: MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = labelColor ?: MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

/** 徽章：重/错/已刷完 等状态 */
@Composable
fun Badge(
    text: String,
    color: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Surface(color = color, shape = RoundedCornerShape(7.dp)) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

// ===========================================================================
// 进度
// ===========================================================================

/** 线性进度条：高 6dp（细版 4dp） */
@Composable
fun ThinProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(700),
        label = "progress"
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated)
                .clip(CircleShape)
                .background(color)
        )
    }
}

/** 环形进度：直径 size，中心显示百分比 */
@Composable
fun RingProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    strokeWidth: Dp = 6.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    label: String? = null
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(600),
        label = "ring"
    )
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { animated },
            modifier = Modifier.fillMaxSize(),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            strokeWidth = strokeWidth
        )
        if (label != null) {
            Text(
                label,
                fontSize = (size.value / 4.2f).sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// ===========================================================================
// 统计块 / 空状态
// ===========================================================================

/** 统计块：数值 + 标签 */
@Composable
fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 12.dp, horizontal = 10.dp)
    ) {
        Text(
            value,
            fontSize = 19.sp,
            fontWeight = FontWeight.ExtraBold,
            color = valueColor,
            letterSpacing = (-0.6).sp,
            maxLines = 1
        )
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/** 空状态（兼容旧调用） */
@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )
    }
}

/** 空状态（增强版：图标 + 标题 + 描述 + 动作） */
@Composable
fun EmptyStateCard(
    title: String,
    description: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 26.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.secondaryContainer,
                            MaterialTheme.colorScheme.tertiaryContainer
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(title, fontSize = 15.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(6.dp))
        Text(
            description,
            fontSize = 13.sp,
            lineHeight = 21.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionText != null) {
            Spacer(Modifier.height(14.dp))
            SoftButton(text = actionText, variant = SoftButtonVariant.Outlined, compact = true, onClick = { onAction?.invoke() })
        }
    }
}

// ===========================================================================
// 按钮
// ===========================================================================

enum class SoftButtonVariant { Primary, Tonal, Outlined, Text, Disabled }

/**
 * 主行动按钮：Primary 唯一实心（主色）；其余降级 tonal / outlined / text。
 * 高 50dp（compact 42dp），圆角 full。
 */
@Composable
fun SoftButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: SoftButtonVariant = SoftButtonVariant.Primary,
    compact: Boolean = false,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    val height = if (compact) 42.dp else 50.dp
    val shape = CircleShape
    val label: @Composable RowScope.() -> Unit = {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(text, fontSize = if (compact) 13.5.sp else 15.sp, fontWeight = FontWeight.Bold)
    }
    when (variant) {
        SoftButtonVariant.Primary -> Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.height(height),
            shape = shape,
            contentPadding = PaddingValues(horizontal = 20.dp)
        ) { label() }

        SoftButtonVariant.Tonal -> Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.height(height),
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ),
            contentPadding = PaddingValues(horizontal = 20.dp)
        ) { label() }

        SoftButtonVariant.Outlined -> OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.height(height),
            shape = shape,
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            contentPadding = PaddingValues(horizontal = 20.dp)
        ) { label() }

        SoftButtonVariant.Text -> TextButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.height(height),
            shape = shape,
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.primary
            ),
            contentPadding = PaddingValues(horizontal = 20.dp)
        ) { label() }

        SoftButtonVariant.Disabled -> Button(
            onClick = {},
            enabled = false,
            modifier = modifier.height(height),
            shape = shape,
            contentPadding = PaddingValues(horizontal = 20.dp)
        ) { label() }
    }
}

// ===========================================================================
// 题型标签工具
// ===========================================================================

/** 题型中文标签 */
fun typeLabel(type: com.example.shuati.model.QuestionType): String = when (type) {
    com.example.shuati.model.QuestionType.single -> "单选"
    com.example.shuati.model.QuestionType.multiple -> "多选"
    com.example.shuati.model.QuestionType.judge -> "判断"
    com.example.shuati.model.QuestionType.fill -> "填空"
    com.example.shuati.model.QuestionType.essay -> "简答"
}

/**
 * 答案展示：将答案字母映射为"字母+选项文本"（等价小程序 formatAnswerWithLabel）。
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
    if (single.length > 1 && single.all { it.isLetter() }) {
        return single.map { mapOne(it.toString()) }.joinToString("、")
    }
    return mapOne(single)
}
