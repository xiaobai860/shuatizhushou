package com.example.shuati.logic

import com.example.shuati.model.FavoriteRecord
import com.example.shuati.model.Question
import com.example.shuati.model.QuestionBank
import com.example.shuati.model.QuestionType
import com.example.shuati.model.QuizHistory
import com.example.shuati.model.WrongRecord

/**
 * 智能推荐算法。等价小程序 pages/recommend 的 buildRecommendations：
 *  1. 错题：间隔重复加权（越久未练权重越高，streak 越高越接近掌握则略降）
 *  2. 收藏：中等优先（未在错题中出现的收藏）
 *  3. 低正确率题库（<70%）：按遗忘曲线推题，每库最多 4 题、按题型均匀取样
 *  4. 未练习过的题库：新题探索，最多 2 库 × 3 题
 *  5. 兜底补充：题型多样性，凑满 20 题
 *  综合分数降序，同分随机，取前 20。
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

    /** 时间衰减：距今越久，权重越高（遗忘风险越大）。等价小程序 timeDecay */
    fun timeDecay(ts: Long, now: Long = System.currentTimeMillis()): Double {
        val hours = (now - ts).coerceAtLeast(0) / 3600000.0
        return when {
            hours < 24 -> 0.3
            hours < 72 -> 0.6
            hours < 168 -> 0.8
            else -> 1.0
        }
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
        getStreak: (bankId: String, questionId: String) -> Int
    ): List<RecommendItem> {
        if (banks.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()

        // 预计算索引
        val wrongMap = wrongRecords.associateBy { key(it.bankId, it.questionId) }

        // 每个题库最后练习时间（取 history 中最新的 finishedAt）
        val lastPracticedMap = HashMap<String, Long>()
        history.forEach { h ->
            val ts = if (h.finishedAt > 0) h.finishedAt else h.createdAt
            if (ts > 0 && ts > (lastPracticedMap[h.bankId] ?: 0L)) lastPracticedMap[h.bankId] = ts
        }

        // 题库平均正确率
        val accList = HashMap<String, MutableList<Int>>()
        history.forEach { h -> accList.getOrPut(h.bankId) { mutableListOf() }.add(h.accuracy) }
        val bankAvgAcc = accList.mapValues { (_, accs) -> Math.round(accs.average()).toInt() }

        val added = HashSet<String>()
        val scored = mutableListOf<RecommendItem>()

        // ── 1. 错题：间隔重复加权 ───────────────────────────────────────────
        for (r in wrongRecords) {
            val k = key(r.bankId, r.questionId)
            if (added.contains(k)) continue
            val bank = banks.find { it.id == r.bankId } ?: continue
            val q = r.question ?: bank.questions.find { it.id == r.questionId } ?: continue
            added.add(k)
            val streak = getStreak(r.bankId, r.questionId)
            val decay = timeDecay(if (r.recordedAt > 0) r.recordedAt else r.lastWrongAt, now)
            val score = 100 * decay * Math.max(0.3, 1 - streak * 0.15)
            val reason = if (streak > 0) "错题·连对${streak}次" else "错题·待攻克"
            scored.add(RecommendItem(q, r.bankId, bank.name, reason, score))
        }

        // ── 2. 收藏题：中等优先 ─────────────────────────────────────────────
        for (f in favorites) {
            val k = key(f.bankId, f.questionId)
            if (added.contains(k)) continue
            val bank = banks.find { it.id == f.bankId } ?: continue
            val q = f.question ?: bank.questions.find { it.id == f.questionId } ?: continue
            added.add(k)
            scored.add(RecommendItem(q, f.bankId, bank.name, "已收藏", 55.0))
        }

        // ── 3. 低正确率题库（< 70%）：按遗忘曲线推题 ─────────────────────────
        for (bank in banks) {
            val avg = bankAvgAcc[bank.id] ?: continue
            if (avg >= 70) continue
            val last = lastPracticedMap[bank.id] ?: (now - 7 * DAY)
            val decay = timeDecay(last, now)
            val pool = bank.questions.filter { q ->
                val k = key(bank.id, q.id)
                !added.contains(k) && !wrongMap.containsKey(k)
            }
            val byType = LinkedHashMap<QuestionType, MutableList<Question>>()
            for (q in pool) byType.getOrPut(q.type) { mutableListOf() }.add(q)
            val typeOrder = listOf(QuestionType.single, QuestionType.multiple, QuestionType.judge, QuestionType.fill, QuestionType.essay)
            var taken = 0
            for (t in typeOrder) {
                if (taken >= 4) break
                val pick = (byType[t] ?: emptyList()).shuffled().firstOrNull() ?: continue
                val k = key(bank.id, pick.id)
                added.add(k)
                scored.add(RecommendItem(pick, bank.id, bank.name, "${bank.name}正确率$avg%", 40 * decay))
                taken++
            }
        }

        // ── 4. 未练习过的题库（新题探索） ───────────────────────────────────
        val newBanks = banks.filter { !bankAvgAcc.containsKey(it.id) }
        for (bank in newBanks.take(2)) {
            val pool = bank.questions.filter { !added.contains(key(bank.id, it.id)) }
            for (q in pool.shuffled().take(3)) {
                val k = key(bank.id, q.id)
                added.add(k)
                scored.add(RecommendItem(q, bank.id, bank.name, "新题库探索", 30.0))
            }
        }

        // ── 5. 兜底补充（题型多样性，凑满 20 题） ───────────────────────────
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
                    val k = key(bank.id, pick.id)
                    added.add(k)
                    scored.add(RecommendItem(pick, bank.id, bank.name, "随机练习", 10 + Math.random() * 5))
                }
            }
        }

        // 排序：先随机打乱，再按分数稳定降序（保证同分内顺序随机）
        return scored.shuffled().sortedByDescending { it.score }.take(20)
    }

    private fun key(bankId: String, questionId: String): String = "${bankId}_$questionId"
}
