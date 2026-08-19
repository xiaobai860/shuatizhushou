package com.example.shuati.logic

import com.example.shuati.model.QuestionBank
import com.example.shuati.model.QuestionType
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.OutputStream

/**
 * 导出题库为 Excel（与导入格式互相对应，并可被小程序 buildBankWorkbook 读取）。
 * 列：题目ID | 题型 | 题干 | 选项A | 选项B | 选项C | 选项D | 正确答案 | 解析 | 标签
 */
object ExcelWriter {

    private val TYPE_LABEL = mapOf(
        QuestionType.single to "单选",
        QuestionType.multiple to "多选",
        QuestionType.judge to "判断",
        QuestionType.fill to "填空",
        QuestionType.essay to "简答"
    )

    fun writeBank(bank: QuestionBank, out: OutputStream) {
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet(bank.name.take(31))
        val header = listOf("题目ID", "题型", "题干", "选项A", "选项B", "选项C", "选项D", "正确答案", "解析", "标签")
        val headRow = sheet.createRow(0)
        header.forEachIndexed { i, h -> headRow.createCell(i).setCellValue(h) }

        bank.questions.forEachIndexed { r, q ->
            val row = sheet.createRow(r + 1)
            val opts = q.options ?: emptyList()
            val answer = if (q.answer is List<*>) (q.answer as List<*>).joinToString(",")
            else (q.answer ?: "")
            val tags = (q.tags ?: emptyList()).joinToString(",")
            val values = listOf(
                q.id, TYPE_LABEL[q.type] ?: q.type, q.question,
                opts.getOrNull(0) ?: "", opts.getOrNull(1) ?: "",
                opts.getOrNull(2) ?: "", opts.getOrNull(3) ?: "",
                answer, q.explanation ?: "", tags
            )
            values.forEachIndexed { i, v -> row.createCell(i).setCellValue(v?.toString() ?: "") }
        }

        val widths = intArrayOf(10, 8, 36, 16, 16, 16, 16, 20, 24, 18)
        widths.forEachIndexed { i, w -> sheet.setColumnWidth(i, w * 256) }

        wb.write(out)
        wb.close()
    }
}
