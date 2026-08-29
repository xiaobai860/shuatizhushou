package com.example.shuati.logic

import com.example.shuati.model.QuestionBank
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * 题库导出为 Excel（.xlsx）。
 *
 * 列头与 ExcelParser.parseByHeader 完全对齐，因此生成的文件可被导入流程原样解析，
 * 实现「备份 → 还原」的 Excel 往返一致：
 *   题目ID | 题型 | 题干 | 选项A..G | 正确答案 | 解析 | 标签 | 不限顺序
 *
 * 题型以枚举名（single/multiple/judge/fill/essay）写出；多选答案以逗号分隔；
 * 标签以「|」分隔；不限顺序仅多空填空有效，写「是/否」。
 */
object ExcelWriter {

    private val HEADER = listOf(
        "题目ID", "题型", "题干",
        "选项A", "选项B", "选项C", "选项D", "选项E", "选项F", "选项G",
        "正确答案", "解析", "标签", "不限顺序"
    )

    private const val COL_OPTIONS = 3   // 选项A 起始列
    private const val COL_ANSWER = 10
    private const val COL_EXPLANATION = 11
    private const val COL_TAGS = 12
    private const val COL_ORDER_FREE = 13

    /** 把题库写入一个 XSSFWorkbook（表头 + 题目行）。供下方各导出入口复用。 */
    private fun buildWorkbook(bank: QuestionBank): XSSFWorkbook {
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet("题库")

        val hrow = sheet.createRow(0)
        HEADER.forEachIndexed { i, name -> hrow.createCell(i).setCellValue(name) }

        bank.questions.forEachIndexed { idx, q ->
            val row = sheet.createRow(idx + 1)
            row.createCell(0).setCellValue(q.id)
            row.createCell(1).setCellValue(q.type.name)
            row.createCell(2).setCellValue(q.question)

            val opts = q.options ?: emptyList()
            for (i in 0 until 7) {
                opts.getOrNull(i)?.let { row.createCell(COL_OPTIONS + i).setCellValue(it) }
            }

            row.createCell(COL_ANSWER).setCellValue(q.answer ?: "")
            q.explanation?.let { row.createCell(COL_EXPLANATION).setCellValue(it) }
            if (q.tags.isNotEmpty()) row.createCell(COL_TAGS).setCellValue(q.tags.joinToString("|"))
            row.createCell(COL_ORDER_FREE).setCellValue(if (q.orderFree) "是" else "否")
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

    /** 下载导入模板：表头 + 两行示例，供用户在 Excel/WPS 中填写后导入。 */
    fun writeTemplate(out: OutputStream) {
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet("题库")
        val hrow = sheet.createRow(0)
        HEADER.forEachIndexed { i, name -> hrow.createCell(i).setCellValue(name) }

        val example = listOf(
            listOf("q1", "single", "下列哪一项是正确的？", "A.一", "B.二", "C.三", "D.四", "", "", "A", "解析示例", "标签1|标签2", "否"),
            listOf("q2", "judge", "判断题示例：地球是圆的。", "", "", "", "", "", "", "正确", "解析示例", "", "否")
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
