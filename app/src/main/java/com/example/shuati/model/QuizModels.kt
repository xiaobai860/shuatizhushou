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
 * 题目难度。unset 表示未设置（兼容历史数据）。
 * 序列化用枚举名（easy/normal/hard/unset），Excel 中写中文（简单/中等/困难）。
 */
@Serializable
enum class QuestionDifficulty {
    unset,      // 未设置（默认，兼容老数据）
    easy,       // 简单
    normal,     // 中等
    hard        // 困难
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
    val aiExplanation: AiExplanation? = null,   // AI 生成的解析（默认保存，区别于原题 explanation）
    val tags: List<String> = emptyList(),  // 标签（如行业来源、知识点分类）
    val difficulty: QuestionDifficulty = QuestionDifficulty.unset,  // 难度
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
    val aiHint: String? = null,                // 题库级默认定向提示（如「注册会计师考试」），AI 解析时带入
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    /**
     * 重复题数量（查重算法 v3 结果的持久化缓存）。
     * -1 = 尚未计算或已失效（题目增删后）；导入时计算并写回，
     * 题库管理页直接读取显示徽章，进页面零计算。
     * 失效后由题库管理页进入时后台补算。
     */
    val dupCount: Int = -1,
    /**
     * 疑似错题数量（Scoring.checkIsSuspect 扫描结果的持久化缓存）。
     * 语义与 dupCount 相同：-1 = 未算/失效，导入时计算，失效后进管理页补算。
     */
    val suspectCount: Int = -1
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
    // 动态取色（Material You）：默认开启
    val dynamicColor: Boolean = true,
    // 主题模式：system / light / dark
    val themeMode: String = "system",
    // masteryStreaks 用 Map 表示，key 形如 "bankId::questionId"
    val masteryStreaks: Map<String, Int> = emptyMap()
)

/**
 * 备份文件结构（等价 BackupData，version=6）
 *
 * 说明：
 * - 从 version=6 起，备份分为两类载体：
 *   1) 旧版单 JSON（本结构，题库题目内联）；
 *   2) 新版 ZIP（app_data.json 仅含题库元数据，题目另存为 banks/<id>.xlsx）。
 *   两者均可用本结构反序列化；ZIP 还原时会先把 Excel 题目回填到 banks 再走统一还原逻辑。
 * - questionStats / wrongAnswers 为每题维度数据，旧备份缺省为空，还原时本地优先保留。
 */
@Serializable
data class BackupData(
    val version: Int = 6,
    val exportedAt: Long = 0L,
    val banks: List<QuestionBank> = emptyList(),
    val wrongRecords: List<WrongRecord> = emptyList(),
    val favoriteRecords: List<FavoriteRecord> = emptyList(),
    val progress: List<QuizProgress> = emptyList(),
    val history: List<QuizHistory> = emptyList(),
    val studyPlans: List<StudyPlan> = emptyList(),
    val planProgress: List<PlanProgress> = emptyList(),
    val questionStats: Map<String, String> = emptyMap(),     // 每题正确率统计 key="bankId::questionId" value="correct,total"
    val wrongAnswers: Map<String, List<String>> = emptyMap(), // 每题错答历史 key="bankId::questionId"
    val settings: AppSettings = AppSettings()
)

/**
 * AI 解析结果。由 AiExplainService 生成后写入 Question.aiExplanation 并落盘。
 * citations 为联网搜索来源（若启用搜索引擎），用于展示「引用出处」。
 */
@Serializable
data class AiExplanation(
    val text: String,
    val model: String = "",
    val provider: String = "",
    val createdAt: Long = 0L,
    val hintUsed: String? = null,            // 本次生成使用的定向提示语（空=未用）
    val citations: List<String> = emptyList(),
    val promptHash: String = ""              // 题目+提示 的哈希，用于去重/调试
)

/**
 * AI 大模型 / 搜索引擎配置。仅存于本地加密 SharedPreferences，不上传任何服务器。
 */
data class AiConfig(
    val llmBaseUrl: String = "",   // OpenAI 兼容端点，如 https://api.openai.com/v1
    val llmApiKey: String = "",
    val llmModel: String = "",
    val searchProvider: String = "",  // "tavily" | "exa" | "bocha" | ""（空=依赖模型内置搜索）
    val searchApiKey: String = "",
    val customPrompt: String = ""  // 自定义解析模板（即内置 skill），空=使用内置默认 skill
)

/**
 * 单个大模型供应商配置（多供应商管理的基本单元）。
 * 一个供应商可保存多个已拉取的模型，并指定其中一个为当前选中模型。
 */
@Serializable
data class AiProviderConfig(
    val id: String = "",                // 唯一 id（持久化标识，用于记忆"上次使用"）
    val name: String = "",              // 用户自定义名称，如「DeepSeek」「我的硅基流动」
    val baseUrl: String = "",           // OpenAI 兼容端点
    val apiKey: String = "",
    val models: List<String> = emptyList(),   // 用户从该供应商拉取并多选保存的模型列表
    val selectedModel: String = ""      // 该供应商下当前选中的模型（空=未选）
)
