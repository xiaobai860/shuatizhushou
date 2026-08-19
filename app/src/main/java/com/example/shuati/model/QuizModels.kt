package com.example.shuati.model

import kotlinx.serialization.Serializable

/**
 * 题目类型：单选 / 多选 / 判断 / 填空 / 简答
 * 等价 types/quiz.ts 的 QuestionType
 */
@Serializable
enum class QuestionType {
    single, multiple, judge, fill, essay
}

/**
 * 单道题目。等价 types/quiz.ts Question
 * 注意：answer 在 JSON 中统一以「字符串」形态存储（与小程序保持一致），
 * 多选用逗号分隔（如 "A,B"），判分时在逻辑层拆分。
 */
@Serializable
data class Question(
    val id: String,
    val question: String,
    val options: List<String>? = null,   // 选项（不含前缀字母），如 ["选项1","选项2"]
    val answer: String? = null,            // 标准答案字符串
    val type: QuestionType = QuestionType.single,
    val explanation: String? = null,
    val tags: List<String> = emptyList(),
    val orderFree: Boolean = false,         // 浏览模式下是否打乱顺序（等价 orderFree）
    // 数据质量标记
    val incomplete: Boolean = false,        // 选项/答案为空 → 内容不完整
    val idProblem: Boolean = false         // 题干疑似缺失（ID 校验）或题干异常
)

/**
 * 题库。等价 types/quiz.ts QuestionBank
 */
@Serializable
data class QuestionBank(
    val id: String,
    val name: String,
    val questions: List<Question> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

/**
 * 错题记录。等价 WrongRecord
 */
@Serializable
data class WrongRecord(
    val bankId: String,
    val bankName: String,
    val questionId: String,
    val question: Question? = null,   // 冗余快照，便于直接展示
    val wrongCount: Int = 1,
    val lastWrongAt: Long = 0L,
    val recordedAt: Long = 0L,             // 首次录入错题时间戳（recommend 时间衰减用）
    val createdAt: Long = 0L
)

/**
 * 收藏记录。等价 FavoriteRecord
 */
@Serializable
data class FavoriteRecord(
    val bankId: String,
    val bankName: String,
    val questionId: String,
    val question: Question? = null,
    val note: String? = null,
    val createdAt: Long = 0L
)

/**
 * 刷题进度（断点续刷 / 浏览进度）。等价 QuizProgress
 */
@Serializable
data class QuizProgress(
    val bankId: String,
    val answeredIds: List<String> = emptyList(),
    val browseIndex: Int? = null,
    val everCompleted: Boolean = false,
    val updatedAt: Long = 0L
)

/**
 * 刷题历史（每套完成记录）。等价 QuizHistory
 */
@Serializable
data class QuizHistory(
    val id: String,
    val bankId: String,
    val bankName: String,
    val total: Int,
    val correct: Int,
    val wrong: Int,
    val durationSec: Int? = null,
    val mode: String? = null,
    val accuracy: Int = 0,             // 正确率百分比 0-100（stats 维度用）
    val finishedAt: Long = 0L,         // 完成时间戳（stats 时间轴用）
    val createdAt: Long = 0L
)

/**
 * 学习计划。等价 StudyPlan
 */
@Serializable
data class StudyPlan(
    val id: String,
    val title: String,
    val bankId: String,
    val bankName: String,
    val totalQuestions: Int,
    val dailyCount: Int,
    val mode: String,            // study(练习) | browse(浏览)
    val orderType: String,       // sequence(顺序) | random(随机)
    val startDate: String,       // YYYY-MM-DD
    val endDate: String? = null, // YYYY-MM-DD 计划截止（过期则失效）
    val cycleDays: Int,          // 循环天数（一轮）
    val dailyTargetCeiling: Int = 0,  // 每日上限：0=不限
    val isAllBanks: Boolean = false,  // 是否覆盖全部题库
    val enabled: Boolean = true,
    val archived: Boolean = false,
    val createdAt: Long = 0L
)

/**
 * 学习计划每日进度（含打卡）。等价 PlanProgress
 */
@Serializable
data class PlanProgress(
    val planId: String,
    val answeredIds: List<String> = emptyList(),
    val sessionDate: String? = null,
    val sessionQuestionIds: List<String> = emptyList(),
    val sessionAnsweredCount: Int = 0,
    val sessionBrowseIndex: Int = 0,
    val punchInDates: List<String> = emptyList(),
    val updatedAt: Long = 0L
)

/**
 * 用户偏好设置。等价 AppSettings
 */
@Serializable
data class AppSettings(
    val masteryTarget: Int = 3,
    val dedupThreshold: Double = 0.45,
    // masteryStreaks 用 Map 表示，key 形如 "bankId::questionId"
    val masteryStreaks: Map<String, Int> = emptyMap()
)

/**
 * 备份文件结构（等价 BackupData，version=5）
 */
@Serializable
data class BackupData(
    val version: Int = 5,
    val exportedAt: Long = 0L,
    val banks: List<QuestionBank> = emptyList(),
    val wrongRecords: List<WrongRecord> = emptyList(),
    val favoriteRecords: List<FavoriteRecord> = emptyList(),
    val progress: List<QuizProgress> = emptyList(),
    val history: List<QuizHistory> = emptyList(),
    val studyPlans: List<StudyPlan> = emptyList(),
    val planProgress: List<PlanProgress> = emptyList(),
    val settings: AppSettings = AppSettings()
)
