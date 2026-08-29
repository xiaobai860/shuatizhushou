package com.example.shuati.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/**
 * 动效体系（2026 版）：
 * 三档 120 / 200 / 320ms，统一减速曲线，禁用回弹 / 弹性缓动。
 * 只动画 opacity / transform / 背景色，不动画 layout 属性。
 */
object ShuatiMotion {
    /** 微交互（按钮按下、开关） */
    const val FastMs = 120
    /** 标准（页面内状态切换） */
    const val BaseMs = 200
    /** 进入（页面 / 弹层 / 结果揭晓） */
    const val SlowMs = 320
    /** 进度条填充 */
    const val FillMs = 700
    /** 打卡 / 掌握度环 */
    const val RingMs = 600

    /** 标准减速曲线 */
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** 进入曲线（更快起手、更慢收尾） */
    val Enter: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
}
