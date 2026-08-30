package com.example.shuati.logic

import com.example.shuati.model.AiExplanation
import com.example.shuati.model.Question
import com.example.shuati.model.QuestionBank
import com.example.shuati.model.QuestionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class ExcelRoundTripTest {

    @Test
    fun `writeBank then parseStream preserves extended options, aiExplanation and chinese type`() {
        val q1 = Question(
            id = "q1",
            question = "下列哪一项正确？",
            options = listOf("一", "二", "三", "四", "五", "六"), // 6 个选项 → 动态扩展到 A-F
            answer = "A",
            type = QuestionType.single,
            explanation = "原解析",
            aiExplanation = AiExplanation(text = "AI 生成的解析内容", model = "gpt", provider = "openai")
        )
        val q2 = Question(
            id = "q2",
            question = "地球是圆的？",
            answer = "正确",
            type = QuestionType.judge,
            aiExplanation = AiExplanation(text = "AI 判断解析")
        )
        val bank = QuestionBank(id = "b1", name = "测试题库", questions = listOf(q1, q2))

        val bytes = ExcelWriter.writeBankToBytes(bank)
        val result = ExcelParser.parseStream(ByteArrayInputStream(bytes), "测试题库")

        assertEquals(2, result.imported)

        val out1 = result.bank.questions.first { it.id == "q1" }
        assertEquals(6, out1.options?.size)                 // 动态选项列扩展到 F
        assertEquals("一", out1.options!![0])
        assertEquals("六", out1.options!![5])
        assertEquals(QuestionType.single, out1.type)        // 中文题型往返
        assertEquals("AI 生成的解析内容", out1.aiExplanation?.text) // AI 解析往返

        val out2 = result.bank.questions.first { it.id == "q2" }
        assertEquals(QuestionType.judge, out2.type)
        assertEquals("AI 判断解析", out2.aiExplanation?.text)
    }

    @Test
    fun `writeTemplate example rows align with header (no column shift)`() {
        val baos = ByteArrayOutputStream()
        ExcelWriter.writeTemplate(baos)
        val result = ExcelParser.parseStream(ByteArrayInputStream(baos.toByteArray()), "模板")

        // 三行示例应全部被识别（列对齐，不出现错位导致整行被跳过）
        assertEquals(3, result.imported)
        assertTrue(result.bank.questions.any {
            it.id == "q1" && it.type == QuestionType.single && it.options?.size == 4
        })
        assertTrue(result.bank.questions.any {
            it.id == "q2" && it.type == QuestionType.judge && it.answer == "正确"
        })
        assertTrue(result.bank.questions.any {
            it.id == "q3" && it.type == QuestionType.fill && it.answer == "北京"
        })
    }
}
