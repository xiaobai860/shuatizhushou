package com.example.shuati.logic

import android.content.Context
import android.net.Uri
import com.example.shuati.model.QuestionBank
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream
import java.util.UUID

/**
 * Excel 题库解析（.xlsx 与 .xls）。
 *
 * 本类只负责「把 Excel 适配成表格」，题库字段解析统一委托给 [QuestionTableParser]，
 * 与 CSV 等其它格式共用同一套逻辑，保证各格式行为一致。
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
        val workbook: Workbook = XSSFWorkbook(stream)
        return try {
            parseWorkbook(workbook, bankName)
        } finally {
            workbook.close()
        }
    }

    /** 老版 .xls（HSSF）。 */
    fun parseXls(stream: InputStream, bankName: String): ParseResult {
        val workbook: Workbook = HSSFWorkbook(stream)
        return try {
            parseWorkbook(workbook, bankName)
        } finally {
            workbook.close()
        }
    }

    private fun parseWorkbook(workbook: Workbook, bankName: String): ParseResult {
        val sheet: Sheet = workbook.getSheetAt(0)
        val result = QuestionTableParser.parse(SheetTable(sheet))
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

    /** 把 POI Sheet 适配成内核所需的表格接口。 */
    private class SheetTable(private val sheet: Sheet) : QuestionTableParser.Table {
        override val lastRowIndex: Int get() = sheet.lastRowNum

        override fun row(rowIndex: Int): List<String>? {
            val row = sheet.getRow(rowIndex) ?: return null
            val last = row.lastCellNum.toInt()
            if (last <= 0) return emptyList()
            return (0 until last).map { c ->
                val cell = row.getCell(c) ?: return@map ""
                // POI 已正确解码单元格字符串（含中文），不要对其再做字符集转换，
                // 否则会不可逆地损坏数据（中文无法映射为单字节 → 变成 '?'）。
                when (cell.cellType) {
                    org.apache.poi.ss.usermodel.CellType.STRING -> cell.stringCellValue
                    org.apache.poi.ss.usermodel.CellType.BLANK -> ""
                    else -> DATA_FORMATTER.formatCellValue(cell, null)
                }.trim()
            }
        }
    }

    private val DATA_FORMATTER = org.apache.poi.ss.usermodel.DataFormatter()
}
