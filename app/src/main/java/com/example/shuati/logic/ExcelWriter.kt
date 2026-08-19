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

    /**
     * 生成导入模板（含表头 + 各题型示例数据）。与 writeBank 列格式一致，
     * 可直接填写后通过导入解析。等价小程序 buildTemplateWorkbook。
     */
    fun writeTemplate(out: OutputStream) {
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet("题库模板")
        val header = listOf("题目ID", "题型", "题干", "选项A", "选项B", "选项C", "选项D", "正确答案", "解析", "标签")
        val headRow = sheet.createRow(0)
        header.forEachIndexed { i, h -> headRow.createCell(i).setCellValue(h) }

        val examples = listOf(
            listOf("1", "单选", "以下哪个是水果？", "苹果", "石头", "铁块", "木板", "A", "苹果属于水果类植物果实", "生活常识"),
            listOf("2", "多选", "以下哪些是编程语言？", "Python", "Java", "钢铁", "Go", "A,B,D", "Python、Java、Go 均为主流编程语言", "编程,技术"),
            listOf("3", "判断", "JavaScript 是脚本语言", "", "", "", "", "正确", "JS 最初作为浏览器脚本语言设计", "编程"),
            listOf("4", "填空", "___ 是世界第一长河，全长约 ___ 千米", "", "", "", "", "尼罗河|6853", "多空填空答案用 | 分隔", "地理")
        )
        examples.forEachIndexed { r, row ->
            val excelRow = sheet.createRow(r + 1)
            row.forEachIndexed { i, v -> excelRow.createCell(i).setCellValue(v) }
        }

        val widths = intArrayOf(10, 8, 36, 16, 16, 16, 16, 20, 24, 18)
        widths.forEachIndexed { i, w -> sheet.setColumnWidth(i, w * 256) }

        wb.write(out)
        wb.close()
    }
}
