package com.example.shuati.logic

import com.example.shuati.model.AiExplanation
import com.example.shuati.model.QuestionBank
import com.example.shuati.model.QuestionType
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * 题库导出为 Excel（.xlsx）。
 *
 * 列头与 QuestionTableParser.parseByHeader 完全对齐，因此生成的文件可被导入流程原样解析，
 * 实现「备份 → 还原」的 Excel 往返一致：
 *   题目ID | 题型(中文) | 题干 | 选项A..N | 正确答案 | 解析 | AI解析 | 标签 | 难度 | 不限顺序
 *
 * - 题型以中文写出（单选/多选/判断/填空/简答）；多选答案以逗号分隔；标签以「|」分隔。
 * - 选项列数量动态：默认 4 列（A-D）；某题库选项超过 4 个时自动扩展到实际最大数
 *   （如 6 个选项 → A-F），导入侧亦能自动识别任意「选项X」列。
 * - 新增「AI解析」列：写出 Question.aiExplanation.text，导入时可原样回填，
 *   解决「AI 解析后导出题库不显示 AI 解析内容」的问题。
 * - 新增「难度」列：写出中文（简单/中等/困难），unset 留空；导入时经
 *   QuestionTableParser.parseDifficulty 还原为枚举。
 */
object ExcelWriter {

    /** 选项字母表（A-Z，覆盖任意扩展列）。 */
    private val OPTION_LETTERS = ('A'..'Z').toList()

    /** 题型 → 中文标签（与 ui/components/Common.kt 的 typeLabel 一致）。 */
    private fun typeLabel(type: QuestionType): String = when (type) {
        QuestionType.single -> "单选"
        QuestionType.multiple -> "多选"
        QuestionType.judge -> "判断"
        QuestionType.fill -> "填空"
        QuestionType.essay -> "简答"
    }

    /** 难度 → 中文标签（unset 输出空串，保持表格整洁）。 */
    fun difficultyLabel(d: com.example.shuati.model.QuestionDifficulty): String = when (d) {
        com.example.shuati.model.QuestionDifficulty.easy -> "简单"
        com.example.shuati.model.QuestionDifficulty.normal -> "中等"
        com.example.shuati.model.QuestionDifficulty.hard -> "困难"
        com.example.shuati.model.QuestionDifficulty.unset -> ""
    }

    /**
     * 计算某题库所需的选项列数：至少 4 列（A-D，满足绝大多数题库），
     * 超过则按实际最大选项数扩展（封顶 OPTION_LETTERS.size）。
     */
    private fun optionColumnCount(bank: QuestionBank): Int {
        val maxOpts = bank.questions.maxOfOrNull { it.options?.size ?: 0 } ?: 0
        return maxOf(4, maxOpts).coerceAtMost(OPTION_LETTERS.size)
    }

    /** 生成表头：题干/选项/答案/解析/AI解析/标签/不限顺序，选项列数量由 colCount 决定。 */
    private fun headerFor(colCount: Int): List<String> {
        val header = mutableListOf("题目ID", "题型", "题干")
        OPTION_LETTERS.take(colCount).forEach { header.add("选项$it") }
        header.add("正确答案")
        header.add("解析")
        header.add("AI解析")
        header.add("标签")
        header.add("难度")
        header.add("不限顺序")
        return header
    }

    /** 按当前题库的选项数构建 workbook（表头 + 题目行）。供各导出入口复用。 */
    private fun buildWorkbook(bank: QuestionBank): XSSFWorkbook {
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet("题库")

        val colCount = optionColumnCount(bank)
        val header = headerFor(colCount)
        val hrow = sheet.createRow(0)
        header.forEachIndexed { i, name -> hrow.createCell(i).setCellValue(name) }

        val colOptions = 3
        val colAnswer = colOptions + colCount
        val colExplanation = colAnswer + 1
        val colAi = colExplanation + 1
        val colTags = colAi + 1
        val colDifficulty = colTags + 1
        val colOrderFree = colDifficulty + 1

        bank.questions.forEachIndexed { idx, q ->
            val row = sheet.createRow(idx + 1)
            row.createCell(0).setCellValue(q.id)
            row.createCell(1).setCellValue(typeLabel(q.type))
            row.createCell(2).setCellValue(q.question)

            val opts = q.options ?: emptyList()
            opts.take(colCount).forEachIndexed { i, opt ->
                row.createCell(colOptions + i).setCellValue(opt)
            }

            row.createCell(colAnswer).setCellValue(q.answer ?: "")
            q.explanation?.let { row.createCell(colExplanation).setCellValue(it) }
            q.aiExplanation?.text?.let { row.createCell(colAi).setCellValue(it) }
            if (q.tags.isNotEmpty()) row.createCell(colTags).setCellValue(q.tags.joinToString("|"))
            difficultyLabel(q.difficulty).takeIf { it.isNotEmpty() }
                ?.let { row.createCell(colDifficulty).setCellValue(it) }
            row.createCell(colOrderFree).setCellValue(if (q.orderFree) "是" else "否")
        }
        return wb
    }

    /** 备份用：直接产出 xlsx 字节数组（供 ZIP 内嵌）。 */
    fun writeBankToBytes(bank: QuestionBank): ByteArray {
        val wb = buildWorkbook(bank)
        val out = ByteArrayOutputStream()
        try {
            wb.write(out)
        } finally {
            wb.close()
        }
        return out.toByteArray()
    }

    /** 单题库导出（题库页「导出」按钮）：写入给定 OutputStream。 */
    fun writeBank(bank: QuestionBank, out: OutputStream) {
        val wb = buildWorkbook(bank)
        try {
            wb.write(out)
        } finally {
            wb.close()
        }
    }

    /**
     * 下载导入模板：表头 + 三行示例，供用户在 Excel/WPS 中填写后导入。
     * 默认仅显示四个选项列（A、B、C、D）；示例行与表头严格对齐（每行列数 = 表头列数），
     * 题型使用中文，并含「AI解析」列（示例留空，提示用户可填）。
     */
    fun writeTemplate(out: OutputStream) {
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet("题库")
        // 默认 4 个选项列
        val header = headerFor(4)
        val hrow = sheet.createRow(0)
        header.forEachIndexed { i, name -> hrow.createCell(i).setCellValue(name) }

        // 列顺序（与 headerFor(4) 一致，共 13 列）：
        // 0题目ID 1题型 2题干 3选项A 4选项B 5选项C 6选项D 7正确答案 8解析 9AI解析 10标签 11难度 12不限顺序
        val example = listOf(
            listOf("q1", "单选", "下列哪一项是正确的？", "一", "二", "三", "四", "A", "解析示例", "", "标签1|标签2", "简单", "否"),
            listOf("q2", "判断", "判断题示例：地球是圆的。", "", "", "", "", "正确", "解析示例", "", "", "中等", "否"),
            listOf("q3", "填空", "中国的首都是____。", "", "", "", "", "北京", "", "", "", "困难", "否")
        )
        example.forEachIndexed { r, cols ->
            val row = sheet.createRow(r + 1)
            cols.forEachIndexed { c, v -> if (v.isNotEmpty()) row.createCell(c).setCellValue(v) }
        }
        try {
            wb.write(out)
        } finally {
            wb.close()
        }
    }
}
