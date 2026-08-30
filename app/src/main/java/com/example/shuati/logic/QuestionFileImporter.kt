package com.example.shuati.logic

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.example.shuati.model.QuestionBank
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * 题库文件导入统一入口：按扩展名 / MIME 分派到对应解析器。
 *
 * 支持的格式：.xlsx（Excel 2007+）、.csv（逗号分隔，UTF-8/GBK 自动探测）。
 * 不再支持老版 .xls（为减小包体已移除 HSSF）。
 * 字段解析统一由 [QuestionTableParser] 完成，行为一致。
 */
object QuestionFileImporter {

    enum class Format(val label: String, val extensions: List<String>) {
        XLSX("Excel (.xlsx)", listOf("xlsx")),
        CSV("CSV (.csv)", listOf("csv", "txt"))
    }

    /** 分享面板 / 文件选择器可接受的 MIME 类型。 */
    val SUPPORTED_MIME_TYPES: Array<String> = arrayOf(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", // xlsx
        "text/csv",
        "text/comma-separated-values",
        "text/plain"
    )

    data class ImportResult(
        val bank: QuestionBank,
        val totalRows: Int,
        val imported: Int,
        val skipped: Int,
        val format: Format,
        val charsetLabel: String? = null   // 仅 CSV：实际采用的编码
    )

    class UnsupportedFormatException(val name: String?) :
        IllegalArgumentException("不支持的文件格式${name?.let { "：$it" } ?: ""}，请选择 .xlsx / .csv 文件")

    /** 根据 Uri 推断格式；无法识别返回 null（调用方据此报错）。 */
    fun detectFormat(context: Context, uri: Uri): Format? {
        val name = displayName(context, uri)
        val ext = name?.substringAfterLast('.', "")?.lowercase()
        if (!ext.isNullOrEmpty()) {
            Format.entries.firstOrNull { ext in it.extensions }?.let { return it }
        }
        // 文件名无扩展名时退回 MIME 判断
        val mime = context.contentResolver.getType(uri)?.lowercase()
            ?: MimeTypeMap.getSingleton()
                .getExtensionFromMimeType(context.contentResolver.getType(uri))
                ?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
        return when {
            mime == null -> null
            mime.contains("spreadsheetml") -> Format.XLSX
            mime.contains("ms-excel") -> null   // 老版 .xls 已不支持
            mime.contains("csv") || mime.contains("comma-separated") || mime.contains("text/plain") -> Format.CSV
            else -> null
        }
    }

    /** 读取 Uri 对应的展示名（用于默认题库名与错误提示）。 */
    fun displayName(context: Context, uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    }

    /** 去掉扩展名的文件名，用作默认题库名。 */
    fun defaultBankName(context: Context, uri: Uri): String {
        val raw = displayName(context, uri) ?: "导入题库"
        val base = raw.substringBeforeLast('.', raw).ifBlank { raw }
        return base.ifBlank { "导入题库" }
    }

    suspend fun importUri(context: Context, uri: Uri, bankName: String): ImportResult =
        withContext(Dispatchers.IO) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalStateException("无法打开文件")
            val format = detectFormat(context, uri)
                ?: throw UnsupportedFormatException(displayName(context, uri))
            importBytes(bytes, format, bankName)
        }

    /** 从字节导入（便于单测与多文件复用）。 */
    fun importBytes(bytes: ByteArray, format: Format, bankName: String): ImportResult {
        return when (format) {
            Format.XLSX -> {
                val r = ExcelParser.parseStream(bytes.inputStream(), bankName)
                ImportResult(r.bank, r.totalRows, r.imported, r.skipped, format)
            }
            Format.CSV -> {
                val r = CsvParser.parseBytes(bytes, bankName)
                ImportResult(r.bank, r.totalRows, r.imported, r.skipped, format, r.charset.name())
            }
        }
    }

    /** 合并多个文件的解析结果为单一题库（多文件分享用）。 */
    fun mergeBanks(
        banks: List<QuestionBank>,
        mergedName: String
    ): QuestionBank {
        val now = System.currentTimeMillis()
        val questions = ArrayList<com.example.shuati.model.Question>()
        val seen = HashSet<String>()
        banks.forEach { bank ->
            bank.questions.forEach { q ->
                var id = q.id
                if (!seen.add(id)) {
                    var n = 2
                    while (!seen.add("${id}_f$n")) n++
                    id = "${id}_f$n"
                }
                questions.add(q.copy(id = id))
            }
        }
        return QuestionBank(
            id = UUID.randomUUID().toString(),
            name = mergedName,
            questions = questions,
            createdAt = now,
            updatedAt = now
        )
    }
}
