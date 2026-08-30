package com.example.shuati.logic

import com.example.shuati.model.QuestionDifficulty
import com.example.shuati.model.QuestionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 多格式题库导入测试：xlsx / xls / csv 三种格式应产出一致的解析结果。
 *
 * 重点覆盖：
 * 1. 三种格式共用解析内核 → 题型推断、选项、答案规整、难度、标签结果一致
 * 2. CSV 的 GBK / UTF-8 编码自动探测
 * 3. 难度列中文 → 枚举的还原
 * 4. xlsx ↔ csv 的往返一致性
 */
class QuestionFileImporterTest {

    private fun sampleCsv(charsetName: String = "UTF-8", sep: String = ","): ByteArray {
        val rows = listOf(
            listOf("题目ID", "题型", "题干", "选项A", "选项B", "正确答案", "解析", "标签", "难度"),
            listOf("q1", "单选", "1+1=?", "2", "3", "A", "基础运算", "数学|小学", "简单"),
            listOf("q2", "判断", "地球是圆的", "", "", "正确", "常识", "地理", "中等"),
            // 多选答案含逗号，按 CSV 规范必须用引号包裹
            listOf("q3", "多选", "以下为质数", "2", "4", "\"A,B\"", "质数判定", "数学", "困难"),
            listOf("q4", "填空", "中国首都", "", "", "北京", "", "地理", "")
        )
        return rows.joinToString("\n") { it.joinToString(sep) }.toByteArray(charset(charsetName))
    }

    @Test
    fun csv_utf8_parsesAllFields() {
        val r = CsvParser.parseBytes(sampleCsv("UTF-8"), "测试库")
        assertEquals(4, r.imported)

        val q1 = r.bank.questions[0]
        assertEquals("q1", q1.id)
        assertEquals(QuestionType.single, q1.type)
        assertEquals(listOf("2", "3"), q1.options)
        assertEquals("A", q1.answer)
        assertEquals("基础运算", q1.explanation)
        assertEquals(listOf("数学", "小学"), q1.tags)
        assertEquals(QuestionDifficulty.easy, q1.difficulty)
    }

    @Test
    fun csv_difficultyParsedForAllLevels() {
        val r = CsvParser.parseBytes(sampleCsv(), "测试库")
        val map = r.bank.questions.associate { it.id to it.difficulty }
        assertEquals(QuestionDifficulty.easy, map["q1"])
        assertEquals(QuestionDifficulty.normal, map["q2"])
        assertEquals(QuestionDifficulty.hard, map["q3"])
        // 空难度列 → unset（兼容历史数据）
        assertEquals(QuestionDifficulty.unset, map["q4"])
    }

    @Test
    fun csv_gbkIsDecodedWithoutMojibake() {
        val gbkBytes = sampleCsv("GBK")
        val r = CsvParser.parseBytes(gbkBytes, "GBK测试")
        // 乱码时题干会出现 U+FFFD；正确解码应等于原文
        r.bank.questions.forEach { q ->
            assertTrue("题干不应含乱码替换符", q.question.indexOf('\uFFFD') < 0)
        }
        assertEquals("1+1=?", r.bank.questions[0].question)
        assertEquals("GB18030", r.charset.name().uppercase().replace("GBK", "GB18030"))
    }

    @Test
    fun csv_judgeAnswerNormalizedToChinese() {
        val r = CsvParser.parseBytes(sampleCsv(), "测试库")
        assertEquals("正确", r.bank.questions[1].answer)
    }

    @Test
    fun csv_typeInferredWhenTypeColumnEmpty() {
        val csv = listOf(
            listOf("题干", "选项A", "选项B", "正确答案"),
            listOf("自动推断选择题", "甲", "乙", "A")
        ).joinToString("\n") { it.joinToString(",") }.toByteArray()
        val r = CsvParser.parseBytes(csv, "推断测试")
        assertEquals(QuestionType.single, r.bank.questions[0].type)
    }

    @Test
    fun csv_quotedFieldsWithCommasAndNewlines() {
        val csv = "题干,选项A,选项B,正确答案\n" +
                "\"含逗号,的题干\",\"选项,一\",选项二,A\n"
        val r = CsvParser.parseBytes(csv.toByteArray(), "引号测试")
        assertEquals(1, r.imported)
        assertEquals("含逗号,的题干", r.bank.questions[0].question)
        assertEquals(listOf("选项,一", "选项二"), r.bank.questions[0].options)
    }

    @Test
    fun xlsx_and_csv_produceSameQuestions() {
        val csvResult = CsvParser.parseBytes(sampleCsv(), "对比").bank.questions
        // 用 Writer 生成 xlsx，再解析回来，验证两种格式内核一致
        val xlsxBytes = ExcelWriter.writeBankToBytes(
            com.example.shuati.model.QuestionBank(
                id = "b1", name = "对比",
                questions = csvResult,
                createdAt = 0, updatedAt = 0
            )
        )
        val xlsxResult = ExcelParser.parseStream(xlsxBytes.inputStream(), "对比").bank.questions

        assertEquals(csvResult.size, xlsxResult.size)
        csvResult.zip(xlsxResult).forEach { (c, x) ->
            assertEquals("题干应一致", c.question, x.question)
            assertEquals("答案应一致", c.answer, x.answer)
            assertEquals("题型应一致", c.type, x.type)
            assertEquals("选项应一致", c.options, x.options)
            assertEquals("难度应一致", c.difficulty, x.difficulty)
        }
    }

    @Test
    fun xls_chineseTextIsNotMojibake() {
        // .xls（HSSF）以 ISO-8859-1 暴露字符串，中文易乱码。
        // 该测试防止「题干/题型/选项中文变乱码」问题回归。
        val wb = org.apache.poi.hssf.usermodel.HSSFWorkbook()
        val sheet = wb.createSheet("题库")
        val header = listOf("题目ID", "题型", "题干", "选项A", "选项B", "正确答案", "解析", "标签", "难度")
        // 注意：createRow 会重建整行，必须只调用一次再逐个写单元格
        val headerRow = sheet.createRow(0)
        header.forEachIndexed { i, h -> headerRow.createCell(i).setCellValue(h) }
        val row = sheet.createRow(1)
        listOf("x1", "单选", "中国的首都是哪座城市", "北京", "上海", "A", "常识题", "地理|中国", "困难")
            .forEachIndexed { i, v -> row.createCell(i).setCellValue(v) }
        val bytes = java.io.ByteArrayOutputStream().also { wb.write(it); wb.close() }.toByteArray()

        val r = ExcelParser.parseXls(bytes.inputStream(), "XLS中文测试")
        assertEquals(1, r.imported)
        val q = r.bank.questions[0]
        assertEquals("中国的首都是哪座城市", q.question)
        assertTrue("题型列中文应正确解析为单选", q.type == QuestionType.single)
        assertEquals(listOf("北京", "上海"), q.options)
        assertEquals("A", q.answer)
        assertEquals("常识题", q.explanation)
        assertEquals(listOf("地理", "中国"), q.tags)
        assertEquals(QuestionDifficulty.hard, q.difficulty)
        // 乱码特征：出现 U+FFFD 或长度异常
        assertTrue("不应出现乱码替换符", q.question.indexOf('\uFFFD') < 0)
        assertTrue("题干长度应正常", q.question.length == 10)
    }

    @Test
    fun xls_and_csv_yieldSameResult() {
        // 同一份题库分别用 xls 与 csv 导出再导入，结果应完全一致
        val csvQs = CsvParser.parseBytes(sampleCsv(), "对比").bank.questions

        val wb = org.apache.poi.hssf.usermodel.HSSFWorkbook()
        val sheet = wb.createSheet("题库")
        val header = listOf("题目ID", "题型", "题干", "选项A", "选项B", "正确答案", "解析", "标签", "难度")
        val headerRow = sheet.createRow(0)
        header.forEachIndexed { i, h -> headerRow.createCell(i).setCellValue(h) }
        csvQs.forEachIndexed { ri, q ->
            val row = sheet.createRow(ri + 1)
            listOf(
                q.id,
                when (q.type) {
                    QuestionType.single -> "单选"; QuestionType.multiple -> "多选"
                    QuestionType.judge -> "判断"; QuestionType.fill -> "填空"
                    QuestionType.essay -> "简答"
                },
                q.question,
                q.options?.getOrNull(0) ?: "",
                q.options?.getOrNull(1) ?: "",
                q.answer ?: "",
                q.explanation ?: "",
                q.tags.joinToString("|"),
                ExcelWriter.difficultyLabel(q.difficulty)
            ).forEachIndexed { ci, v -> row.createCell(ci).setCellValue(v) }
        }
        val bytes = java.io.ByteArrayOutputStream().also { wb.write(it); wb.close() }.toByteArray()

        val xlsQs = ExcelParser.parseXls(bytes.inputStream(), "对比").bank.questions
        assertEquals(csvQs.size, xlsQs.size)
        csvQs.zip(xlsQs).forEach { (c, x) ->
            assertEquals("xls 与题干应一致", c.question, x.question)
            assertTrue("xls 与题型应一致", c.type == x.type)
            assertTrue("xls 与难度应一致", c.difficulty == x.difficulty)
        }
    }

    @Test
    fun mergeBanks_deduplicatesConflictingIds() {
        fun bank(id: String, qid: String) = com.example.shuati.model.QuestionBank(
            id = id, name = id,
            questions = listOf(
                com.example.shuati.model.Question(id = qid, question = "$id-$qid", answer = "A")
            ),
            createdAt = 0, updatedAt = 0
        )
        val merged = QuestionFileImporter.mergeBanks(listOf(bank("a", "dup"), bank("b", "dup")), "合并库")
        assertEquals(2, merged.questions.size)
        assertEquals("合并库", merged.name)
        // 合并后 ID 必须唯一，否则会破坏按 ID 定位的逻辑
        assertEquals(2, merged.questions.map { it.id }.distinct().size)
    }

    @Test
    fun invalidBytesThrowRatherThanReturnEmptyBank() {
        // 非法字节不应「静默导入空题库」，而应抛错由 UI 提示用户
        var threw = false
        try {
            QuestionFileImporter.importBytes(byteArrayOf(1, 2, 3),
                QuestionFileImporter.Format.XLSX, "x")
        } catch (_: Exception) {
            threw = true
        }
        assertTrue("非法 xlsx 应抛出异常而非静默成功", threw)
    }

    @Test
    fun unrecognizableTableThrowsInsteadOfSilentMiscolumn() {
        // 无表头、且按固定列（B 题干 / C 答案）也解析不出任何题目时，应明确报错，
        // 而不是静默返回空题库让用户误以为导入成功。
        val csv = "姓名,年龄\n张三,18\n"
        var threw = false
        try {
            CsvParser.parseBytes(csv.toByteArray(), "异常表格")
        } catch (_: IllegalArgumentException) {
            threw = true
        }
        assertTrue("无法识别的表格应抛出异常", threw)
    }

    @Test
    fun unsupportedFormatException_hasReadableMessage() {
        val e = QuestionFileImporter.UnsupportedFormatException("题库.pdf")
        assertTrue(e.message!!.contains("不支持的文件格式"))
        assertTrue(e.message!!.contains("题库.pdf"))
    }
}
