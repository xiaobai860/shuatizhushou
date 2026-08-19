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
 * Excel 题库解析。等价小程序 import/index.tsx 的解析逻辑（B/C 列格式）。
 *
 * 格式约定（与小程序完全一致）：
 *   B 列：题干（必填，空白行跳过）
 *   C 列：答案（必填，支持多答案）
 *   D 列：选项（竖线 | 分隔；判断/填空/简答可空）
 *   E 列：解析（可空）
 *   F 列：标签（竖线 | 分隔，可空）
 *   G 列：题型（可空，默认按选项自动推断）
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
        val questions = ArrayList<Question>()
        var skipped = 0

        for (row in sheet) {
            if (row.rowNum == 0) continue   // 跳过表头
            val questionText = row.getCell(1)?.toString()?.trim().orEmpty()  // B 列
            val answerText = row.getCell(2)?.toString()?.trim().orEmpty()    // C 列
            if (questionText.isEmpty() || answerText.isEmpty()) {
                if (questionText.isEmpty()) continue
                skipped++
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
