package com.example.shuati.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 圆角体系（2026 版）：
 * xs 8 / sm 12 / md 16 / lg 20 / xl 26 / 2xl 34。
 * 按钮、Chip、头像使用 full（999dp），由组件侧指定。
 * 图标容器统一 sm–md，比卡片（lg）小一档，形成「容器方一点、卡片圆一点」的层次。
 */
val ShuatiShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

/** 扩展形状常量（供组件直接引用） */
object ShuatiRadius {
    val Xs = 8.dp
    val Sm = 12.dp
    val Md = 16.dp
    val Lg = 20.dp
    val Xl = 26.dp
    val Xxl = 34.dp
    val Full = 999.dp
}
