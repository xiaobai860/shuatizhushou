package com.example.shuati.ui

/**
 * 答题会话结果（内存传递，供结果页展示分题型正确率 / 再看一眼错题明细）。
 * 每次进入答题页时重置；结果页读取后清空，避免跨会话残留。
 */
object QuizSessionResult {
    /** 各题型正确率：type 为题型名，correct/total 为本会话累计 */
    var typeStats: List<TypeStat> = emptyList()
    /** 答错题明细（再看一眼列表） */
    var wrongDetails: List<WrongDetail> = emptyList()
    /** 本次开始前同题库上次正确率（用于「比上次提升」文案） */
    var prevAccuracy: Int? = null
    /** 本次新增错题数（进入会话前错题本没有的） */
    var newWrongCount: Int = 0

    fun reset() {
        typeStats = emptyList()
        wrongDetails = emptyList()
        prevAccuracy = null
        newWrongCount = 0
    }
}

/** 分题型统计 */
data class TypeStat(
    val type: String,      // 单选 / 多选 / 判断 / 填空 / 简答
    val correct: Int,
    val total: Int
)

/** 答错题明细 */
data class WrongDetail(
    val bankName: String,
    val question: String,
    val userAnswer: String,
    val correctAnswer: String,
    val explanation: String
)
