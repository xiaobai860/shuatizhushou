package com.example.shuati.logic

import android.content.Context
import android.net.Uri
import com.example.shuati.model.Question
import com.example.shuati.model.QuestionBank
import com.example.shuati.model.QuestionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream
import java.util.UUID

/**
 * Excel 题库解析。等价小程序 import/index.tsx 的解析逻辑。
 *
 * 支持两种格式：
 * 1. 表头格式（与小程序模板/导出一致）：第一行含"题干/正确答案/题型"等列名时，按列名定位
 *    （题目ID | 题型 | 题干 | 选项A-G | 正确答案 | 解析 | 标签 | 不限顺序）
 * 2. 固定列格式（向后兼容）：B 列题干、C 列答案、D 列选项（| 分隔）、E 列解析、F 列标签（| 分隔）、G 列题型
 */
object ExcelParser {

    data class ParseResult(
        val bank: QuestionBank,
        val totalRows: Int,
        val imported: Int,
        val skipped: Int
    )

    /** 从 Uri（文档选择器返回）解析 */
    suspend fun parseUri(
        context: Context,
        uri: Uri,
        bankName: String
    ): ParseResult = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            parseStream(stream, bankName)
        } ?: throw IllegalStateException("无法打开文件")
    }

    /** 从 InputStream 解析（供单测 / 备份导入复用） */
    fun parseStream(stream: InputStream, bankName: String): ParseResult {
        val workbook = XSSFWorkbook(stream)
        val sheet = workbook.getSheetAt(0)

        // 尝试识别表头格式：第一行包含"题干 / 正确答案 / 题型"等列名
        val headerCells = sheet.getRow(0)?.let { row ->
            (0 until row.lastCellNum.toInt()).map { c -> row.getCell(c)?.toString()?.trim().orEmpty() }
        } ?: emptyList()
        val hasHeader = headerCells.any { it.contains("题干") } ||
                headerCells.any { it.contains("正确答案") } ||
                headerCells.any { it.contains("答案") }

        val questions = ArrayList<Question>()
        var skipped = 0

        if (hasHeader) {
            parseByHeader(sheet, headerCells, questions) { skipped++ }
        } else {
            parseFixedColumns(sheet, questions) { skipped++ }
        }
        workbook.close()

        val now = System.currentTimeMillis()
        return ParseResult(
            bank = QuestionBank(
                id = UUID.randomUUID().toString(),
                name = bankName,
                questions = questions,
                createdAt = now,
                updatedAt = now
            ),
            totalRows = sheet.lastRowNum,
            imported = questions.size,
            skipped = skipped
        )
    }

    // ─── 格式 1：按表头列名定位（与小程序模板 / 导出格式一致） ──────────────
    private fun parseByHeader(
        sheet: org.apache.poi.ss.usermodel.Sheet,
        headerCells: List<String>,
        questions: MutableList<Question>,
        onSkip: () -> Unit
    ) {
        fun colIdx(pred: (String) -> Boolean): Int = headerCells.indexOfFirst(pred)
        val idIdx = colIdx { it.contains("题目ID") || it.equals("ID", true) || it.equals("题目编号", true) }
        val typeIdx = colIdx { it.contains("题型") }
        val questionIdx = colIdx { it.contains("题干") || it.equals("题目", true) }
        val answerIdx = colIdx { it.contains("正确答案") || it.contains("答案") }
        val explanationIdx = colIdx { it.contains("解析") }
        val tagsIdx = colIdx { it.contains("标签") }
        val orderFreeIdx = colIdx { it.contains("不限顺序") || it.contains("order") }
        val optIdx = ('A'..'G').map { l ->
            colIdx { h -> Regex("选项$l|^$l$|opt$l|option_${l.lowercase()}", RegexOption.IGNORE_CASE).containsMatchIn(h.trim()) }
        }

        // 第一遍：收集非空 rawId，找出重复值
        val rawIdCount = HashMap<String, Int>()
        for (i in 1..sheet.lastRowNum) {
            val row = sheet.getRow(i) ?: continue
            val cells = rowCells(row)
            if (cells.all { it.isEmpty() }) continue
            val rawId = if (idIdx >= 0) cells.getOrNull(idIdx).orEmpty() else ""
            if (rawId.isNotEmpty()) rawIdCount[rawId] = (rawIdCount[rawId] ?: 0) + 1
        }
        val dupIds = rawIdCount.filter { it.value > 1 }.keys

        val usedIds = HashSet<String>()
        val dupCounters = HashMap<String, Int>()
        for (i in 1..sheet.lastRowNum) {
            val row = sheet.getRow(i) ?: continue
            val cells = rowCells(row)
            if (cells.all { it.isEmpty() }) continue
            fun cell(idx: Int): String = if (idx >= 0) cells.getOrNull(idx).orEmpty() else ""

            val typeRaw = cell(typeIdx).trim().lowercase()
            val explicitType = when (typeRaw) {
                "single", "单选", "单选题" -> QuestionType.single
                "multiple", "多选", "多选题" -> QuestionType.multiple
                "judge", "判断", "判断题" -> QuestionType.judge
                "fill", "填空", "填空题" -> QuestionType.fill
                "essay", "简答", "简答题" -> QuestionType.essay
                else -> null
            }
            val questionText = cell(questionIdx)
            val answerRaw = cell(answerIdx)
            val opts = optIdx.mapNotNull { c -> cell(c).trim().ifEmpty { null } }

            val resolvedType = explicitType ?: run {
                // 自动推断
                when {
                    opts.size >= 2 -> QuestionType.single
                    Regex("(正确|错误|对|错|√|×|true|false)").containsMatchIn(answerRaw.lowercase()) -> QuestionType.judge
                    else -> QuestionType.fill
                }
            }

            val explanation = cell(explanationIdx).trim().ifEmpty { null }
            val tags = if (tagsIdx >= 0)
                cell(tagsIdx).split(Regex("[,，、|｜]")).map { it.trim() }.filter { it.isNotEmpty() }
            else emptyList()

            // ID 处理（与小程序一致：缺失/重复自动纠正）
            val rawId = if (idIdx >= 0) cell(idIdx).trim() else ""
            val isMissingId = rawId.isEmpty()
            val isDupId = rawId.isNotEmpty() && dupIds.contains(rawId)
            val finalId: String = when {
                isMissingId -> {
                    var candidate = "__missing_$i"; var s = 2
                    while (usedIds.contains(candidate)) candidate = "__missing_${i}_${s++}"
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

            // 答案规整：判断类转标准字符串；填空保留多空 | 分隔
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
                    question = questionText.ifEmpty { "（第${i + 1}行，题干缺失）" },
                    options = opts.ifEmpty { null },
                    answer = normalizedAnswer,
                    type = resolvedType,
                    explanation = explanation,
                    tags = tags,
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
        sheet: org.apache.poi.ss.usermodel.Sheet,
        questions: MutableList<Question>,
        onSkip: () -> Unit
    ) {
        for (row in sheet) {
            if (row.rowNum == 0) continue   // 跳过表头
            val questionText = row.getCell(1)?.toString()?.trim().orEmpty()  // B 列
            val answerText = row.getCell(2)?.toString()?.trim().orEmpty()    // C 列
            if (questionText.isEmpty() || answerText.isEmpty()) {
                if (questionText.isEmpty()) continue
                onSkip()
                continue
            }
            val optionsRaw = row.getCell(3)?.toString()?.trim().orEmpty()    // D 列
            val explanation = row.getCell(4)?.toString()?.trim().orEmpty()   // E 列
            val tagsRaw = row.getCell(5)?.toString()?.trim().orEmpty()       // F 列
            val typeRaw = row.getCell(6)?.toString()?.trim().orEmpty()       // G 列

            val tags = if (tagsRaw.isNotEmpty())
                tagsRaw.split(Regex("\\|")).map { it.trim() }.filter { it.isNotEmpty() }
            else emptyList()

            val (type, options, normalizedAnswer) = resolveTypeAndOptions(typeRaw, optionsRaw, answerText)

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

    private fun rowCells(row: org.apache.poi.ss.usermodel.Row): List<String> =
        (0 until row.lastCellNum.toInt()).map { c -> row.getCell(c)?.toString()?.trim().orEmpty() }

    /**
     * 自动推断题型与规整答案。等价 import 页 getCell(6)/default 分支逻辑。
     */
    private fun resolveTypeAndOptions(
        typeRaw: String,
        optionsRaw: String,
        answerText: String
    ): Triple<QuestionType, List<String>?, String?> {
        val options = if (optionsRaw.isNotEmpty())
            optionsRaw.split(Regex("\\|")).map { it.trim() }.filter { it.isNotEmpty() }
        else null

        val type = when (typeRaw.lowercase()) {
            "single", "单选", "单选题" -> QuestionType.single
            "multiple", "多选", "多选题" -> QuestionType.multiple
            "judge", "判断", "判断题" -> QuestionType.judge
            "fill", "填空", "填空题" -> QuestionType.fill
            "essay", "简答", "简答题" -> QuestionType.essay
            else -> {
                // 自动推断
                when {
                    optionsRaw.isNotEmpty() -> QuestionType.single
                    Regex("(正确|错误|对|错|√|×|true|false)").containsMatchIn(answerText.lowercase()) ->
                        QuestionType.judge
                    else -> QuestionType.fill
                }
            }
        }

        // 答案规整：判断类转标准字符串
        val normalizedAnswer = when (type) {
            QuestionType.judge -> {
                // 转成 "正确"/"错误" 以便统一判分
                if (Scoring.standardizeJudge(answerText)) "正确" else "错误"
            }
            else -> answerText
        }

        return Triple(type, options, normalizedAnswer)
    }
}
