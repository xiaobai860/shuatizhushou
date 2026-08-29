package com.example.shuati.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 12 级字阶（sp / dp 等价）。
 * 中文行高规则：题干 ≥ 1.62（16/26），解析 ≥ 1.65（13.5/22），长题干段落间距 ≥ 8dp。
 * 数字场景配合 tabular-nums 使用，避免跳动。
 */
val ShuatiTypography = Typography(
    // Display —— 大数字
    displayLarge = TextStyle(
        fontSize = 36.sp, lineHeight = 44.sp,
        fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp
    ),
    displayMedium = TextStyle(
        fontSize = 32.sp, lineHeight = 40.sp,
        fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp
    ),
    displaySmall = TextStyle(
        fontSize = 30.sp, lineHeight = 38.sp,
        fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp
    ),
    // Headline —— 页面大标题 / Hero 主数字
    headlineLarge = TextStyle(
        fontSize = 28.sp, lineHeight = 36.sp,
        fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontSize = 24.sp, lineHeight = 32.sp,
        fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp
    ),
    headlineSmall = TextStyle(
        fontSize = 22.sp, lineHeight = 30.sp,
        fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp
    ),
    // Title
    titleLarge = TextStyle(
        fontSize = 20.sp, lineHeight = 28.sp,
        fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp, lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp
    ),
    titleSmall = TextStyle(
        fontSize = 14.sp, lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold
    ),
    // Body
    bodyLarge = TextStyle(
        fontSize = 16.sp, lineHeight = 26.sp,
        fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp, lineHeight = 22.sp,
        fontWeight = FontWeight.Medium, letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontSize = 13.sp, lineHeight = 20.sp,
        fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp
    ),
    // Label
    labelLarge = TextStyle(
        fontSize = 15.sp, lineHeight = 22.sp,
        fontWeight = FontWeight.Bold
    ),
    labelMedium = TextStyle(
        fontSize = 12.5.sp, lineHeight = 18.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp, lineHeight = 16.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp
    )
)
