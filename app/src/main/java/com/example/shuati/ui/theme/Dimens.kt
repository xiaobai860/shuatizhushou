package com.example.shuati.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 间距体系（4dp 基准）：2 · 4 · 6 · 8 · 12 · 16 · 20 · 24 · 32 · 40 · 48 · 64。
 */
object ShuatiSpacing {
    val Xs = 4.dp
    val Sm = 8.dp
    val Md = 12.dp
    val Lg = 16.dp
    val Xl = 20.dp
    val Xxl = 24.dp
    val Xxxl = 32.dp

    /** 页面左右安全边距 */
    val PageHorizontal = 18.dp

    /** 卡片内边距 */
    val CardPadding = 16.dp

    /** 区块（Section）上间距 */
    val SectionTop = 24.dp

    /** 区块标题与内容间距 */
    val SectionTitleGap = 11.dp

    /** 同组卡片间距 */
    val CardGap = 10.dp

    /** 列表项内边距：横 14 / 纵 13 */
    val ListRowHorizontal = 14.dp
    val ListRowVertical = 13.dp

    /** 选项间距 */
    val OptionGap = 9.dp
}

/** 阴影层级：暖调棕阴影（浅色），深色改用顶部高光表达层级。 */
object ShuatiElevation {
    /** 卡片 e1 */
    val Card = Dp(2f)
    /** 悬浮按钮 / Hero e2 */
    val Float = Dp(6f)
    /** 底部弹层 e3 */
    val Sheet = Dp(22f)
    /** 模态对话框 e4 */
    val Dialog = Dp(40f)
}
