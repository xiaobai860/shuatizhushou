package com.example.shuati.logic

import android.content.Context
import android.net.Uri
import com.example.shuati.model.QuestionBank
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.UUID

/**
 * Excel 题库解析（.xlsx，新版 Excel 2007+）。
 *
 * 本类只负责「把 Excel 适配成表格」，题库字段解析统一委托给 [QuestionTableParser]，
 * 与 CSV 等其它格式共用同一套逻辑，保证各格式行为一致。
 *
 * 底层使用自研的轻量读写器 [LightXlsx]（xlsx = ZIP + XML），不依赖 Apache POI，
 * 显著减小 APK 体积。仅支持 .xlsx，不再支持老版 .xls（Excel 97-2003，请另存为 xlsx/csv 导入）。
 *
 * 支持两种表格形态（由内核处理）：
 * 1. 表头格式：首行含「题干 / 正确答案 / 题型」等列名时按列名定位
 * 2. 固定列格式（向后兼容）：B 列题干、C 列答案、D 列选项、E 列解析、F 列标签、G 列题型
 */
object ExcelParser {

    data class ParseResult(
        val bank: QuestionBank,
        val totalRows: Int,
        val imported: Int,
        val skipped: Int
    )

    /** 从 Uri（文档选择器 / 分享 intent）解析。 */
    suspend fun parseUri(
        context: Context,
        uri: Uri,
        bankName: String
    ): ParseResult = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            parseStream(stream, bankName)
        } ?: throw IllegalStateException("无法打开文件")
    }

    /** 从 InputStream 解析（供单测 / 备份导入复用）。 */
    fun parseStream(stream: InputStream, bankName: String): ParseResult {
        val rows = LightXlsx.read(stream)
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
            skipped = result.skipped
        )
    }

    /** 把二维字符串表格适配成内核所需的表格接口。 */
    private class ListTable(private val rows: List<List<String>>) : QuestionTableParser.Table {
        override val lastRowIndex: Int get() = rows.size - 1

        override fun row(rowIndex: Int): List<String>? {
            if (rowIndex !in rows.indices) return null
            return rows[rowIndex].map { it.trim() }
        }
    }
}
