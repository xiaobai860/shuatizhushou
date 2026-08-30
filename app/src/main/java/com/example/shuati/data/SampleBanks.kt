package com.example.shuati.data

import com.example.shuati.model.FavoriteRecord
import com.example.shuati.model.Question
import com.example.shuati.model.QuestionBank
import com.example.shuati.model.QuestionType
import com.example.shuati.model.QuizHistory
import com.example.shuati.model.StudyPlan
import com.example.shuati.model.WrongRecord
import java.time.LocalDate

/**
 * 示例（测试）数据。
 *
 * ⚠️ 本文件为「开发期测试题库」，将来移除时：
 *    1) 删除本文件；
 *    2) 删除 ShuatiApplication.onCreate 中的 SampleBanks.seedSampleData(repository) 这一行。
 * 即可完全清除，不会在其它页面/模型留下残留引用。
 *
 * 约定：所有示例数据的 id 均带 `sample_` 前缀，便于将来批量识别与清理。
 */

private fun q(
    id: String,
    question: String,
    type: QuestionType,
    options: List<String>? = null,
    answer: String,
    explanation: String? = null,
    tags: List<String> = emptyList(),
    orderFree: Boolean = false
) = Question(
    id = id,
    question = question,
    options = options,
    answer = answer,
    type = type,
    explanation = explanation,
    tags = tags,
    orderFree = orderFree
)

fun buildSampleBanks(): List<QuestionBank> {
    val now = System.currentTimeMillis()
    return listOf(
        QuestionBank(
            id = "sample_cs",
            name = "计算机基础（示例）",
            createdAt = now,
            updatedAt = now,
            questions = listOf(
                q("cs_q1", "二进制数 1010 对应的十进制值是？", QuestionType.single,
                    options = listOf("8", "10", "12", "16"), answer = "10",
                    explanation = "按权展开：1×8 + 0×4 + 1×2 + 0×1 = 10。", tags = listOf("进制")),
                q("cs_q2", "下列哪些属于面向对象编程语言？（多选）", QuestionType.multiple,
                    options = listOf("Java", "C++", "Python", "HTML"), answer = "A,B,C",
                    explanation = "HTML 是标记语言，不是面向对象编程语言。", tags = listOf("语言")),
                q("cs_q3", "HTTP 协议是无状态协议。", QuestionType.judge,
                    answer = "正确", explanation = "HTTP 本身不保存客户端状态。", tags = listOf("网络")),
                q("cs_q4", "下列哪种数据结构遵循先进先出（FIFO）？", QuestionType.single,
                    options = listOf("栈", "队列", "二叉树", "哈希表"), answer = "队列",
                    explanation = "队列头出尾进，先进先出。", tags = listOf("数据结构")),
                q("cs_q5", "TCP 是面向连接的传输层协议。", QuestionType.judge,
                    answer = "正确", tags = listOf("网络")),
                q("cs_q6", "下列属于关系型数据库的有？（多选）", QuestionType.multiple,
                    options = listOf("MySQL", "Redis", "PostgreSQL", "MongoDB"), answer = "A,C",
                    explanation = "Redis、MongoDB 为非关系型数据库。", tags = listOf("数据库")),
                q("cs_q7", "一个字节（Byte）由多少位（bit）组成？", QuestionType.single,
                    options = listOf("4", "8", "16", "32"), answer = "8", tags = listOf("基础")),
                q("cs_q8", "Android 应用只能用 Java 开发。", QuestionType.judge,
                    answer = "错误", explanation = "也可用 Kotlin、C++ 等开发。", tags = listOf("移动"))
            )
        ),
        QuestionBank(
            id = "sample_eng",
            name = "英语练习（示例）",
            createdAt = now,
            updatedAt = now,
            questions = listOf(
                q("eng_q1", "The capital of France is ______.", QuestionType.fill,
                    answer = "Paris", explanation = "巴黎是法国首都。", tags = listOf("地理")),
                q("eng_q2", "请用英语简述你最喜欢的爱好。", QuestionType.essay,
                    answer = "I like reading books because it broadens my mind and helps me relax.",
                    tags = listOf("写作")),
                q("eng_q3", "Choose the correct spelling:", QuestionType.single,
                    options = listOf("Recieve", "Receive", "Recive", "Receve"), answer = "Receive",
                    explanation = "c 之后 e 在 i 前：ie 变 ei。", tags = listOf("拼写")),
                q("eng_q4", "She ___ (go) to school every day.", QuestionType.fill,
                    answer = "goes", explanation = "第三人称单数加 -es。", tags = listOf("语法")),
                q("eng_q5", "What is the importance of learning English?", QuestionType.essay,
                    answer = "English helps us communicate with people worldwide and access more information.",
                    tags = listOf("写作")),
                q("eng_q6", "Which word means '快速的'?", QuestionType.single,
                    options = listOf("slow", "quick", "calm", "loud"), answer = "quick",
                    tags = listOf("词汇"))
            )
        ),
        QuestionBank(
            id = "sample_gk",
            name = "生活常识（示例）",
            createdAt = now,
            updatedAt = now,
            questions = listOf(
                q("gk_q1", "在标准大气压下，水的沸点是 100℃。", QuestionType.judge,
                    answer = "正确", tags = listOf("物理")),
                q("gk_q2", "一年共有多少个月？", QuestionType.single,
                    options = listOf("10", "11", "12", "13"), answer = "12", tags = listOf("历法")),
                q("gk_q3", "中国的首都是______。", QuestionType.fill,
                    answer = "北京", tags = listOf("地理")),
                q("gk_q4", "地球是太阳系中体积最大的行星。", QuestionType.judge,
                    answer = "错误", explanation = "木星体积最大。", tags = listOf("天文")),
                q("gk_q5", "下列哪种维生素有助于钙的吸收？", QuestionType.single,
                    options = listOf("维生素A", "维生素C", "维生素D", "维生素E"), answer = "维生素D",
                    tags = listOf("健康")),
                q("gk_q6", "光在真空中的传播速度约为 ___ 万千米/秒。", QuestionType.fill,
                    answer = "30", explanation = "约 299792 km/s，近 30 万 km/s。", tags = listOf("物理"))
            )
        ),
        QuestionBank(
            id = "sample_math",
            name = "数学运算（示例）",
            createdAt = now,
            updatedAt = now,
            questions = listOf(
                q("math_q1", "2 + 3 × 4 = ?", QuestionType.single,
                    options = listOf("20", "14", "24", "9"), answer = "14",
                    explanation = "先乘后加：3×4=12，再加 2 得 14。", tags = listOf("运算")),
                q("math_q2", "下列属于偶数的是？（多选）", QuestionType.multiple,
                    options = listOf("2", "3", "4", "8"), answer = "A,C,D", tags = listOf("数论")),
                q("math_q3", "12 和 18 的最大公约数是______。", QuestionType.fill,
                    answer = "6", explanation = "12=2²×3，18=2×3²，gcd=2×3=6。", tags = listOf("数论")),
                q("math_q4", "0 是自然数。", QuestionType.judge,
                    answer = "正确", explanation = "现行教材通常将 0 列为自然数。", tags = listOf("数论")),
                q("math_q5", "边长为 5cm 的正方形，面积是？", QuestionType.single,
                    options = listOf("10", "20", "25", "100"), answer = "25",
                    explanation = "5×5=25。", tags = listOf("几何")),
                q("math_q6", "下列选项中为质数的是？（多选）", QuestionType.multiple,
                    options = listOf("2", "9", "11", "15"), answer = "A,C",
                    explanation = "9=3×3，15=3×5，均非质数。", tags = listOf("数论"))
            )
        ),
        QuestionBank(
            id = "sample_sci",
            name = "综合知识（示例）",
            createdAt = now,
            updatedAt = now,
            questions = listOf(
                q("sci_q1", "地球大气中含量最高的气体是？", QuestionType.single,
                    options = listOf("氮气", "氧气", "二氧化碳", "氢气"), answer = "氮气",
                    explanation = "氮气约占大气体积的 78%。", tags = listOf("常识")),
                q("sci_q2", "下列属于可再生能源的是？（多选）", QuestionType.multiple,
                    options = listOf("太阳能", "风能", "煤炭", "水能"), answer = "A,B,D",
                    explanation = "煤炭为化石燃料，不可再生。", tags = listOf("能源")),
                q("sci_q3", "声音不能在真空中传播。", QuestionType.judge,
                    answer = "正确", explanation = "声音需要介质，真空中无介质。", tags = listOf("物理")),
                q("sci_q4", "光的三原色（RGB）分别是 ___ 、___ 和 ___ 。", QuestionType.fill,
                    answer = "红|绿|蓝", orderFree = true,
                    explanation = "红、绿、蓝三色光可混合出各种颜色。", tags = listOf("光学"))
            )
        )
    )
}

/**
 * 在应用启动（ShuatiApplication.onCreate）时调用一次。
 * 仅当不存在任何 sample_ 题库时才播种，保证幂等、重复启动不会重复写入。
 */
fun seedSampleData(repo: AppRepository) {
    val banks = buildSampleBanks()
    var newlyAdded = false
    for (b in banks) {
        if (repo.getBanks().none { it.id == b.id }) {
            repo.addBank(b)
            newlyAdded = true
        }
    }
    if (!newlyAdded) return
    seedSampleExtras(repo, banks)
}

/**
 * 预置一些示例状态，让「错题 / 收藏 / 历史 / 学习计划 / 掌握度」等页面开箱即可测试，
 * 无需先手动刷大量题。仅在首次播种时执行，避免重复启动产生重复数据。
 */
private fun seedSampleExtras(repo: AppRepository, banks: List<QuestionBank>) {
    val byId = banks.associateBy { it.id }
    val now = System.currentTimeMillis()
    val today = LocalDate.now().toString()

    fun find(bankId: String, qid: String) = byId[bankId]?.questions?.firstOrNull { it.id == qid }

    // 错题示例
    listOf(
        "sample_cs" to "cs_q1",
        "sample_math" to "math_q3",
        "sample_sci" to "sci_q2"
    ).forEach { (bankId, qid) ->
        val bank = byId[bankId]!!
        val question = find(bankId, qid) ?: return@forEach
        repo.addWrongRecord(
            WrongRecord(
                bankId = bankId, bankName = bank.name, questionId = qid,
                question = question, wrongCount = 1, lastWrongAt = now,
                recordedAt = now, createdAt = now
            )
        )
    }

    // 收藏示例
    listOf(
        "sample_eng" to "eng_q1",
        "sample_gk" to "gk_q3",
        "sample_sci" to "sci_q1"
    ).forEach { (bankId, qid) ->
        val bank = byId[bankId]!!
        val question = find(bankId, qid) ?: return@forEach
        if (!repo.isFavorite(bankId, qid)) {
            repo.toggleFavorite(
                FavoriteRecord(bankId = bankId, bankName = bank.name, questionId = qid,
                    question = question, createdAt = now)
            )
        }
    }

    // 历史示例（一套已完成记录）
    repo.addHistoryRecord(
        QuizHistory(
            id = "sample_hist1", bankId = "sample_cs", bankName = "计算机基础（示例）",
            total = 8, correct = 6, wrong = 2, durationSec = 305,
            mode = "study", accuracy = 75, finishedAt = now, createdAt = now
        )
    )

    // 学习计划示例
    listOf(
        StudyPlan(
            id = "sample_plan_cs", title = "每日计算机 10 题", bankId = "sample_cs",
            bankName = "计算机基础（示例）", totalQuestions = 8, dailyCount = 10,
            mode = "study", orderType = "sequence", startDate = today, cycleDays = 7,
            enabled = true, createdAt = now
        ),
        StudyPlan(
            id = "sample_plan_math", title = "数学随机 5 题", bankId = "sample_math",
            bankName = "数学运算（示例）", totalQuestions = 6, dailyCount = 5,
            mode = "study", orderType = "random", startDate = today, cycleDays = 7,
            enabled = true, createdAt = now
        )
    ).forEach { plan ->
        if (repo.getStudyPlans().none { it.id == plan.id }) repo.saveStudyPlan(plan)
    }

    // 掌握度示例（让学习页进度条非空白）
    // 已掌握（连续答对达到目标）
    repeat(3) { repo.incrementStreak("sample_cs", "cs_q2") }
    repeat(3) { repo.incrementStreak("sample_math", "math_q5") }
    // 模糊（连续答对但未达目标）
    repeat(1) { repo.incrementStreak("sample_cs", "cs_q4") }
    repeat(2) { repo.incrementStreak("sample_eng", "eng_q1") }
    // 每题正确率统计
    repo.recordQuestionResult("sample_cs", "cs_q2", correct = true)
    repo.recordQuestionResult("sample_cs", "cs_q4", correct = true)
    repo.recordQuestionResult("sample_math", "math_q5", correct = true)
    repo.recordQuestionResult("sample_eng", "eng_q1", correct = true)
    repo.recordQuestionResult("sample_sci", "sci_q1", correct = true)
    // 综合知识掌握度示例
    repeat(2) { repo.incrementStreak("sample_sci", "sci_q1") }
}
