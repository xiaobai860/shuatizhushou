package com.example.shuati.logic

import com.example.shuati.model.FavoriteRecord
import com.example.shuati.model.Question
import com.example.shuati.model.QuestionBank
import com.example.shuati.model.QuestionType
import com.example.shuati.model.QuizHistory
import com.example.shuati.model.WrongRecord

/**
 * 智能推荐算法（v2，更准更稳）：
 *  1. 错题：连续遗忘曲线(指数) × 错误频次 × 掌握度——临门一脚(连对=目标-1)加成，已掌握降权；
 *     越久没练、错得越多，越优先；同一题库错题不受软上限约束（最高优先）。
 *  2. 收藏：已掌握的收藏降权（已会不必反复推），未掌握保持中优先。
 *  3. 低正确率题库(<70%)：遗忘曲线 × 薄弱度(离 70% 越远越推) × 练习可信度(次数少则降权)，
 *     排除已掌握题，按题型均匀取样、每库最多 4 题。
 *  4. 未练习题库：新题探索，受软上限约束。
 *  5. 兜底：题型多样性补满 20 题，受软上限约束，避免单库刷屏。
 *  综合分降序取前 20；引入每题库软上限(6)保证推荐多样性。
 */
object Recommend {

    private val DAY = 86400000L

    /** 推荐项：题目 + 所属题库 + 推荐原因 + 综合分数 */
    data class RecommendItem(
        val question: Question,
        val bankId: String,
        val bankName: String,
        val reason: String,
        val score: Double
    )

    /**
     * 遗忘曲线权重（连续版）：距今越久，遗忘风险越高，复习优先级越大。
     * 采用指数遗忘模型 retention = exp(-hours / HALF_LIFE)，复习紧迫度 = 1 - retention。
     * 相比原 4 档阶梯，能更平滑、准确地排序"哪道题最该现在复习"。
     * HALF_LIFE = 72h（3 天半衰期）；结果钳制到 [0.15, 1.0] 保证可比较。
     */
    private const val HALF_LIFE_HOURS = 72.0

    fun forgettingFactor(ts: Long, now: Long = System.currentTimeMillis()): Double {
        if (ts <= 0) return 1.0
        val hours = (now - ts).coerceAtLeast(0) / 3600000.0
        return (1 - kotlin.math.exp(-hours / HALF_LIFE_HOURS)).coerceIn(0.15, 1.0)
    }

    /** 题型中文标签 */
    fun typeLabel(t: QuestionType?): String = when (t) {
        QuestionType.single -> "单选"
        QuestionType.multiple -> "多选"
        QuestionType.judge -> "判断"
        QuestionType.fill -> "填空"
        QuestionType.essay -> "简答"
        null -> "未知"
    }

    /**
     * 构建推荐列表（取前 20）。
     *
     * @param getStreak (bankId, questionId) -> 连续答对次数（掌握度）
     */
    fun buildRecommendations(
        banks: List<QuestionBank>,
        wrongRecords: List<WrongRecord>,
        favorites: List<FavoriteRecord>,
        history: List<QuizHistory>,
        getStreak: (bankId: String, questionId: String) -> Int,
        masteryTarget: Int = 3
    ): List<RecommendItem> {
        if (banks.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        val target = masteryTarget.coerceAtLeast(1)

        // 预计算索引
        val wrongMap = wrongRecords.associateBy { key(it.bankId, it.questionId) }

        // 每个题库最后练习时间（取 history 中最新的 finishedAt）
        val lastPracticedMap = HashMap<String, Long>()
        history.forEach { h ->
            val ts = if (h.finishedAt > 0) h.finishedAt else h.createdAt
            if (ts > 0 && ts > (lastPracticedMap[h.bankId] ?: 0L)) lastPracticedMap[h.bankId] = ts
        }

        // 题库平均正确率 + 练习次数（次数越少越不可靠）
        val accList = HashMap<String, MutableList<Int>>()
        history.forEach { h -> accList.getOrPut(h.bankId) { mutableListOf() }.add(h.accuracy) }
        val bankAvgAcc = accList.mapValues { (_, accs) -> Math.round(accs.average()).toInt() }

        val added = HashSet<String>()
        val scored = mutableListOf<RecommendItem>()
        // 每题库软上限，保证推荐多样性（错题最高优先，不计入上限）
        val MAX_PER_BANK = 6
        val bankCount = HashMap<String, Int>()
        fun addItem(item: RecommendItem, exemptFromCap: Boolean = false): Boolean {
            val cnt = bankCount[item.bankId] ?: 0
            if (!exemptFromCap && cnt >= MAX_PER_BANK) return false
            added.add(key(item.bankId, item.question.id))
            bankCount[item.bankId] = cnt + 1
            scored.add(item)
            return true
        }

        // ── 1. 错题：遗忘曲线 × 错误频次 × 掌握度（连对临门一脚加成） ────────
        for (r in wrongRecords) {
            val k = key(r.bankId, r.questionId)
            if (added.contains(k)) continue
            val bank = banks.find { it.id == r.bankId } ?: continue
            val q = r.question ?: bank.questions.find { it.id == r.questionId } ?: continue
            val streak = getStreak(r.bankId, r.questionId)
            val forget = forgettingFactor(if (r.lastWrongAt > 0) r.lastWrongAt else r.recordedAt, now)
            val freq = Math.min(1.6, 0.5 + 0.12 * r.wrongCount)   // 错得越多越该练
            val masteryFactor = when {
                streak >= target -> 0.35           // 已基本掌握，降低优先级
                streak == target - 1 -> 1.15        // 临门一脚：巩固掌握
                else -> 1.0
            }
            val score = (80 + 20 * forget) * freq * masteryFactor
            val reason = when {
                streak >= target -> "错题·已连对${streak}次"
                streak > 0 -> "错题·连对${streak}次"
                else -> "错题·待攻克"
            }
            addItem(RecommendItem(q, r.bankId, bank.name, reason, score), exemptFromCap = true)
        }

        // ── 2. 收藏题：已掌握的收藏降权（你已经会了，不必反复推） ───────────
        for (f in favorites) {
            val k = key(f.bankId, f.questionId)
            if (added.contains(k)) continue
            val bank = banks.find { it.id == f.bankId } ?: continue
            val q = f.question ?: bank.questions.find { it.id == f.questionId } ?: continue
            val mastered = getStreak(f.bankId, f.questionId) >= target
            val score = if (mastered) 30.0 else 52.0
            addItem(RecommendItem(q, f.bankId, bank.name, if (mastered) "已收藏·已掌握" else "已收藏", score))
        }

        // ── 3. 低正确率题库（< 70%）：按遗忘曲线 + 薄弱度 + 练习可信度推题 ──
        for (bank in banks) {
            val avg = bankAvgAcc[bank.id] ?: continue
            if (avg >= 70) continue
            val attempts = accList[bank.id]?.size ?: 0
            val weakness = (70 - avg) / 70.0                  // 离 70% 越远越薄弱
            val conf = if (attempts <= 1) 0.6 else 1.0        // 练习太少不可靠，降权
            val last = lastPracticedMap[bank.id] ?: (now - 7 * DAY)
            val forget = forgettingFactor(last, now)
            val base = 40 * forget * (0.5 + 0.5 * weakness) * conf
            val pool = bank.questions.filter { q ->
                val k = key(bank.id, q.id)
                !added.contains(k) && !wrongMap.containsKey(k) && getStreak(bank.id, q.id) < target
            }
            val byType = LinkedHashMap<QuestionType, MutableList<Question>>()
            for (q in pool) byType.getOrPut(q.type) { mutableListOf() }.add(q)
            val typeOrder = listOf(QuestionType.single, QuestionType.multiple, QuestionType.judge, QuestionType.fill, QuestionType.essay)
            var taken = 0
            for (t in typeOrder) {
                if (taken >= 4) break
                val pick = (byType[t] ?: emptyList()).shuffled().firstOrNull() ?: continue
                if (!addItem(RecommendItem(pick, bank.id, bank.name, "${bank.name}正确率$avg%", base))) continue
                taken++
            }
        }

        // ── 4. 未练习过的题库（新题探索），受软上限约束 ─────────────────────
        val newBanks = banks.filter { !bankAvgAcc.containsKey(it.id) }
        for (bank in newBanks.take(2)) {
            val pool = bank.questions.filter { !added.contains(key(bank.id, it.id)) }
            for (q in pool.shuffled().take(3)) {
                addItem(RecommendItem(q, bank.id, bank.name, "新题库探索", 30.0))
            }
        }

        // ── 5. 兜底补充（题型多样性，凑满 20 题），受软上限约束 ─────────────
        if (scored.size < 20) {
            val typeCount = HashMap<QuestionType, Int>()
            for (it in scored) typeCount[it.question.type] = (typeCount[it.question.type] ?: 0) + 1
            val sortedTypes = listOf(QuestionType.single, QuestionType.multiple, QuestionType.judge, QuestionType.fill, QuestionType.essay)
                .sortedBy { typeCount[it] ?: 0 }
            for (bank in banks.shuffled()) {
                if (scored.size >= 20) break
                for (t in sortedTypes) {
                    if (scored.size >= 20) break
                    val pool = bank.questions.filter { q -> q.type == t && !added.contains(key(bank.id, q.id)) }
                    val pick = pool.shuffled().firstOrNull() ?: continue
                    addItem(RecommendItem(pick, bank.id, bank.name, "随机练习", 10 + Math.random() * 5))
                }
            }
        }

        // 排序：按综合分降序（同分随机），取前 20
        return scored.sortedByDescending { it.score }.take(20)
    }

    private fun key(bankId: String, questionId: String): String = "${bankId}_$questionId"
}
