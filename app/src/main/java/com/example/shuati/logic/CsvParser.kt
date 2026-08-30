package com.example.shuati.logic

import android.content.Context
import android.net.Uri
import com.example.shuati.model.QuestionBank
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.nio.charset.Charset
import java.util.UUID

/**
 * CSV 题库解析。
 *
 * 表格字段解析复用 [QuestionTableParser]，与 Excel 共用同一套列识别与题型推断逻辑。
 *
 * 编码处理：国内 Excel「另存为 CSV」默认输出 GBK，直接按 UTF-8 读取会乱码。
 * 因此先按 UTF-8 解码，若出现 U+FFFD 替换字符则回退 GBK 重解。
 */
object CsvParser {

    data class ParseResult(
        val bank: QuestionBank,
        val totalRows: Int,
        val imported: Int,
        val skipped: Int,
        val charset: Charset
    )

    suspend fun parseUri(
        context: Context,
        uri: Uri,
        bankName: String
    ): ParseResult = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("无法打开文件")
        parseBytes(bytes, bankName)
    }

    fun parseBytes(bytes: ByteArray, bankName: String): ParseResult {
        val decoded = decodeAuto(bytes)
        val rows = parseCsvRows(decoded.text)
        val result = QuestionTableParser.parse(ListTable(rows))
        val now = System.currentTimeMillis()
        return ParseResult(
            bank = QuestionBank(
                id = UUID.randomUUID().toString(),
                name = bankName,
                questions = result.questions,
                createdAt = now,
                updatedAt = now
            ),
            totalRows = result.totalRows,
            imported = result.imported,
            skipped = result.skipped,
            charset = decoded.charset
        )
    }

    private data class DecodedText(val text: String, val charset: Charset)

    /** UTF-8 优先；出现替换字符则回退 GBK。 */
    private fun decodeAuto(bytes: ByteArray): DecodedText {
        val utf8 = String(bytes, Charsets.UTF_8)
        return if (utf8.indexOf('\uFFFD') >= 0) {
            DecodedText(String(bytes, charset("GBK")), charset("GBK"))
        } else {
            DecodedText(utf8, Charsets.UTF_8)
        }
    }

    /** 简易 CSV 行解析：支持引号包裹、字段内逗号与换行、"" 转义。 */
    private fun parseCsvRows(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var current = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        val s = text.removePrefix("\uFEFF") // 去除 BOM
        while (i < s.length) {
            val ch = s[i]
            when {
                inQuotes -> {
                    when {
                        ch == '"' -> {
                            if (i + 1 < s.length && s[i + 1] == '"') { field.append('"'); i++ }
                            else inQuotes = false
                        }
                        else -> field.append(ch)
                    }
                }
                ch == '"' -> inQuotes = true
                ch == ',' || ch == '，' -> { current.add(field.toString()); field.setLength(0) }
                ch == '\n' -> {
                    current.add(field.toString()); field.setLength(0)
                    rows.add(ArrayList(current)); current = ArrayList()
                }
                ch == '\r' -> { /* 忽略 CR，统一以 LF 分行 */ }
                else -> field.append(ch)
            }
            i++
        }
        if (field.isNotEmpty() || current.isNotEmpty()) {
            current.add(field.toString())
            rows.add(current)
        }
        return rows.filter { cells -> cells.any { it.isNotBlank() } }
    }

    private class ListTable(private val rows: List<List<String>>) : QuestionTableParser.Table {
        override val lastRowIndex: Int get() = rows.size - 1
        override fun row(rowIndex: Int): List<String>? = rows.getOrNull(rowIndex)
    }
}
