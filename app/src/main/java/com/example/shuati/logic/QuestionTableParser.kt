package com.example.shuati.logic

import com.example.shuati.model.AiExplanation
import com.example.shuati.model.Question
import com.example.shuati.model.QuestionDifficulty
import com.example.shuati.model.QuestionType
import java.util.UUID

/**
 * 表格题库解析内核。
 *
 * 被多种文件格式共用（.xlsx / .csv），各类格式只需把自己的表格
 * 适配成 [Table] 接口，即可复用同一套「表头识别 → 列定位 → 题型推断 →
 * 答案规整 → ID 去重」逻辑，避免多份实现各自演进导致行为不一致。
 *
 * 支持两种表格形态：
 * 1. 表头格式：首行含「题干 / 正确答案 / 题型」等列名时，按列名定位列
 * 2. 固定列格式（向后兼容旧文件）：B 列题干、C 列答案、D 列选项、E 列解析、F 列标签、G 列题型
 */
object QuestionTableParser {

    /** 表格的最小抽象：行数 + 按行取单元格文本。 */
    interface Table {
        /** 最后一行索引（0-based），无数据时返回负数。 */
        val lastRowIndex: Int

        /** 取第 rowIndex 行；行不存在时返回 null。 */
        fun row(rowIndex: Int): List<String>?

        /** 顺序迭代所有存在的数据行（行号可能不连续）。 */
        fun rows(): Sequence<Pair<Int, List<String>>> =
            (0..lastRowIndex).asSequence().mapNotNull { i -> row(i)?.let { i to it } }
    }

    data class ParseResult(
        val questions: List<Question>,
        val totalRows: Int,
        val imported: Int,
        val skipped: Int
    )

    fun parse(table: Table): ParseResult {
        val header = table.row(0).orEmpty()
        val hasHeader = header.any { it.contains("题干") } ||
                header.any { it.contains("正确答案") } ||
                header.any { it.contains("答案") }

        val questions = ArrayList<Question>()
        var skipped = 0
        if (hasHeader) {
            parseByHeader(table, header, questions) { skipped++ }
        } else {
            parseFixedColumns(table, questions) { skipped++ }
            // 固定列模式是为兼容旧文件，无法可靠区分「旧格式题库」与「任意三列表格」。
            // 若一条题都没解析出来，说明表格形态不对，明确报错而非导入空题库。
            if (questions.isEmpty() && table.lastRowIndex >= 1) {
                throw IllegalArgumentException(
                    "未能解析出任何题目：未找到「题干 / 正确答案」表头，请下载模板填写后导入。"
                )
            }
        }
        return ParseResult(
            questions = questions,
            totalRows = table.lastRowIndex,
            imported = questions.size,
            skipped = skipped
        )
    }

    // ─── 格式 1：按表头列名定位（与模板 / 导出格式一致） ─────────────────────
    private fun parseByHeader(
        table: Table,
        headerCells: List<String>,
        questions: MutableList<Question>,
        onSkip: () -> Unit
    ) {
        fun colIdx(pred: (String) -> Boolean): Int = headerCells.indexOfFirst(pred)
        val idIdx = colIdx { it.contains("题目ID") || it.equals("ID", true) || it.equals("题目编号", true) }
        val typeIdx = colIdx { it.contains("题型") }
        val questionIdx = colIdx { it.contains("题干") || it.equals("题目", true) }
        val answerIdx = colIdx { it.contains("正确答案") || it.contains("答案") }
        // 解析列：匹配"解析"，但排除"AI解析"列（避免列冲突）
        val explanationIdx = colIdx { h ->
            val t = h.trim()
            (t.equals("解析", true) || (t.endsWith("解析") && !t.contains("AI")))
        }
        val tagsIdx = colIdx { it.contains("标签") }
        val difficultyIdx = colIdx { it.contains("难度") }
        val orderFreeIdx = colIdx { it.contains("不限顺序") || it.contains("order") }
        val aiExplanationIdx = colIdx { it.trim().contains("AI解析") }
        // 选项列：动态识别所有「选项X」（X 为任意字母），支持扩展列（E、F…）
        val optionColumns = LinkedHashMap<Char, Int>()
        headerCells.forEachIndexed { i, h ->
            val m = Regex("^选项([A-Za-z])$").find(h.trim())
            if (m != null) optionColumns[m.groupValues[1].uppercase().first()] = i
        }

        // 第一遍：统计非空 rawId 出现次数，识别重复 ID
        val rawIdCount = HashMap<String, Int>()
        for ((_, cells) in table.rows()) {
            if (cells.all { it.isEmpty() }) continue
            val rawId = if (idIdx >= 0) cells.getOrNull(idIdx).orEmpty() else ""
            if (rawId.isNotEmpty()) rawIdCount[rawId] = (rawIdCount[rawId] ?: 0) + 1
        }
        val dupIds = rawIdCount.filter { it.value > 1 }.keys

        val usedIds = HashSet<String>()
        val dupCounters = HashMap<String, Int>()
        for ((rowIndex, cells) in table.rows()) {
            if (rowIndex == 0) continue
            if (cells.all { it.isEmpty() }) continue
            fun cell(idx: Int): String = if (idx >= 0) cells.getOrNull(idx).orEmpty() else ""

            val typeRaw = cell(typeIdx).trim().lowercase()
            val explicitType = parseTypeRaw(typeRaw)
            val questionText = cell(questionIdx)
            val answerRaw = cell(answerIdx)
            val opts = optionColumns.values.mapNotNull { c -> cell(c).trim().ifEmpty { null } }

            val resolvedType = explicitType ?: when {
                opts.size >= 2 -> QuestionType.single
                Regex("(正确|错误|对|错|√|×|true|false)").containsMatchIn(answerRaw.lowercase()) -> QuestionType.judge
                else -> QuestionType.fill
            }

            val explanation = cell(explanationIdx).trim().ifEmpty { null }
            val aiText = if (aiExplanationIdx >= 0) cell(aiExplanationIdx).trim() else ""
            val tags = if (tagsIdx >= 0)
                cell(tagsIdx).split(Regex("[,，、|｜]")).map { it.trim() }.filter { it.isNotEmpty() }
            else emptyList()
            val difficulty = if (difficultyIdx >= 0) parseDifficulty(cell(difficultyIdx)) else QuestionDifficulty.unset

            // ID 处理：缺失 / 重复自动纠正
            val rawId = if (idIdx >= 0) cell(idIdx).trim() else ""
            val isMissingId = rawId.isEmpty()
            val isDupId = rawId.isNotEmpty() && dupIds.contains(rawId)
            val finalId: String = when {
                isMissingId -> {
                    var candidate = "__missing_$rowIndex"; var s = 2
                    while (usedIds.contains(candidate)) candidate = "__missing_${rowIndex}_${s++}"
                    candidate
                }
                isDupId -> {
                    val cnt = (dupCounters[rawId] ?: 0) + 1
                    dupCounters[rawId] = cnt
                    if (cnt == 1) rawId else "${rawId}_dup$cnt"
                }
                else -> rawId
            }
            usedIds.add(finalId)

            val isIncomplete = explicitType == null || questionText.isEmpty() || answerRaw.isEmpty() ||
                    ((resolvedType == QuestionType.single || resolvedType == QuestionType.multiple) && opts.size < 2)

            val normalizedAnswer = when (resolvedType) {
                QuestionType.judge -> if (Scoring.standardizeJudge(answerRaw)) "正确" else "错误"
                else -> answerRaw
            }

            // 不限顺序：仅多空填空有效，填"是/yes/1/true"开启
            val orderFreeRaw = if (orderFreeIdx >= 0) cell(orderFreeIdx).trim().lowercase() else ""
            val isOrderFree = resolvedType == QuestionType.fill &&
                    normalizedAnswer.split(Regex("[|｜]")).count { it.isNotBlank() } > 1 &&
                    Regex("^(是|yes|1|true)$").containsMatchIn(orderFreeRaw)

            questions.add(
                Question(
                    id = finalId,
                    question = questionText.ifEmpty { "（第${rowIndex + 1}行，题干缺失）" },
                    options = opts.ifEmpty { null },
                    answer = normalizedAnswer,
                    type = resolvedType,
                    explanation = explanation,
                    aiExplanation = aiText.ifEmpty { null }?.let { AiExplanation(text = it) },
                    tags = tags,
                    difficulty = difficulty,
                    orderFree = isOrderFree,
                    incomplete = isIncomplete || isMissingId || isDupId,
                    idProblem = isMissingId || isDupId
                )
            )
            if (isIncomplete || isMissingId || isDupId) onSkip()
        }
    }

    // ─── 格式 2：固定列（B/C/D/E/F/G），向后兼容 ────────────────────────────
    private fun parseFixedColumns(
        table: Table,
        questions: MutableList<Question>,
        onSkip: () -> Unit
    ) {
        for ((rowIndex, cells) in table.rows()) {
            if (rowIndex == 0) continue
            val questionText = cells.getOrNull(1).orEmpty()   // B 列
            val answerText = cells.getOrNull(2).orEmpty()      // C 列
            if (questionText.isEmpty() || answerText.isEmpty()) {
                if (questionText.isEmpty()) continue
                onSkip()
                continue
            }
            val optionsRaw = cells.getOrNull(3).orEmpty()      // D 列
            val explanation = cells.getOrNull(4).orEmpty()     // E 列
            val tagsRaw = cells.getOrNull(5).orEmpty()         // F 列
            val typeRaw = cells.getOrNull(6).orEmpty()         // G 列

            val tags = if (tagsRaw.isNotEmpty())
                tagsRaw.split(Regex("\\|")).map { it.trim() }.filter { it.isNotEmpty() }
            else emptyList()

            val explicitType = parseTypeRaw(typeRaw)
            val options = if (optionsRaw.isNotEmpty())
                optionsRaw.split(Regex("\\|")).map { it.trim() }.filter { it.isNotEmpty() }
            else null
            val type = explicitType ?: when {
                optionsRaw.isNotEmpty() -> QuestionType.single
                Regex("(正确|错误|对|错|√|×|true|false)").containsMatchIn(answerText.lowercase()) -> QuestionType.judge
                else -> QuestionType.fill
            }
            val normalizedAnswer = if (type == QuestionType.judge) {
                if (Scoring.standardizeJudge(answerText)) "正确" else "错误"
            } else answerText

            questions.add(
                Question(
                    id = UUID.randomUUID().toString(),
                    question = questionText,
                    options = options,
                    answer = normalizedAnswer,
                    type = type,
                    explanation = explanation.ifEmpty { null },
                    tags = tags,
                    incomplete = (type == QuestionType.single || type == QuestionType.multiple)
                            && (options.isNullOrEmpty() || normalizedAnswer.isNullOrBlank())
                )
            )
        }
    }

    /** 题型文本 → 枚举；中英文均可，无法识别返回 null（交由调用方自动推断）。 */
    fun parseTypeRaw(raw: String): QuestionType? = when (raw.trim().lowercase()) {
        "single", "单选", "单选题" -> QuestionType.single
        "multiple", "多选", "多选题" -> QuestionType.multiple
        "judge", "判断", "判断题" -> QuestionType.judge
        "fill", "填空", "填空题" -> QuestionType.fill
        "essay", "简答", "简答题" -> QuestionType.essay
        else -> null
    }

    /** 难度文本 → 枚举；支持中文与英文，未识别/空值一律为 unset。 */
    fun parseDifficulty(raw: String): QuestionDifficulty = when (raw.trim().lowercase()) {
        "简单", "easy", "易", "1" -> QuestionDifficulty.easy
        "中等", "normal", "中", "普通", "2" -> QuestionDifficulty.normal
        "困难", "hard", "难", "3" -> QuestionDifficulty.hard
        else -> QuestionDifficulty.unset
    }
}
