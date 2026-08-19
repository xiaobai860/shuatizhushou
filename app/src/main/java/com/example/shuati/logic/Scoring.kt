package com.example.shuati.logic

import com.example.shuati.model.Question
import com.example.shuati.model.QuestionType

/**
 * 判分与查重核心逻辑。等价小程序 utils/similarity.ts + pages/quiz 判分逻辑。
 */

object Scoring {

    /** 归一化：去首尾空白、合并中间空白 */
    private fun normalize(s: String?): String =
        (s ?: "").trim().replace(Regex("\\s+"), " ")

    /** 评估模式：宽松（判分用） */
    private fun loose(s: String?): String = normalize(s).lowercase()

    /** 加强模式：去标点符号（查重用） */
    private fun strict(s: String?): String =
        loose(s).replace(Regex("[^a-z0-9\\u4e00-\\u9fa5]"), "")

    /** 宽松相等（判分） */
    fun looseEqual(a: String?, b: String?): Boolean = loose(a) == loose(b)

    /** 严格相等（查重 / 相似判定） */
    fun strictEqual(a: String?, b: String?): Boolean = strict(a) == strict(b)

    /**
     * 字符级相似度（Dice 系数）。
     * 等价 similarity.ts similarity(s1, s2)。
     */
    fun similarity(s1: String?, s2: String?): Double {
        val a = normalize(s1)
        val b = normalize(s2)
        if (a == b) return 1.0
        if (a.isEmpty() && b.isEmpty()) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0

        val bigramsA = bigrams(a)
        val bigramsB = bigrams(b)
        if (bigramsA.isEmpty() || bigramsB.isEmpty()) return 0.0

        var overlap = 0
        val countsB = bigramsB.groupingBy { it }.eachCount().toMutableMap()
        for (bg in bigramsA) {
            val c = countsB[bg]
            if (c != null && c > 0) {
                overlap++
                countsB[bg] = c - 1
            }
        }
        return 2.0 * overlap / (bigramsA.size + bigramsB.size)
    }

    private fun bigrams(s: String): List<String> {
        if (s.length <= 1) return listOf(s)
        val result = ArrayList<String>(s.length - 1)
        for (i in 0 until s.length - 1) {
            result.add(s.substring(i, i + 2))
        }
        return result
    }

    /**
     * 判断题目是否为「疑似错题 / 数据质量问题」。
     * 等价 storage.ts checkIsSuspect。
     */
    fun checkIsSuspect(q: Question): Boolean {
        if (q.incomplete || q.idProblem) return true
        if (q.type != QuestionType.single && q.type != QuestionType.multiple) return false
        val opts = (q.options ?: emptyList()).map { it.trim() }.filter { it.isNotEmpty() }
        if (opts.size != opts.toSet().size) return true   // 选项重复
        if (q.type == QuestionType.multiple) {
            val answerArr = (q.answer ?: "").let { a ->
                if (a.isBlank()) emptyList() else listOf(a.trim())
            }.filter { it.isNotEmpty() }
            if (answerArr.size == 1) {
                val only = answerArr[0].trim()
                if (only.isNotEmpty() && !Regex("[,，|｜、]").containsMatchIn(only)) return true
            }
        }
        return false
    }

    /**
     * 拆分答案为选项索引集合（0-based）。
     * 兼容多种写法：
     *   - "A" / "B,C" / "AB" / "A B" / "1" / "2,3" / "1 2"
     *   - 选项文本本身（当选项无字母前缀时回退匹配文本）
     * 等价 quiz/index.tsx 的 parseAnswer。
     */
    fun parseAnswerIndices(raw: String?, options: List<String>?): Set<Int> {
        if (raw.isNullOrBlank() || options.isNullOrEmpty()) return emptySet()
        val cleaned = raw.trim().uppercase()

        // 1) 尝试字母形式：单个字母(1个)、逗号/顿号分隔、或连续字母
        val letterPattern = Regex("^[A-Z](,[A-Z]|、[A-Z]|\$|[A-Z])*\$")
        if (Regex("^[A-Z]\$").matches(cleaned)) {
            val idx = cleaned[0] - 'A'
            if (idx in options.indices) return setOf(idx)
        } else if (cleaned.contains(Regex("[,，、]"))) {
            val set = cleaned.split(Regex("[,，、]"))
                .mapNotNull { it.trim().takeIf { t -> t.isNotEmpty() }?.get(0) }
                .map { it - 'A' }
                .filter { it in options.indices }
                .toSet()
            if (set.isNotEmpty()) return set
        } else if (Regex("^[A-Z]{2,}\$").matches(cleaned)) {
            // 连续字母 AB / ABC
            val set = cleaned.map { it - 'A' }.filter { it in options.indices }.toSet()
            if (set.isNotEmpty()) return set
        }

        // 2) 数字形式（从 1 开始）
        val numPattern = Regex("^\\d+\$")
        if (numPattern.matches(cleaned)) {
            val idx = cleaned.toInt() - 1
            if (idx in options.indices) return setOf(idx)
        }
        val numList = cleaned.split(Regex("[,，、\\s]+"))
            .mapNotNull { it.trim().takeIf { t -> t.isNotEmpty() } }
            .filter { numPattern.matches(it) }
            .map { it.toInt() - 1 }
            .filter { it in options.indices }
            .toSet()
        if (numList.isNotEmpty()) return numList

        // 3) 回退：直接按选项文本匹配（宽松去空白）
        val normRaw = normalize(raw)
        val matched = options.mapIndexedNotNull { i, opt ->
            if (looseEqual(opt, normRaw)) i else null
        }.toSet()
        if (matched.isNotEmpty()) return matched

        return emptySet()
    }

    /**
     * 判分主函数。等价 quiz/index.tsx judge(userAnswer, question)。
     *
     * @param userAnswer 用户作答：
     *   - 选择类(single/multiple)：Set<Int>（选项 0-based 索引）
     *   - 判断：Boolean（true=正确/对）
     *   - 填空/简答：String
     * @return 是否答对
     */
    fun judge(userAnswer: Any?, q: Question): Boolean {
        return when (q.type) {
            QuestionType.single, QuestionType.multiple -> {
                val correct = parseAnswerIndices(q.answer, q.options)
                val user = userAnswer as? Set<*>
                val userIdx = user?.filterIsInstance<Int>()?.toSet() ?: emptySet()
                correct == userIdx
            }
            QuestionType.judge -> {
                // 标准答案应为 "正确/对/√/true/1/yes" 或 "错误/错/×/false/0/no"
                val userBool = userAnswer as? Boolean ?: return false
                val std = standardizeJudge(q.answer)
                userBool == std
            }
            QuestionType.fill, QuestionType.essay -> {
                val userStr = (userAnswer as? String)?.let { normalize(it) } ?: ""
                val stdStr = normalize(q.answer)
                // 填空/简答：严格去空白相等（支持多答案用 | 或 / 或 、 分隔）
                if (userStr.isEmpty() || stdStr.isEmpty()) return false
                stdStr.split(Regex("[|／/、]")).any { ans ->
                    looseEqual(ans, userStr)
                }
            }
        }
    }

    /** 将判断类答案标准化为 Boolean（true=正确）。空或非错即视为正确。 */
    fun standardizeJudge(raw: String?): Boolean {
        val s = loose(raw)
        return when {
            s in setOf("错误", "错", "×", "x", "false", "0", "no", "否", "不对") -> false
            s in setOf("正确", "对", "√", "v", "true", "1", "yes", "是") -> true
            else -> {
                // 兜底：含"错误/不对/错"字样判错，否则判对
                Regex("(错误|不对|错)").containsMatchIn(s)
            }
        }
    }

    /**
     * 填空多空判分。等价 quiz/index.tsx 的 checkCorrect（fill 分支）+ getFillSlotCorrectness。
     *
     * @param userInputs 用户逐空输入（每空一个字符串，可能为空串）
     * @param orderFree 是否不限顺序（仅多空有效；用户可手动切换）
     * @return 所有空均答对才为 true
     */
    fun judgeFill(userInputs: List<String>, q: Question, orderFree: Boolean = false): Boolean {
        val correct = (q.answer ?: "").split(Regex("[|｜]"))
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
        val user = userInputs.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        if (correct.isEmpty() || user.isEmpty()) return false
        if (correct.size != user.size) return false
        if (orderFree && correct.size > 1) {
            // 顺序无关：集合完全一致即可
            return correct.sorted() == user.sorted()
        }
        return correct.indices.all { i -> correct[i] == user[i] }
    }

    /** 填空答案的空数（按 | 分隔，至少 1）。 */
    fun fillSlotCount(q: Question): Int {
        val parts = (q.answer ?: "").split(Regex("[|｜]"))
            .map { it.trim() }.filter { it.isNotEmpty() }
        return maxOf(parts.size, 1)
    }

    /**
     * 填空多空逐空判对（用于提交后逐空标色）。
     * 等价小程序 quiz/index.tsx 的 getFillSlotCorrectness。
     */
    fun fillSlotCorrectness(userSlots: List<String>, q: Question, orderFree: Boolean): List<Boolean> {
        val correct = (q.answer ?: "").split(Regex("[|｜]"))
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
        val user = userSlots.map { it.trim().lowercase() }
        if (correct.size != user.size) return user.map { false }
        if (!orderFree || correct.size <= 1) {
            return user.indices.map { i -> user[i] == correct[i] }
        }
        // 顺序无关：贪心匹配（先锁定位置相同，剩余空位从未用答案里认领）
        val used = MutableList(correct.size) { false }
        val result = MutableList(user.size) { false }
        for (i in user.indices) {
            if (user[i] == correct[i]) { result[i] = true; used[i] = true }
        }
        for (i in user.indices) {
            if (result[i]) continue
            val found = correct.indices.find { !used[it] && correct[it] == user[i] }
            if (found != null) { result[i] = true; used[found] = true }
        }
        return result
    }
}
