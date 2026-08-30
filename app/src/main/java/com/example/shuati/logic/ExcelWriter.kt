package com.example.shuati.logic

import com.example.shuati.model.QuestionBank
import com.example.shuati.model.QuestionType
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * 题库导出为 Excel（.xlsx）。
 *
 * 底层使用自研轻量写入器 [LightXlsx]（xlsx = ZIP + XML），不依赖 Apache POI。
 * 列头与 QuestionTableParser.parseByHeader 完全对齐，因此生成的文件可被导入流程原样解析，
 * 实现「备份 → 还原」的 Excel 往返一致：
 *   题目ID | 题型(中文) | 题干 | 选项A..N | 正确答案 | 解析 | AI解析 | 标签 | 难度 | 不限顺序
 *
 * - 题型以中文写出（单选/多选/判断/填空/简答）；多选答案以逗号分隔；标签以「|」分隔。
 * - 选项列数量动态：默认 4 列（A-D）；某题库选项超过 4 个时自动扩展到实际最大数
 *   （如 6 个选项 → A-F），导入侧亦能自动识别任意「选项X」列。
 * - 新增「AI解析」列：写出 Question.aiExplanation.text，导入时可原样回填。
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

    /** 计算某题库所需的选项列数：至少 4 列（A-D），超过则按实际最大选项数扩展。 */
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

    /** 按当前题库的选项数构建二维表格（表头 + 题目行）。 */
    private fun buildCells(bank: QuestionBank): List<List<String>> {
        val colCount = optionColumnCount(bank)
        val header = headerFor(colCount)

        val colOptions = 3
        val colAnswer = colOptions + colCount
        val colExplanation = colAnswer + 1
        val colAi = colExplanation + 1
        val colTags = colAi + 1
        val colDifficulty = colTags + 1
        val colOrderFree = colDifficulty + 1

        val rows = mutableListOf<List<String>>()
        rows.add(header)
        bank.questions.forEach { q ->
            val row = MutableList(colOrderFree + 1) { "" }
            row[0] = q.id
            row[1] = typeLabel(q.type)
            row[2] = q.question
            (q.options ?: emptyList()).take(colCount).forEachIndexed { i, opt ->
                row[colOptions + i] = opt
            }
            row[colAnswer] = q.answer ?: ""
            q.explanation?.let { row[colExplanation] = it }
            q.aiExplanation?.text?.let { row[colAi] = it }
            if (q.tags.isNotEmpty()) row[colTags] = q.tags.joinToString("|")
            difficultyLabel(q.difficulty).takeIf { it.isNotEmpty() }?.let { row[colDifficulty] = it }
            row[colOrderFree] = if (q.orderFree) "是" else "否"
            rows.add(row)
        }
        return rows
    }

    /** 备份用：直接产出 xlsx 字节数组（供 ZIP 内嵌）。 */
    fun writeBankToBytes(bank: QuestionBank): ByteArray {
        val out = ByteArrayOutputStream()
        LightXlsx.write(buildCells(bank), out)
        return out.toByteArray()
    }

    /** 单题库导出（题库页「导出」按钮）：写入给定 OutputStream。 */
    fun writeBank(bank: QuestionBank, out: OutputStream) {
        LightXlsx.write(buildCells(bank), out)
    }

    /**
     * 下载导入模板：表头 + 三行示例，供用户在 Excel/WPS 中填写后导入。
     * 默认仅显示四个选项列（A、B、C、D）；示例行与表头严格对齐（每行列数 = 表头列数）。
     */
    fun writeTemplate(out: OutputStream) {
        val header = headerFor(4)
        // 第 2 行：每列的填写说明。首列写入固定标记 TEMPLATE_NOTE_MARK，
        // 导入时 QuestionTableParser 检测到该标记即整行跳过（用户也可直接删掉该行）。
        // ⚠️ 说明行必须放在「表头之后、数据之前」：解析器靠首行识别列名，
        // 若把说明放首行会破坏表头识别。
        val note = listOf(
            TEMPLATE_NOTE_MARK,
            "必填。单选/多选/判断/填空/简答",
            "必填。题目正文",
            "选项文本，按 A/B/C… 逐列填写",
            "", "", "",
            "必填。单选填 A/B；多选填 AB 或 A,B；判断填 正确/错误",
            "答案解析，可留空",
            "AI 解析，可留空",
            "多个标签用 | 分隔，如 地理|中国",
            "简单/中等/困难，留空=未设置",
            "是/否。多空填空判定时是否忽略顺序"
        )
        // 0题目ID 1题型 2题干 3选项A 4选项B 5选项C 6选项D 7正确答案 8解析 9AI解析 10标签 11难度 12不限顺序
        val example = listOf(
            listOf("q1", "单选", "下列哪一项是正确的？", "一", "二", "三", "四", "A", "解析示例", "", "标签1|标签2", "简单", "否"),
            listOf("q2", "判断", "判断题示例：地球是圆的。", "", "", "", "", "正确", "解析示例", "", "", "中等", "否"),
            listOf("q3", "填空", "中国的首都是____。", "", "", "", "", "北京", "", "", "", "困难", "否")
        )
        LightXlsx.write(listOf(header, note) + example, out)
    }

    /** 模板「填写说明」行标记：导入解析器据此整行忽略，不计入题目。 */
    const val TEMPLATE_NOTE_MARK = "填写说明（本行导入时自动忽略）"
}
