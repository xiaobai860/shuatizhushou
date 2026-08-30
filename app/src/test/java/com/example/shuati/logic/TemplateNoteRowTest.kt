package com.example.shuati.logic

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 模板「填写说明行」识别的回归测试。
 *
 * 锁定「对暗号」机制的 4 个边界行为（判定依据是内容标记而非行号）：
 * 1. 模板自带说明行 → 整行忽略，不计入题目
 * 2. 用户删掉说明行、直接在第 2 行写题目 → 正常解析（不能因行号假设而丢题）
 * 3. 真实题目的 ID 恰好含「填写说明」字样 → 不被误杀（曾因宽松匹配出过 bug）
 * 4. 题干含「填写说明」但 ID 列不含 → 正常解析
 *
 * 任何对 [QuestionTableParser.isNoteRow] 或 [ExcelWriter.writeTemplate] 的改动
 * 若破坏上述任一行为，本测试会失败。
 */
class TemplateNoteRowTest {

    private val header = listOf(
        "题目ID", "题型", "题干", "选项A", "选项B", "选项C", "选项D",
        "正确答案", "解析", "AI解析", "标签", "难度", "不限顺序"
    )

    private fun row(id: String, q: String, ans: String) = listOf(
        id, "单选", q, "甲", "乙", "丙", "丁", ans, "", "", "", "", ""
    )

    private val noteRow = listOf(
        ExcelWriter.TEMPLATE_NOTE_MARK, "必填。单选/多选/判断/填空/简答", "必填。题目正文",
        "选项文本", "", "", "", "必填。单选填 A/B", "可留空", "可留空",
        "用 | 分隔", "简单/中等/困难", "是/否"
    )

    /** 场景1：保留说明行 → 应被忽略 */
    @Test
    fun keepNoteRow_isIgnored() {
        val rows = listOf(header, noteRow, row("q1", "第一题", "A"), row("q2", "第二题", "B"))
        val r = QuestionTableParser.parse(ListTable(rows))
        assertEquals("说明行应被忽略", 2, r.imported)
    }

    /** 场景2：用户删除说明行，直接在第2行写题目 → 应正常解析 */
    @Test
    fun deletedNoteRow_questionAtRow2_isParsed() {
        val rows = listOf(header, row("q1", "第一题", "A"), row("q2", "第二题", "B"))
        val r = QuestionTableParser.parse(ListTable(rows))
        assertEquals("第2行是真实题目时应解析", 2, r.imported)
    }

    /** 场景3：真实题目 ID 恰好含「填写说明」字样 → 不应被误杀 */
    @Test
    fun questionIdContainsNoteKeyword_notMisidentified() {
        val tricky = row("填写说明-01", "关于填写说明的考题", "A")
        val rows = listOf(header, tricky, row("q2", "第二题", "B"))
        val r = QuestionTableParser.parse(ListTable(rows))
        assertEquals("ID含说明关键词的题目不应被误杀", 2, r.imported)
    }

    /** 场景4：题干含「填写说明」但 ID 列不含 → 正常解析 */
    @Test
    fun questionTextContainsNoteKeyword_isParsed() {
        val tricky = row("q1", "请阅读填写说明后再作答", "A")
        val rows = listOf(header, tricky, row("q2", "第二题", "B"))
        val r = QuestionTableParser.parse(ListTable(rows))
        assertEquals("题干含说明关键词但ID列不含，应正常解析", 2, r.imported)
    }

    private class ListTable(private val rows: List<List<String>>) : QuestionTableParser.Table {
        override val lastRowIndex: Int get() = rows.size - 1
        override fun row(rowIndex: Int): List<String>? = rows.getOrNull(rowIndex)
    }
}
