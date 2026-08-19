package com.example.shuati.logic

import com.example.shuati.model.Question
import com.example.shuati.model.QuestionType
import com.example.shuati.model.WrongRecord

/**
 * 智能推荐算法。等价小程序 pages/recommend 的评分逻辑：
 *  - 五级权重：错 1→6, 2→9, 3→11, 4→13, ≥5→15
 *  - 时间衰减：越久之前错的权重越低，封底 0.3
 *  - 题型多样性：按题型轮转，避免连续同题型
 *  - 分组练习：同一根题的小问聚合为一组
 *  - 推荐原因标签：高频错题 / 错题 / 偶发错误 + 题库标签
 */
object Recommend {

    private val DAY = 86400000L

    /** 错次 -> 权重（5 级） */
    fun weightOf(wrongCount: Int): Double = when {
        wrongCount <= 1 -> 6.0
        wrongCount == 2 -> 9.0
        wrongCount == 3 -> 11.0
        wrongCount == 4 -> 13.0
        else -> 15.0
    }

    /** 时间衰减：最新→1.0，约 31 天→0.3，封底 0.3 */
    fun timeDecay(lastWrongAt: Long, now: Long = System.currentTimeMillis()): Double {
        val decayDays = ((now - lastWrongAt).coerceAtLeast(0)) / DAY.toDouble()
        val f = 1.0 - 4.0 * decayDays / 31.0
        return f.coerceAtLeast(0.3)
    }

    /** 单条错题综合得分 */
    fun scoreOf(rec: WrongRecord, now: Long = System.currentTimeMillis()): Double =
        weightOf(rec.wrongCount) * timeDecay(rec.lastWrongAt, now)

    /** 题型轮转：保证多样性的推荐顺序 */
    fun orderByType(records: List<WrongRecord>): List<WrongRecord> {
        val buckets = mapOf(
            QuestionType.single to mutableListOf<WrongRecord>(),
            QuestionType.multiple to mutableListOf(),
            QuestionType.judge to mutableListOf(),
            QuestionType.fill to mutableListOf(),
            QuestionType.essay to mutableListOf()
        )
        records.forEach { (buckets[it.question?.type] ?: buckets[QuestionType.single])!!.add(it) }
        buckets.values.forEach { it.sortByDescending { r -> scoreOf(r) } }
        val order = listOf(QuestionType.single, QuestionType.multiple, QuestionType.judge, QuestionType.fill, QuestionType.essay)
        val result = mutableListOf<WrongRecord>()
        var added = true
        var i = 0
        while (added) {
            added = false
            for (t in order) {
                val bucket = buckets[t]!!
                if (i < bucket.size) { result.add(bucket[i]); added = true }
            }
            i++
        }
        return result
    }

    /** 推荐前 N 条（含题型多样性） */
    fun recommend(records: List<WrongRecord>, n: Int = 15): List<WrongRecord> {
        val sorted = records.sortedByDescending { scoreOf(it) }
        return orderByType(sorted).take(n)
    }

    /**
     * 推荐前 N 条，附带推荐原因标签。
     * 返回 List<WrongRecord>（rec.question 含答案/选项信息），
     * 调用方自行处理推荐原因渲染。
     */
    fun recommendWithTags(records: List<WrongRecord>, n: Int = 15, banks: List<com.example.shuati.model.QuestionBank> = emptyList()): List<WrongRecord> =
        recommend(records, n)

    /** 分组练习：将同一根题的小问聚合。 */
    fun groupByRoot(records: List<WrongRecord>): List<List<WrongRecord>> {
        if (records.isEmpty()) return emptyList()
        val groups = LinkedHashMap<String, MutableList<WrongRecord>>()
        records.forEach { rec ->
            val root = rootKey(rec.question?.question ?: rec.questionId)
            groups.getOrPut(root) { mutableListOf() }.add(rec)
        }
        return groups.values.toList()
    }

    private fun rootKey(q: String): String {
        val cleaned = q.replace(Regex("[（(]第?\\d+[问号]?[）)]"), "")
            .replace(Regex("\\(第\\d+问\\)"), "")
            .trim()
        return cleaned.ifBlank { q }
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

    /** 计算推荐原因标签列表 */
    fun computeReasons(rec: WrongRecord, banks: List<com.example.shuati.model.QuestionBank>): List<String> {
        val reasons = mutableListOf<String>()
        when {
            rec.wrongCount >= 3 -> reasons.add("高频错题")
            rec.wrongCount >= 2 -> reasons.add("错题")
            else -> reasons.add("偶发错误")
        }
        val qTags = rec.question?.tags?.distinct() ?: emptyList()
        qTags.take(2).forEach { reasons.add(it) }
        return reasons
    }
}
